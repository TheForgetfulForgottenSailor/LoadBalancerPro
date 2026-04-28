package core;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import util.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadBalancer {
    private static final Logger logger = LogManager.getLogger(LoadBalancer.class);
    private static final int DEFAULT_HASH_REPLICAS = 10;
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SEC = 5;
    private static final double DEFAULT_PREDICTIVE_LOAD_FACTOR = 1.1;
    private static final double DEFAULT_MAX_USAGE_THRESHOLD = 100.0;
    private static final int CLOUD_RETRY_ATTEMPTS = 3;
    private static final long CLOUD_RETRY_DELAY_MS = 1000;
    private static final int SHUTDOWN_RETRIES = 2;

    private final List<Server> servers = Collections.synchronizedList(new ArrayList<>());
    private final List<String> alertLog = new CopyOnWriteArrayList<>();
    private final Map<String, Server> serverMap = new ConcurrentHashMap<>();
    private final PriorityQueue<Server> loadQueue = new PriorityQueue<>(Comparator.comparingDouble(Server::getLoadScore));
    private final Map<String, Double> currentDistribution = new ConcurrentHashMap<>();
    private final ConcurrentNavigableMap<Long, Server> consistentHashRing = new ConcurrentSkipListMap<>();
    private final ServerMonitor monitor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ReentrantReadWriteLock serverLock = new ReentrantReadWriteLock();
    private final int hashReplicas;
    private final double predictiveLoadFactor;
    private final double maxUsageThreshold;
    private CloudManager cloudManager;

    public enum Strategy {
        ROUND_ROBIN,
        LEAST_LOADED
    }

    private volatile Strategy strategy = Strategy.ROUND_ROBIN;

    public LoadBalancer(double maxUsageThreshold, int hashReplicas, double predictiveLoadFactor) {
        this.maxUsageThreshold = Math.max(0, maxUsageThreshold);
        this.hashReplicas = Math.max(1, hashReplicas);
        this.predictiveLoadFactor = Math.max(1.0, predictiveLoadFactor);
        this.monitor = new ServerMonitor(this, this.maxUsageThreshold, 1000, 10.0, null);
    }

    public LoadBalancer() {
        this(DEFAULT_MAX_USAGE_THRESHOLD, DEFAULT_HASH_REPLICAS, DEFAULT_PREDICTIVE_LOAD_FACTOR);
    }

    /**
     * Legacy nullable accessor. Prefer getCloudManagerOptional() or hasCloudManager().
     */
    @Deprecated
    public CloudManager getCloudManager() {
        return cloudManager;
    }

    public Optional<CloudManager> getCloudManagerOptional() {
        return Optional.ofNullable(cloudManager);
    }

    public boolean hasCloudManager() {
        return cloudManager != null;
    }

    public void initializeCloud(String accessKey, String secretKey, String region, int minServers, int maxServers) {
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalArgumentException("Access key cannot be null or blank.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Secret key cannot be null or blank.");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("Region cannot be null or blank.");
        }
        if (minServers <= 0 || maxServers < minServers) {
            throw new IllegalArgumentException("Invalid cloud server bounds.");
        }
        serverLock.writeLock().lock();
        try {
            if (cloudManager == null) {
                CloudManager newCloudManager = new CloudManager(this, accessKey, secretKey, region);
                newCloudManager.initializeCloudServers(minServers, maxServers);
                cloudManager = newCloudManager;
                logger.info("Cloud integration initialized with {} servers.", minServers);
            } else {
                logger.warn("CloudManager already initialized. Skipping.");
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            logger.error("Failed to initialize cloud: {}", e.getMessage());
            throw new RuntimeException("Cloud initialization failed", e);
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    public void addServer(Server server) {
        if (server == null) throw new IllegalArgumentException("Server cannot be null");
        serverLock.writeLock().lock();
        try {
            if (serverMap.containsKey(server.getServerId())) {
                logger.warn("Duplicate server ID {} detected; replacing existing server.", server.getServerId());
                removeFailedServer(serverMap.get(server.getServerId()));
            }
            servers.add(server);
            serverMap.put(server.getServerId(), server);
            loadQueue.offer(server);
            precomputeHashRingEntries(server);
            logger.info("Added server {} ({})", server.getServerId(), server.getServerType());
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    public void checkServerHealth() {
        serverLock.writeLock().lock();
        try {
            List<Server> failedServers = new ArrayList<>();
            for (Server server : servers) {
                if (server.getCpuUsage() >= maxUsageThreshold || server.getMemoryUsage() >= maxUsageThreshold || 
                    server.getDiskUsage() >= maxUsageThreshold || !server.isHealthy()) {
                    server.setHealthy(false);
                    failedServers.add(server);
                    logger.warn("Server {} ({}) down—RIP!", server.getServerId(), server.getServerType());
                }
            }
            if (!failedServers.isEmpty()) removeFailedServersAndRecover(failedServers);
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    private void removeFailedServersAndRecover(List<Server> failedServers) {
        serverLock.writeLock().lock();
        try {
            double redistributedData = 0;
            List<Server> failedCloudServers = new ArrayList<>();
            for (Server failed : failedServers) {
                redistributedData += removeFailedServer(failed);
                if (failed.getServerType() == ServerType.CLOUD) failedCloudServers.add(failed);
            }
            redistributeLoad(redistributedData);
            replaceFailedCloudCapacity(failedCloudServers);
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    private double removeFailedServer(Server failed) {
        Double data = currentDistribution.remove(failed.getServerId());
        double removedData = data != null ? data : 0;
        servers.remove(failed);
        serverMap.remove(failed.getServerId());
        loadQueue.remove(failed);
        removeServerFromHashRing(failed);
        return removedData;
    }

    private void precomputeHashRingEntries(Server server) {
        for (int i = 0; i < hashReplicas; i++) {
            long hash = Utils.hash(server.getServerId() + "-" + i);
            if (hash == Long.MIN_VALUE) {
                logger.warn("Invalid hash for server {} replica {}; using fallback.", server.getServerId(), i);
                hash = i;
            }
            consistentHashRing.put(hash, server);
        }
    }

    private void removeServerFromHashRing(Server failed) {
        for (int i = 0; i < hashReplicas; i++) {
            consistentHashRing.remove(Utils.hash(failed.getServerId() + "-" + i));
        }
    }

    private void redistributeLoad(double redistributedData) {
        if (redistributedData > 0) {
            List<Server> healthy = getHealthyServers();
            if (healthy.isEmpty()) {
                logger.error("No healthy servers available to redistribute {}GB.", redistributedData);
                return;
            }
            Map<String, Double> newDist = leastLoaded(redistributedData);
            currentDistribution.putAll(newDist);
            logger.info("Redistributed {}GB: {}", redistributedData, newDist);
        }
    }

    private void replaceFailedCloudCapacity(List<Server> failedCloudServers) {
        if (cloudManager != null && !failedCloudServers.isEmpty()) {
            int minServers = cloudManager.getMinServers();
            int currentCloudServers = getServersByType(ServerType.CLOUD).size();
            int desiredCapacity = Math.max(minServers, currentCloudServers + failedCloudServers.size());
            cloudManager.scaleServers(desiredCapacity);
            logger.info("Scaled cloud to {} servers after failover of {} cloud servers.", 
                        desiredCapacity, failedCloudServers.size());
        }
    }

    public Map<String, Double> roundRobin(double totalData) {
        validateDistributionInput(totalData);
        return distributeWithHealthyServers(totalData, servers -> {
            Map<String, Double> dist = LoadDistributionPlanner.roundRobin(servers, totalData);
            for (Map.Entry<String, Double> entry : dist.entrySet()) {
                currentDistribution.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            return dist;
        });
    }

    public Map<String, Double> leastLoaded(double totalData) {
        validateDistributionInput(totalData);
        return distributeWithHealthyServers(totalData, servers -> {
            Map<String, Double> dist = LoadDistributionPlanner.leastLoaded(servers, totalData);
            for (Map.Entry<String, Double> entry : dist.entrySet()) {
                currentDistribution.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            return dist;
        });
    }

    public Map<String, Double> weightedDistribution(double totalData) {
        validateDistributionInput(totalData);
        return distributeWithHealthyServers(totalData, servers -> {
            Map<String, Double> dist = LoadDistributionPlanner.weighted(servers, totalData);
            for (Map.Entry<String, Double> entry : dist.entrySet()) {
                currentDistribution.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            return dist;
        });
    }

    public Map<String, Double> consistentHashing(double totalData, int numKeys) {
        if (totalData < 0 || numKeys <= 0) throw new IllegalArgumentException("Invalid totalData or numKeys");
        if (servers.isEmpty()) {
            logger.info("No servers available.");
            return Collections.emptyMap();
        }
        Map<String, Double> dist = new HashMap<>();
        if (consistentHashRing.isEmpty()) return dist;
        double dataPerKey = totalData / numKeys;
        List<Server> healthy = getHealthyServers();
        if (healthy.isEmpty()) {
            logger.warn("No healthy servers for consistent hashing.");
            return dist;
        }
        for (int i = 0; i < numKeys; i++) {
            long keyHash = Utils.hash("data-" + i);
            Map.Entry<Long, Server> entry = consistentHashRing.ceilingEntry(keyHash);
            if (entry == null) entry = consistentHashRing.firstEntry();
            Server server = entry.getValue();
            int attempts = 0;
            while (!server.isHealthy() && attempts < consistentHashRing.size()) {
                entry = consistentHashRing.higherEntry(entry.getKey());
                if (entry == null) entry = consistentHashRing.firstEntry();
                server = entry.getValue();
                attempts++;
            }
            if (server.isHealthy()) {
                dist.merge(server.getServerId(), dataPerKey, Double::sum);
                currentDistribution.merge(server.getServerId(), dataPerKey, Double::sum);
            } else {
                logger.warn("No healthy servers found for data key {}", i);
            }
        }
        return dist;
    }

    public Map<String, Double> capacityAware(double totalData) {
        return capacityAwareWithResult(totalData).allocations();
    }

    public LoadDistributionResult capacityAwareWithResult(double totalData) {
        validateDistributionInput(totalData);
        return distributeWithHealthyServersResult(totalData, servers -> {
            LoadDistributionResult result = LoadDistributionPlanner.capacityAwareResult(servers, totalData);
            for (Map.Entry<String, Double> entry : result.allocations().entrySet()) {
                currentDistribution.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            return result;
        });
    }

    public Map<String, Double> predictiveLoadBalancing(double totalData) {
        return predictiveLoadBalancingWithResult(totalData).allocations();
    }

    public LoadDistributionResult predictiveLoadBalancingWithResult(double totalData) {
        validateDistributionInput(totalData);
        return distributeWithHealthyServersResult(totalData, servers -> {
            LoadDistributionResult result = LoadDistributionPlanner.predictiveResult(
                servers, totalData, calculatePredictedLoads(servers));
            for (Map.Entry<String, Double> entry : result.allocations().entrySet()) {
                currentDistribution.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            return result;
        });
    }

    public ScalingRecommendation recommendScaling(double unallocatedLoad, double targetCapacityPerServer) {
        return ScalingRecommendation.forUnallocatedLoad(unallocatedLoad, targetCapacityPerServer);
    }

    private Map<String, Double> calculatePredictedLoads(List<Server> servers) {
        Map<String, Double> predicted = new HashMap<>();
        for (Server server : servers) {
            predicted.put(server.getServerId(), server.getLoadScore() * predictiveLoadFactor);
        }
        return predicted;
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
        serverLock.readLock().lock();
        try {
            Utils.exportReport(filename, format, servers, alertLog);
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public void shutdown() {
        int retries = SHUTDOWN_RETRIES;
        while (retries > 0) {
            monitor.stop();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    logger.warn("Executor shutdown timed out, forcing termination (retries left: {}).", retries);
                    executor.shutdownNow();
                    if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                        logger.error("Executor did not terminate cleanly after forced shutdown.");
                    }
                }
                cloudManagerOptionalShutdown();
                logger.info("=== LOAD BALANCER SHUT DOWN—GAME OVER ===");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Shutdown interrupted (retries left: {}): {}", retries, e.getMessage());
                retries--;
                if (retries == 0) {
                    logger.error("Shutdown failed after {} retries.", SHUTDOWN_RETRIES);
                    executor.shutdownNow();
                }
            }
        }
    }

    private void cloudManagerOptionalShutdown() {
        getCloudManagerOptional().ifPresent(cm -> {
            try {
                cm.shutdown();
                logger.info("Cloud resources terminated.");
            } catch (Exception e) {
                logger.error("Failed to shutdown CloudManager: {}", e.getMessage());
            }
        });
    }

    public void logAlert(String alertMsg) {
        alertLog.add(alertMsg);
    }

    public List<Server> getServers() {
        serverLock.readLock().lock();
        try {
            return new ArrayList<>(servers);
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public List<String> getAlertLog() {
        return new ArrayList<>(alertLog); // Safe with CopyOnWriteArrayList
    }

    public Map<String, Server> getServerMap() {
        return Collections.unmodifiableMap(new HashMap<>(serverMap));
    }

    public void updateCloudMetricsIfAvailable() throws IOException {
        updateMetricsFromCloud();
    }

    /**
     * Legacy name kept for CLI/monitor callers.
     */
    @Deprecated
    public void updateMetricsFromCloud() throws IOException {
        if (cloudManager == null) {
            logger.debug("CloudManager not initialized; skipping cloud metric update.");
            return;
        }
        int attempts = CLOUD_RETRY_ATTEMPTS;
        while (attempts > 0) {
            try {
                cloudManager.updateServerMetricsFromCloud();
                return;
            } catch (Exception e) {
                attempts--;
                logger.warn("Cloud metric update failed (attempts left: {}): {}", attempts, e.getMessage());
                if (attempts == 0) {
                    throw new IOException("Cloud metric update failed after retries", e);
                }
                try {
                    Thread.sleep(CLOUD_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry delay", ie);
                }
            }
        }
    }

    public void scaleCloudServers(int desiredCapacity) {
        if (desiredCapacity <= 0) {
            throw new IllegalArgumentException("Desired capacity must be positive");
        }
        getCloudManagerOptional().ifPresent(cm -> {
            cm.scaleServers(desiredCapacity);
            logger.info("Scaled cloud to {} servers.", desiredCapacity);
        });
    }

    public Server getServer(String serverId) {
        return serverMap.get(serverId);
    }

    public void removeServer(String serverId) {
        if (serverId == null) return;
        serverLock.writeLock().lock();
        try {
            Server server = serverMap.get(serverId);
            if (server != null) {
                removeFailedServer(server);
            }
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    /**
     * Compatibility shim for older tests. New code should call checkServerHealth().
     */
    @Deprecated
    public void handleFailover() {
        checkServerHealth();
    }

    public void setStrategy(Strategy strategy) {
        if (strategy != null) {
            this.strategy = strategy;
        }
    }

    public Map<String, Double> rebalanceExistingLoad() {
        double totalData = currentDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalData <= 0) {
            logger.info("No existing distributed load to rebalance.");
            return Collections.emptyMap();
        }
        currentDistribution.clear();
        return strategy == Strategy.LEAST_LOADED ? leastLoaded(totalData) : roundRobin(totalData);
    }

    /**
     * Legacy no-argument rebalance entry point for the GUI.
     * Prefer rebalanceExistingLoad() or explicit strategy methods.
     */
    @Deprecated
    public Map<String, Double> balanceLoad() {
        return rebalanceExistingLoad();
    }

    /**
     * Compatibility accessor for tests. Prefer owning monitor lifecycle outside LoadBalancer.
     */
    @Deprecated
    public ServerMonitor getServerMonitor() {
        return monitor;
    }

    private List<Server> getHealthyServers() {
        serverLock.readLock().lock();
        try {
            return servers.stream().filter(Server::isHealthy).toList();
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public List<Server> getServersByType(ServerType type) {
        serverLock.readLock().lock();
        try {
            return servers.stream()
                          .filter(server -> server.getServerType() == type)
                          .collect(Collectors.toList());
        } finally {
            serverLock.readLock().unlock();
        }
    }

    private void validateDistributionInput(double totalData) {
        if (totalData < 0) throw new IllegalArgumentException("Total data cannot be negative");
    }

    private Map<String, Double> distributeWithHealthyServers(double totalData, 
                                                             Function<List<Server>, Map<String, Double>> distributor) {
        serverLock.readLock().lock();
        try {
            if (servers.isEmpty()) {
                logger.info("No servers available.");
                return Collections.emptyMap();
            }
            List<Server> healthy = getHealthyServers();
            if (healthy.isEmpty()) {
                logger.warn("No healthy servers available for distribution.");
                return Collections.emptyMap();
            }
            return distributor.apply(healthy);
        } finally {
            serverLock.readLock().unlock();
        }
    }

    private LoadDistributionResult distributeWithHealthyServersResult(
            double totalData, Function<List<Server>, LoadDistributionResult> distributor) {
        serverLock.readLock().lock();
        try {
            if (servers.isEmpty()) {
                logger.info("No servers available.");
                return new LoadDistributionResult(Collections.emptyMap(), totalData);
            }
            List<Server> healthy = getHealthyServers();
            if (healthy.isEmpty()) {
                logger.warn("No healthy servers available for distribution.");
                return new LoadDistributionResult(Collections.emptyMap(), totalData);
            }
            return distributor.apply(healthy);
        } finally {
            serverLock.readLock().unlock();
        }
    }
}
