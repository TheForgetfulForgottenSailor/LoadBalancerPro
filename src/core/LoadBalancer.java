package core;

import java.io.IOException;
import util.Utils;
import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages server load balancing, distribution, failover, and cloud scaling.
 *
 * This class implements multiple load balancing strategies, including:
 * <ul>
 *   <li><b>Round Robin:</b> Distributes data evenly across servers.</li>
 *   <li><b>Least Loaded:</b> Prioritizes servers with the lowest load.</li>
 *   <li><b>Weighted Distribution:</b> Allocates data based on server weights.</li>
 *   <li><b>Consistent Hashing:</b> Ensures consistent server selection for data keys.</li>
 *   <li><b>Capacity-Aware Distribution:</b> Considers server capacity and current load.</li>
 *   <li><b>Predictive Load Balancing:</b> Predicts future load for smarter distribution.</li>
 * </ul>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Dynamically manages active servers.</li>
 *   <li>Monitors server health and triggers failover for unhealthy servers.</li>
 *   <li>Supports AWS Cloud auto-scaling integration via CloudManager.</li>
 *   <li>Implements priority queue-based load management.</li>
 *   <li>Uses a monitoring thread (ServerMonitor) for real-time updates.</li>
 *   <li>Provides structured logging for alerts and system state.</li>
 * </ul>
 *
 * <p><b>UML Diagram:</b></p>
 * <p><img src="../loadbalancer.png" alt="LoadBalancer UML Diagram"></p>
 */
public class LoadBalancer {
    /** Logger for tracking operations, alerts, and errors. */
    private static final Logger logger = LogManager.getLogger(LoadBalancer.class);

    /** List of active servers, thread-safe with synchronized access. */
    public final List<Server> servers = Collections.synchronizedList(new ArrayList<>());

    /** Map of server IDs to server instances, synchronized for thread safety. */
    public final Map<String, Server> serverMap = Collections.synchronizedMap(new HashMap<>());

    /** Priority queue for load-based scheduling, ordering servers by load score. */
    private final PriorityQueue<Server> loadQueue = new PriorityQueue<>(Comparator.comparingDouble(Server::getLoadScore));

    /** Current distribution of data across servers, mapping server IDs to allocated data amounts. */
    private final Map<String, Double> currentDistribution = Collections.synchronizedMap(new HashMap<>());

    /** TreeMap-based consistent hashing ring for server selection, mapping hash values to servers. */
    private final TreeMap<Long, Server> consistentHashRing = new TreeMap<>();

    /** Background server monitor for real-time metric updates and alerts. */
    private final ServerMonitor monitor;

    /** Thread running the ServerMonitor for continuous monitoring. */
    private final Thread monitorThread;

    /** List of logged alerts, synchronized for thread safety. */
    private final List<String> alertLog = Collections.synchronizedList(new ArrayList<>());

    /** Executor service for handling asynchronous tasks, such as log imports. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Cloud integration manager for auto-scaling and metric tracking (optional). */
    private CloudManager cloudManager;

    /**
     * Initializes a new LoadBalancer with monitoring enabled.
     *
     * Creates a ServerMonitor instance and starts it in a separate thread to continuously
     * monitor server health and metrics.
     */
    public LoadBalancer() {
        monitor = new ServerMonitor(this);
        monitorThread = new Thread(monitor);
        monitorThread.start();
    }

    /**
     * Initializes cloud integration with AWS and starts cloud-based scaling.
     *
     * Creates a new CloudManager instance if none exists, using the provided AWS credentials
     * and region. Initializes the specified number of cloud servers and starts auto-scaling.
     *
     * @param accessKey AWS access key for authentication
     * @param secretKey AWS secret key for authentication
     * @param region AWS region (e.g., "us-east-1")
     * @param minServers Minimum number of cloud servers to maintain
     * @param maxServers Maximum number of cloud servers to scale to
     * @throws RuntimeException if cloud initialization fails
     */
    public void initializeCloud(String accessKey, String secretKey, String region, int minServers, int maxServers) {
        synchronized (this) {
            if (cloudManager == null) {
                try {
                    CloudManager newCloudManager = new CloudManager(this, accessKey, secretKey, region);
                    newCloudManager.initializeCloudServers(minServers, maxServers);
                    cloudManager = newCloudManager;
                    logger.info("Cloud integration initialized with {} servers.", minServers);
                } catch (Exception e) {
                    logger.error("Failed to initialize cloud: {}", e.getMessage());
                    throw new RuntimeException("Cloud initialization failed", e);
                }
            } else {
                logger.warn("CloudManager already initialized. Skipping.");
            }
        }
    }

