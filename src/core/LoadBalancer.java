package core;

import java.io.IOException;
import util.Utils;
import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadBalancer {
    private static final Logger logger = LogManager.getLogger(LoadBalancer.class);
    public final List<Server> servers = Collections.synchronizedList(new ArrayList<>());
    public final Map<String, Server> serverMap = Collections.synchronizedMap(new HashMap<>());
    private final PriorityQueue<Server> loadQueue = new PriorityQueue<>(Comparator.comparingDouble(Server::getLoadScore));
    private final Map<String, Double> currentDistribution = Collections.synchronizedMap(new HashMap<>());
    private final TreeMap<Long, Server> consistentHashRing = new TreeMap<>();
    private final ServerMonitor monitor;
    private final Thread monitorThread;
    private final List<String> alertLog = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CloudManager cloudManager;

    public LoadBalancer() {
        monitor = new ServerMonitor(this);
        monitorThread = new Thread(monitor);
        monitorThread.start();
    }

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
    
    public CloudManager getCloudManager() {
        return cloudManager;
    }

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

    public void exportReport(String filename, String format) throws IOException {
        Utils.exportReport(filename, format, servers, alertLog);
    }

    public void shutdown() {
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

    public void logAlert(String alertMsg) {
        alertLog.add(alertMsg);
    }

    public List<Server> getServers() {
        return new ArrayList<>(servers); // Return a copy for safety to prevent external modification
    }

    public List<String> getAlertLog() {
        return new ArrayList<>(alertLog);
    }

    public Map<String, Server> getServerMap() {
        return serverMap;
    }

    public void updateMetricsFromCloud() {
        if (cloudManager != null) {
            cloudManager.updateServerMetricsFromCloud();
        }
    }

    public void scaleCloudServers(int desiredCapacity) {
        if (cloudManager != null) {
            cloudManager.scaleServers(desiredCapacity);
            logger.info("Scaled cloud to {} servers.", desiredCapacity);
        }
    }
}