    /**
     * Retrieves the CloudManager instance for AWS integration.
     *
     * @return the CloudManager instance, or null if not initialized
     */
    public CloudManager getCloudManager() {
        return cloudManager;
    }

    /**
     * Adds a new server to the load balancer.
     *
     * The server is added to the list of active servers, the server map, the load queue,
     * and the consistent hashing ring for distribution.
     *
     * @param server the server to be added
     */
    public void addServer(Server server) {
        synchronized (this) {
            servers.add(server);
            serverMap.put(server.getServerId(), server);
            loadQueue.offer(server);
            for (int i = 0; i < 10; i++) {
                long hash = Utils.hash(server.getServerId() + "-" + i);
                consistentHashRing.put(hash, server);
            }
        }
    }

    /**
     * Checks and updates the health status of all servers.
     *
     * Servers are marked unhealthy if their CPU, memory, or disk usage reaches 100%,
     * or if they are already marked unhealthy. Unhealthy servers are collected
     * and passed to the failover process.
     */
    public void checkServerHealth() {
        synchronized (this) {
            List<Server> failedServers = new ArrayList<>();
            for (Server server : servers) {
                if (server.getCpuUsage() >= 100 || server.getMemoryUsage() >= 100 || 
                    server.getDiskUsage() >= 100 || !server.isHealthy()) {
                    server.setHealthy(false);
                    failedServers.add(server);
                    logger.warn("Server {} down—RIP!", server.getServerId());
                }
            }
            if (!failedServers.isEmpty()) handleFailover(failedServers);
        }
    }

    /**
     * Handles failover by redistributing data from failed servers and updating server health.
     *
     * Removes failed servers from the active server list, server map, load queue,
     * and consistent hashing ring. Redistributes their data using the least loaded strategy
     * and scales cloud servers if cloud integration is enabled.
     *
     * @param failedServers list of servers that have failed
     */
    private void handleFailover(List<Server> failedServers) {
        synchronized (this) {
            double redistributedData = 0;
            for (Server failed : failedServers) {
                Double data = currentDistribution.remove(failed.getServerId());
                if (data != null) redistributedData += data;
                servers.remove(failed);
                serverMap.remove(failed.getServerId());
                loadQueue.remove(failed);
                for (int i = 0; i < 10; i++) {
                    consistentHashRing.remove(Utils.hash(failed.getServerId() + "-" + i));
                }
            }
            if (redistributedData > 0 && !servers.isEmpty()) {
                Map<String, Double> newDist = leastLoaded(redistributedData);
                currentDistribution.putAll(newDist);
                logger.info("Redistributed {}GB: {}", redistributedData, newDist);
            }
            if (cloudManager != null) {
                int minServers = cloudManager.getMinServers();
                int desiredCapacity = servers.isEmpty() ? minServers : Math.max(servers.size(), minServers);
                cloudManager.scaleServers(desiredCapacity);
                logger.info("Scaled cloud to {} servers after failover.", desiredCapacity);
            }
        }
    }

    /**
     * Distributes data evenly across healthy servers using the round-robin strategy.
     *
     * Each healthy server receives an equal share of the total data, and the current
     * distribution is updated accordingly.
     *
     * @param totalData the total data to distribute (in GB)
     * @return a map of server IDs to allocated data amounts
     */
    public Map<String, Double> roundRobin(double totalData) {
        synchronized (this) {
            Map<String, Double> dist = new HashMap<>();
            List<Server> healthy = servers.stream().filter(Server::isHealthy).toList();
            if (healthy.isEmpty()) return dist;
            double dataPerServer = totalData / healthy.size();
            for (Server server : healthy) {
                dist.put(server.getServerId(), dataPerServer);
                currentDistribution.merge(server.getServerId(), dataPerServer, Double::sum);
            }
            return dist;
        }
    }

    /**
     * Distributes data to the least loaded healthy servers.
     *
     * Servers are sorted by their load score, and data is allocated starting with the
     * least loaded server until the total data is distributed.
     *
     * @param totalData the total data to distribute (in GB)
     * @return a map of server IDs to allocated data amounts
     */
    public Map<String, Double> leastLoaded(double totalData) {
        synchronized (this) {
            Map<String, Double> dist = new HashMap<>();
            List<Server> healthy = servers.stream().filter(Server::isHealthy)
                                         .sorted(Comparator.comparingDouble(Server::getLoadScore)).toList();
            if (healthy.isEmpty()) return dist;
            double remaining = totalData;
            for (Server server : healthy) {
                double alloc = Math.min(remaining, totalData / healthy.size());
                dist.put(server.getServerId(), alloc);
                currentDistribution.merge(server.getServerId(), alloc, Double::sum);
                remaining -= alloc;
                if (remaining <= 0) break;
            }
            return dist;
        }
    }

    /**
     * Distributes data based on server weights.
     *
     * Each healthy server receives a portion of the total data proportional to its weight
     * relative to the total weight of all healthy servers.
     *
     * @param totalData the total data to distribute (in GB)
     * @return a map of server IDs to allocated data amounts
     */
    public Map<String, Double> weightedDistribution(double totalData) {
        synchronized (this) {
            Map<String, Double> dist = new HashMap<>();
            List<Server> healthy = servers.stream().filter(Server::isHealthy).toList();
            if (healthy.isEmpty()) return dist;
            double totalWeight = healthy.stream().mapToDouble(Server::getWeight).sum();
            for (Server server : healthy) {
                double alloc = (server.getWeight() / totalWeight) * totalData;
                dist.put(server.getServerId(), alloc);
                currentDistribution.merge(server.getServerId(), alloc, Double::sum);
            }
            return dist;
        }
    }

    /**
     * Distributes data using consistent hashing.
     *
     * Data keys are hashed and mapped to servers using a consistent hashing ring,
     * ensuring consistent server selection for the same data key.
     *
     * @param totalData the total data to distribute (in GB)
     * @param numKeys the number of data keys to distribute
     * @return a map of server IDs to allocated data amounts
     */
    public Map<String, Double> consistentHashing(double totalData, int numKeys) {
        synchronized (this) {
            Map<String, Double> dist = new HashMap<>();
            if (consistentHashRing.isEmpty()) return dist;
            double dataPerKey = totalData / numKeys;
            for (int i = 0; i < numKeys; i++) {
                long keyHash = Utils.hash("data-" + i);
                Map.Entry<Long, Server> entry = consistentHashRing.ceilingEntry(keyHash);
                if (entry == null) entry = consistentHashRing.firstEntry();
                Server server = entry.getValue();
                if (server.isHealthy()) {
                    dist.merge(server.getServerId(), dataPerKey, Double::sum);
                    currentDistribution.merge(server.getServerId(), dataPerKey, Double::sum);
                }
            }
            return dist;
        }
    }

    /**
     * Distributes data based on server capacity and current load.
     *
     * Servers are sorted by their load-to-capacity ratio, and data is allocated
     * based on available capacity, prioritizing servers with more capacity.
     *
     * @param totalData the total data to distribute (in GB)
     * @return a map of server IDs to allocated data amounts
     */
    public Map<String, Double> capacityAware(double totalData) {
        synchronized (this) {
            Map<String, Double> dist = new HashMap<>();
            List<Server> healthy = servers.stream().filter(Server::isHealthy)
                                         .sorted(Comparator.comparingDouble(s -> s.getLoadScore() / s.getCapacity())).toList();
            if (healthy.isEmpty()) return dist;
            double totalCap = healthy.stream().mapToDouble(s -> s.getCapacity() - s.getLoadScore()).sum();
            double remaining = totalData;
            for (Server server : healthy) {
                double avail = server.getCapacity() - server.getLoadScore();
                if (avail <= 0) continue;
                double alloc = Math.min(remaining, (avail / totalCap) * totalData);
                dist.put(server.getServerId(), alloc);
                currentDistribution.merge(server.getServerId(), alloc, Double::sum);
                remaining -= alloc;
                if (remaining <= 0) break;
            }
            return dist;
        }
    }

    /**
     * Distributes data based on predicted future load.
     *
     * Predicts future load by scaling the current load score by 1.1, then allocates
     * data based on remaining capacity, prioritizing servers with lower predicted load.
     *
     * @param totalData the total data to distribute (in GB)
     * @return a map of server IDs to allocated data amounts
     */
    public Map<String, Double> predictiveLoadBalancing(double totalData) {
        synchronized (this) {
            Map<String, Double> dist = new HashMap<>();
            List<Server> healthy = servers.stream().filter(Server::isHealthy).toList();
            if (healthy.isEmpty()) return dist;
            Map<String, Double> predicted = new HashMap<>();
            double totalPredCap = 0;
            for (Server server : healthy) {
                double predLoad = server.getLoadScore() * 1.1;
                predicted.put(server.getServerId(), predLoad);
                totalPredCap += Math.max(0, server.getCapacity() - predLoad);
            }
            double remaining = totalData;
            for (Server server : healthy) {
                double avail = Math.max(0, server.getCapacity() - predicted.get(server.getServerId()));
                if (avail <= 0) continue;
                double alloc = Math.min(remaining, (avail / totalPredCap) * totalData);
                dist.put(server.getServerId(), alloc);
                currentDistribution.merge(server.getServerId(), alloc, Double::sum);
                remaining -= alloc;
                if (remaining <= 0) break;
            }
            return dist;
        }
    }

    /**
     * Imports server logs asynchronously from a file in the specified format.
     *
     * Uses the Utils class to import server logs in CSV or JSON format, running the import
     * operation in a separate thread via the executor service.
     *
     * @param filename the path to the log file
     * @param format the format of the log file ("csv" or "json")
     */
    public void importServerLogs(String filename, String format) {
        CompletableFuture.runAsync(() -> {
            try {
                Utils.importServerLogs(filename, format, this);
                logger.info("Async import from {} completed!", filename);
            } catch (Exception e) {
                logger.error("Async import from {} failed: {}", filename, e.getMessage());
            }
        }, executor);
    }

    /**
     * Exports server status and alerts to a report file.
     *
     * Delegates to the Utils class to export server data and alerts in the specified format.
     *
     * @param filename the path to the output report file
     * @param format the format of the report ("csv" or "json")
     * @throws IOException if an I/O error occurs during file writing
     */
    public void exportReport(String filename, String format) throws IOException {
        Utils.exportReport(filename, format, servers, alertLog);
    }

    /**
     * Gracefully shuts down the load balancer.
     *
     * Stops the server monitor, terminates the executor service, shuts down cloud resources
     * if cloud integration is enabled, and logs the shutdown process.
     *
     * 
     */
    public void shutdown() throws InterruptedException {
        monitor.stop();
        try {
            monitorThread.join();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                logger.warn("Executor forcibly terminated.");
            }
            if (cloudManager != null) {
                cloudManager.shutdown();
                logger.info("Cloud resources terminated.");
            }
            logger.info("=== LOAD BALANCER SHUT DOWN—GAME OVER ===");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted: {}", e.getMessage());
        }
    }

    /**
     * Logs an alert message to the alert log.
     *
     * @param alertMsg the alert message to log
     */
    public void logAlert(String alertMsg) {
        alertLog.add(alertMsg);
    }

    /**
     * Retrieves a copy of the list of servers.
     *
     * Returns a copy to prevent external modification of the internal list.
     *
     * @return a list of servers
     */
    public List<Server> getServers() {
        return new ArrayList<>(servers); // Return a copy for safety to prevent external modification
    }

    /**
     * Retrieves a copy of the list of logged alerts.
     *
     * Returns a copy to prevent external modification of the internal list.
     *
     * @return a list of alert messages
     */
    public List<String> getAlertLog() {
        return new ArrayList<>(alertLog);
    }

    /**
     * Retrieves the map of server IDs to server objects.
     *
     * @return the server map
     */
    public Map<String, Server> getServerMap() {
        return serverMap;
    }

    /**
     * Updates server metrics from cloud data if cloud integration is enabled.
     *
     * Delegates to the CloudManager to fetch and update metrics from AWS CloudWatch.
     */
    public void updateMetricsFromCloud() {
        if (cloudManager != null) {
            cloudManager.updateServerMetricsFromCloud();
        }
    }

    /**
     * Scales the number of cloud servers to the desired capacity.
     *
     * Delegates to the CloudManager to adjust the number of active cloud servers.
     *
     * @param desiredCapacity the desired number of servers
     */
    public void scaleCloudServers(int desiredCapacity) {
        if (cloudManager != null) {
            cloudManager.scaleServers(desiredCapacity);
            logger.info("Scaled cloud to {} servers.", desiredCapacity);
        }
    }
}
