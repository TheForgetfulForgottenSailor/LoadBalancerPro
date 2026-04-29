package core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

public class ServerMonitor implements Runnable {
    private static final Logger logger = LogManager.getLogger(ServerMonitor.class);

    private final LoadBalancer balancer;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile double alertThreshold;
    private volatile long monitorIntervalMs;
    private volatile double maxFluctuation;
    private final Consumer<String> alertCallback;
    private HealthChangeCallback healthChangeCallback;
    private final List<MonitorDashboardListener> dashboardListeners = Collections.synchronizedList(new ArrayList<>());
    private volatile Thread monitorThread;
    private final ExecutorService alertExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ServerMonitorAlertExecutor");
        t.setDaemon(true);
        return t;
    });
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private volatile int consecutiveCloudFailures = 0;
    private final Map<String, Long> alertCooldowns = new ConcurrentHashMap<>();
    private final Config config;
    private final long startTimeMillis = System.currentTimeMillis();
    private final Map<String, List<MetricPoint>> metricHistory = new ConcurrentHashMap<>();

    public static class Config {
        double alertThreshold = 80.0;
        long monitorIntervalMs = 5000;
        double maxFluctuation = 10.0;
        int maxCloudRetries = 3;
        long cloudRetryBaseMs = 1000;
        double maxMetricValue = 100.0;
        double minMetricValue = 0.0;
        long minMonitorIntervalMs = 100;
        double maxFluctuationLimit = 50.0;
        long shutdownTimeoutSeconds = 5;
        int maxConsecutiveCloudFailures = 5;
        long alertCooldownMs = 60000;
        int metricHistoryWindow = 50;

        public Config withThreshold(double value) { this.alertThreshold = value; return this; }
        public Config withInterval(long ms) { this.monitorIntervalMs = ms; return this; }
        public Config withFluctuation(double value) { this.maxFluctuation = value; return this; }
        public Config withMaxCloudRetries(int retries) { this.maxCloudRetries = retries; return this; }
        public Config withCloudRetryBaseMs(long ms) { this.cloudRetryBaseMs = ms; return this; }
        public Config withMaxMetricValue(double value) { this.maxMetricValue = value; return this; }
        public Config withMinMetricValue(double value) { this.minMetricValue = value; return this; }
        public Config withMinMonitorIntervalMs(long ms) { this.minMonitorIntervalMs = ms; return this; }
        public Config withMaxFluctuationLimit(double value) { this.maxFluctuationLimit = value; return this; }
        public Config withShutdownTimeoutSeconds(long seconds) { this.shutdownTimeoutSeconds = seconds; return this; }
        public Config withMaxConsecutiveCloudFailures(int failures) { this.maxConsecutiveCloudFailures = failures; return this; }
        public Config withAlertCooldownMs(long ms) { this.alertCooldownMs = ms; return this; }
        public Config withMetricHistoryWindow(int window) { this.metricHistoryWindow = window; return this; }
    }

    @FunctionalInterface
    public interface HealthChangeCallback extends Consumer<Server> {}

    public interface MonitorDashboardListener {
        void onNewSnapshot(ServerMetricsSnapshot snapshot);
        void onHealthEvent(String serverId, boolean healthy);
    }

    public ServerMonitor(Config config, LoadBalancer balancer, Consumer<String> alertCallback) {
        if (balancer == null) throw new IllegalArgumentException("Balancer cannot be null");
        this.config = config != null ? config : new Config();
        this.balancer = balancer;
        this.alertThreshold = clamp(config.alertThreshold, config.minMetricValue, config.maxMetricValue);
        this.monitorIntervalMs = Math.max(config.minMonitorIntervalMs, config.monitorIntervalMs);
        this.maxFluctuation = clamp(config.maxFluctuation, config.minMetricValue, config.maxFluctuationLimit);
        this.alertCallback = alertCallback != null ? alertCallback : msg -> {};
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public ServerMonitor(LoadBalancer balancer) {
        this(new Config(), balancer, null);
    }

    public ServerMonitor(LoadBalancer balancer, double alertThreshold, long monitorIntervalMs,
                         double maxFluctuation, Consumer<String> alertCallback) {
        this(new Config()
                .withThreshold(alertThreshold)
                .withInterval(monitorIntervalMs)
                .withFluctuation(maxFluctuation),
             balancer,
             alertCallback);
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Compatibility alias for older tests that treated the monitor like a Thread.
     */
    @Deprecated
    public boolean isAlive() {
        return isRunning();
    }

    public void start() {
        if (running) {
            logger.warn("Monitor already running.");
            return;
        }
        running = true;
        monitorThread = new Thread(this, "ServerMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        logger.info("Server monitor starting with interval {}ms...", monitorIntervalMs);
    }

    public void stop() {
        if (!running) return;
        running = false;
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        try {
            if (monitorThread != null) {
                monitorThread.join(TimeUnit.SECONDS.toMillis(config.shutdownTimeoutSeconds));
            }
            if (monitorThread != null && monitorThread.isAlive()) {
                logger.warn("Monitor thread did not terminate within {} seconds.", config.shutdownTimeoutSeconds);
            }
            alertExecutor.shutdownNow();
            if (!alertExecutor.awaitTermination(config.shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("Alert executor did not terminate within {} seconds.", config.shutdownTimeoutSeconds);
            }
        } catch (InterruptedException e) {
            handleInterruptedException(e);
        }
        logger.info("Server monitor stopped.");
    }

    public void pause() {
        lock.lock();
        try {
            paused = true;
            condition.signal();
            logger.info("Server monitor paused at {}", LocalDateTime.now());
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        lock.lock();
        try {
            paused = false;
            condition.signal();
            logger.info("Server monitor resumed at {}", LocalDateTime.now());
        } finally {
            lock.unlock();
        }
    }

    public void addDashboardListener(MonitorDashboardListener listener) {
        dashboardListeners.add(listener);
    }

    public void removeDashboardListener(MonitorDashboardListener listener) {
        dashboardListeners.remove(listener);
    }

    @Override
    public void run() {
        monitorThread = Thread.currentThread();
        if (!running) {
            running = true;
        }
        if (Thread.currentThread().isInterrupted()) {
            logger.warn("Monitor thread interrupted before starting.");
            running = false;
            return;
        }
        logger.info("Server monitor run loop started.");
        while (running) {
            if (!paused) {
                monitorServers();
                AggregatedMetrics agg = getAggregatedMetrics();
                logger.info("Aggregated metrics: {}", agg);
            }
            if (running) {
                sleepWithInterruptHandling();
            }
        }
        logger.info("Server monitor run loop exited.");
    }

    private void monitorServers() {
        var servers = balancer.getServers();
        if (servers == null) {
            logger.warn("Server list is null; skipping monitor cycle.");
            return;
        }
        if (servers.isEmpty()) {
            logger.info("No servers available for monitoring.");
            return;
        }
        processServers(servers);
        if (running) {
            synchronized (balancer) {
                balancer.checkServerHealth();
            }
        }
    }

    private void processServers(List<Server> servers) {
        for (Server server : servers) {
            if (!running) break;
            if (server == null) {
                logger.warn("Null server encountered during monitoring loop; skipping iteration.");
                continue;
            }
            boolean wasHealthy = server.isHealthy();
            if (server.isHealthy()) {
                updateServerMetrics(server);
                recordMetricHistory(server);
                ServerMetricsSnapshot snapshot = new ServerMetricsSnapshot(
                    LocalDateTime.now(), server.getServerId(), server.getServerType(),
                    server.getCpuUsage(), server.getMemoryUsage(), server.getDiskUsage());
                notifyDashboardListeners(snapshot);
                checkAndLogAlerts(server);
            }
            if (wasHealthy != server.isHealthy()) {
                notifyHealthChange(server);
            }
        }
    }

    private void notifyDashboardListeners(ServerMetricsSnapshot snapshot) {
        synchronized (dashboardListeners) {
            for (MonitorDashboardListener listener : dashboardListeners) {
                listener.onNewSnapshot(snapshot);
            }
        }
    }

    private void updateServerMetrics(Server server) {
        if (!running) return;
        if (server.getServerType() == ServerType.CLOUD && balancer.hasCloudManager()) {
            fetchCloudMetrics(server);
        } else {
            consecutiveCloudFailures = 0;
            updateLocalMetrics(server);
        }
    }

    private void recordMetricHistory(Server server) {
        List<MetricPoint> history = metricHistory.computeIfAbsent(server.getServerId(), k -> new ArrayList<>());
        synchronized (history) {
            history.add(new MetricPoint(LocalDateTime.now(), server.getCpuUsage(), server.getMemoryUsage(), server.getDiskUsage()));
            while (history.size() > config.metricHistoryWindow) {
                history.remove(0);
            }
        }
    }

    private void fetchCloudMetrics(Server server) {
        int retries = config.maxCloudRetries;
        while (retries > 0 && running) {
            if (tryFetchCloudMetrics(server)) {
                consecutiveCloudFailures = 0;
                return;
            }
            retries--;
            handleCloudFetchFailure(server, retries);
        }
    }

    private boolean tryFetchCloudMetrics(Server server) {
        try {
            balancer.updateCloudMetricsIfAvailable();
            return true;
        } catch (Exception e) {
            logger.warn("Cloud metric fetch failure for server {}. Exception: {}", server.getServerId(), e.getMessage(), e);
            return false;
        }
    }

    private void handleCloudFetchFailure(Server server, int retriesLeft) {
        if (retriesLeft > 0) {
            long delay = getRetryDelay(retriesLeft);
            logger.info("Retrying cloud fetch for server {} after {}ms. Retries left: {}", server.getServerId(), delay, retriesLeft);
            sleepWithRetryDelay(delay);
        } else {
            consecutiveCloudFailures++;
            if (consecutiveCloudFailures >= config.maxConsecutiveCloudFailures) {
                logger.error("Max consecutive cloud failures ({}) reached for server {}. Marking unhealthy.", 
                             config.maxConsecutiveCloudFailures, server.getServerId());
                server.setHealthy(false);
                raiseCriticalAlert(server, "Persistent cloud metric fetch failure");
                notifyHealthEvent(server.getServerId(), false);
            }
            updateLocalMetrics(server);
        }
    }

    private void sleepWithRetryDelay(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            handleInterruptedException(e);
        }
    }

    private long getRetryDelay(int retriesLeft) {
        long baseDelay = (long) (config.cloudRetryBaseMs * Math.pow(2, config.maxCloudRetries - retriesLeft));
        long jitter = ThreadLocalRandom.current().nextLong(100, 500);
        return baseDelay + jitter;
    }

    private void updateLocalMetrics(Server server) {
        double[] previousMetrics = {server.getCpuUsage(), server.getMemoryUsage(), server.getDiskUsage()};
        double[] newMetrics = calculateNewMetrics(previousMetrics);
        server.updateMetrics(newMetrics[0], newMetrics[1], newMetrics[2]);
        logMetricChanges(server, previousMetrics, newMetrics);
    }

    private double[] calculateNewMetrics(double[] previousMetrics) {
        double cpu = sanitizeMetric(previousMetrics[0]) + (ThreadLocalRandom.current().nextDouble() - 0.5) * maxFluctuation;
        double mem = sanitizeMetric(previousMetrics[1]) + (ThreadLocalRandom.current().nextDouble() - 0.5) * maxFluctuation;
        double disk = sanitizeMetric(previousMetrics[2]) + (ThreadLocalRandom.current().nextDouble() - 0.5) * maxFluctuation;
        return new double[] {
            clamp(cpu, config.minMetricValue, config.maxMetricValue),
            clamp(mem, config.minMetricValue, config.maxMetricValue),
            clamp(disk, config.minMetricValue, config.maxMetricValue)
        };
    }

    private void logMetricChanges(Server server, double[] previous, double[] current) {
        logger.debug("Metrics updated for server {}: CPU from %.2f%% to %.2f%%, memory from %.2f%% to %.2f%%, disk from %.2f%% to %.2f%%",
                     server.getServerId(), previous[0], current[0], previous[1], current[1], previous[2], current[2]);
    }

    private void checkAndLogAlerts(Server server) {
        if (!running || !canSendAlert(server, config.alertCooldownMs)) return;
        double cpu = server.getCpuUsage();
        double mem = server.getMemoryUsage();
        double disk = server.getDiskUsage();

        if (cpu >= alertThreshold || mem >= alertThreshold || disk >= alertThreshold) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String alertMsg = String.format("%s - ALERT: Server %s (%s) exceeded threshold. CPU: %.2f%% Mem: %.2f%% Disk: %.2f%%",
                                            timestamp, server.getServerId(), server.getServerType(), cpu, mem, disk);
            logger.warn(alertMsg);
            balancer.logAlert(alertMsg);
            sendAlertAsync(alertMsg);
        }
    }

    private void sendAlertAsync(String alertMsg) {
        if (!running || alertExecutor.isShutdown()) {
            logger.debug("Skipping async alert submission after monitor shutdown.");
            return;
        }
        try {
            alertExecutor.submit(() -> {
                try {
                    alertCallback.accept(alertMsg);
                } catch (Exception e) {
                    logger.error("Async alert callback failed for message '{}': {}", alertMsg, e.getMessage(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.debug("Skipping async alert submission because alert executor is shutting down.");
        }
    }

    private boolean canSendAlert(Server server, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastAlert = alertCooldowns.getOrDefault(server.getServerId(), 0L);
        if (now - lastAlert < cooldownMs) return false;
        alertCooldowns.put(server.getServerId(), now);
        return true;
    }

    private void raiseCriticalAlert(Server server, String reason) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String alertMsg = String.format("%s - CRITICAL: Server %s - %s", timestamp, server.getServerId(), reason);
        logger.fatal(alertMsg);
        balancer.logAlert(alertMsg);
        sendAlertAsync(alertMsg);
    }

    private void sleepWithInterruptHandling() {
        lock.lock();
        try {
            long effectiveInterval = adjustIntervalBasedOnHealthAndLoad();
            condition.await(effectiveInterval, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            handleInterruptedException(e);
        } finally {
            lock.unlock();
        }
    }

    private long adjustIntervalBasedOnHealthAndLoad() {
        if (!running) return config.minMonitorIntervalMs;
        boolean anyCritical = balancer.getServers().stream()
            .anyMatch(server -> server != null && (!server.isHealthy() || server.getCpuUsage() > 90.0));
        return anyCritical ? Math.max(monitorIntervalMs / 3, config.minMonitorIntervalMs) : monitorIntervalMs;
    }

    private void handleInterruptedException(InterruptedException e) {
        Thread.currentThread().interrupt();
        running = false;
        logger.info("Monitor interrupted, shutting down. Reason: {}", e.getMessage());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double sanitizeMetric(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            logger.warn("Invalid metric detected (NaN/Infinite), resetting to {}.", config.minMetricValue);
            return config.minMetricValue;
        }
        return value;
    }

    public void setAlertThreshold(double threshold) {
        this.alertThreshold = clamp(threshold, config.minMetricValue, config.maxMetricValue);
        logger.info("Alert threshold updated to {}", alertThreshold);
    }

    public void setMaxFluctuation(double fluctuation) {
        this.maxFluctuation = clamp(fluctuation, config.minMetricValue, config.maxFluctuationLimit);
        logger.info("Max fluctuation updated to {}", maxFluctuation);
    }

    public void setHealthChangeCallback(HealthChangeCallback callback) {
        this.healthChangeCallback = callback;
    }

    private void notifyHealthChange(Server server) {
        if (healthChangeCallback != null && !server.isHealthy()) {
            logger.info("Health callback triggered for server {}", server.getServerId());
            healthChangeCallback.accept(server);
        }
        notifyHealthEvent(server.getServerId(), server.isHealthy());
    }

    private void notifyHealthEvent(String serverId, boolean healthy) {
        synchronized (dashboardListeners) {
            for (MonitorDashboardListener listener : dashboardListeners) {
                listener.onHealthEvent(serverId, healthy);
            }
        }
    }

    public List<ServerMetricsSnapshot> getMetricsSnapshot() {
        List<ServerMetricsSnapshot> snapshot = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.now();
        for (Server server : balancer.getServers()) {
            if (server != null) {
                snapshot.add(new ServerMetricsSnapshot(
                    timestamp, server.getServerId(), server.getServerType(),
                    server.getCpuUsage(), server.getMemoryUsage(), server.getDiskUsage()));
            }
        }
        return snapshot;
    }

    public void exportMetricsSnapshot(Path file) {
        List<ServerMetricsSnapshot> snapshot = getMetricsSnapshot();
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Timestamp,ServerID,Type,CPU,Memory,Disk");
            writer.newLine();
            for (ServerMetricsSnapshot snap : snapshot) {
                writer.write(snap.toString());
                writer.newLine();
            }
            logger.info("Exported {} metrics snapshots to {}", snapshot.size(), file);
        } catch (IOException e) {
            logger.error("Failed to export metrics snapshot to {}: {}", file, e.getMessage(), e);
        }
    }

    public static class ServerMetricsSnapshot {
        private final LocalDateTime timestamp;
        private final String serverId;
        private final ServerType serverType;
        private final double cpu, memory, disk;

        public ServerMetricsSnapshot(LocalDateTime timestamp, String serverId, ServerType serverType,
                                     double cpu, double memory, double disk) {
            this.timestamp = timestamp;
            this.serverId = serverId;
            this.serverType = serverType;
            this.cpu = cpu;
            this.memory = memory;
            this.disk = disk;
        }

        @Override
        public String toString() {
            return String.format("%s,%s,%s,%.2f,%.2f,%.2f",
                timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), serverId, serverType,
                cpu, memory, disk);
        }

        // Getters for REST serialization
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getServerId() { return serverId; }
        public ServerType getServerType() { return serverType; }
        public double getCpu() { return cpu; }
        public double getMemory() { return memory; }
        public double getDisk() { return disk; }
    }

    public void clearOldAlerts(long ageMs) {
        long now = System.currentTimeMillis();
        alertCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > ageMs);
        logger.info("Cleared old alerts older than {}ms.", ageMs);
    }

    public MonitorStatus getStatus() {
        return new MonitorStatus(running, paused, consecutiveCloudFailures, getUptimeMs());
    }

    public static class MonitorStatus {
        private final boolean running, paused;
        private final int consecutiveFailures;
        private final long uptimeMs;

        public MonitorStatus(boolean running, boolean paused, int consecutiveFailures, long uptimeMs) {
            this.running = running;
            this.paused = paused;
            this.consecutiveFailures = consecutiveFailures;
            this.uptimeMs = uptimeMs;
        }

        public boolean isRunning() { return running; }
        public boolean isPaused() { return paused; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public long getUptimeMs() { return uptimeMs; }
    }

    public long getUptimeMs() {
        return running ? System.currentTimeMillis() - startTimeMillis : 0;
    }

    public AggregatedMetrics getAggregatedMetrics() {
        List<Server> servers = balancer.getServers();
        if (servers == null || servers.isEmpty()) return new AggregatedMetrics(0, 0, 0, 0);

        double cpuTotal = 0, memTotal = 0, diskTotal = 0;
        int count = 0;

        for (Server server : servers) {
            if (server != null && server.isHealthy()) {
                cpuTotal += server.getCpuUsage();
                memTotal += server.getMemoryUsage();
                diskTotal += server.getDiskUsage();
                count++;
            }
        }

        return count == 0 ? new AggregatedMetrics(0, 0, 0, 0)
                          : new AggregatedMetrics(count, cpuTotal / count, memTotal / count, diskTotal / count);
    }

    public static class AggregatedMetrics {
        private final int count;
        private final double avgCpu, avgMem, avgDisk;

        public AggregatedMetrics(int count, double avgCpu, double avgMem, double avgDisk) {
            this.count = count;
            this.avgCpu = avgCpu;
            this.avgMem = avgMem;
            this.avgDisk = avgDisk;
        }

        public int getCount() { return count; }
        public double getAvgCpu() { return avgCpu; }
        public double getAvgMem() { return avgMem; }
        public double getAvgDisk() { return avgDisk; }

        @Override
        public String toString() {
            return String.format("Avg CPU: %.2f%%, Mem: %.2f%%, Disk: %.2f%% over %d server(s)",
                    avgCpu, avgMem, avgDisk, count);
        }
    }

    // Historical Metrics Aggregation
    public static class MetricPoint {
        private final LocalDateTime timestamp;
        private final double cpu, memory, disk;

        public MetricPoint(LocalDateTime timestamp, double cpu, double memory, double disk) {
            this.timestamp = timestamp;
            this.cpu = cpu;
            this.memory = memory;
            this.disk = disk;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public double getCpu() { return cpu; }
        public double getMemory() { return memory; }
        public double getDisk() { return disk; }
    }

    public AggregatedMetrics getRollingAverage(Server server) {
        if (server == null) return new AggregatedMetrics(0, 0, 0, 0);
        List<MetricPoint> history = metricHistory.getOrDefault(server.getServerId(), Collections.emptyList());
        synchronized (history) {
            if (history.isEmpty()) return new AggregatedMetrics(0, 0, 0, 0);
            double cpuTotal = 0, memTotal = 0, diskTotal = 0;
            int count = history.size();
            for (MetricPoint point : history) {
                cpuTotal += point.getCpu();
                memTotal += point.getMemory();
                diskTotal += point.getDisk();
            }
            return new AggregatedMetrics(count, cpuTotal / count, memTotal / count, diskTotal / count);
        }
    }

    // CSV/JSON Export
    public enum Format { CSV, JSON }

    public void exportAggregatedMetrics(Path file, Format format) {
        try {
            switch (format) {
                case CSV -> exportAggregatedMetricsToCsv(file);
                case JSON -> exportAggregatedMetricsToJson(file);
            }
            logger.info("Exported aggregated metrics to {} in {} format", file, format);
        } catch (IOException e) {
            logger.error("Failed to export aggregated metrics to {}: {}", file, e.getMessage(), e);
        }
    }

    private void exportAggregatedMetricsToCsv(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("ServerID,Timestamp,CPU,Memory,Disk");
            writer.newLine();
            for (Map.Entry<String, List<MetricPoint>> entry : metricHistory.entrySet()) {
                String serverId = entry.getKey();
                for (MetricPoint point : entry.getValue()) {
                    writer.write(String.format("%s,%s,%.2f,%.2f,%.2f",
                        serverId, point.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        point.getCpu(), point.getMemory(), point.getDisk()));
                    writer.newLine();
                }
            }
        }
    }

    private void exportAggregatedMetricsToJson(Path file) throws IOException {
        JSONObject json = new JSONObject();
        JSONArray serversArray = new JSONArray();
        for (Map.Entry<String, List<MetricPoint>> entry : metricHistory.entrySet()) {
            JSONObject serverJson = new JSONObject();
            serverJson.put("serverId", entry.getKey());
            JSONArray metricsArray = new JSONArray();
            for (MetricPoint point : entry.getValue()) {
                JSONObject metricJson = new JSONObject();
                metricJson.put("timestamp", point.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                metricJson.put("cpu", point.getCpu());
                metricJson.put("memory", point.getMemory());
                metricJson.put("disk", point.getDisk());
                metricsArray.put(metricJson);
            }
            serverJson.put("metrics", metricsArray);
            serversArray.put(serverJson);
        }
        json.put("servers", serversArray);
        Files.writeString(file, json.toString(2), StandardCharsets.UTF_8);
    }

    // GUI Integration-Ready
    public static class TimeSeriesPoint {
        private final LocalDateTime timestamp;
        private final String serverId;
        private final String metricType;
        private final double value;
        private final double rollingAverage;

        public TimeSeriesPoint(LocalDateTime timestamp, String serverId, String metricType, double value, double rollingAverage) {
            this.timestamp = timestamp;
            this.serverId = serverId;
            this.metricType = metricType;
            this.value = value;
            this.rollingAverage = rollingAverage;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getServerId() { return serverId; }
        public String getMetricType() { return metricType; }
        public double getValue() { return value; }
        public double getRollingAverage() { return rollingAverage; }
    }

    public List<TimeSeriesPoint> getTrendSnapshot() {
        List<TimeSeriesPoint> points = new ArrayList<>();
        for (Map.Entry<String, List<MetricPoint>> entry : metricHistory.entrySet()) {
            String serverId = entry.getKey();
            List<MetricPoint> history = entry.getValue();
            Server server = balancer.getServers().stream()
                .filter(s -> s != null && s.getServerId().equals(serverId))
                .findFirst().orElse(null);
            AggregatedMetrics rollingAvg = getRollingAverage(server);
            synchronized (history) {
                for (MetricPoint point : history) {
                    points.add(new TimeSeriesPoint(point.getTimestamp(), serverId, "CPU", point.getCpu(), rollingAvg.getAvgCpu()));
                    points.add(new TimeSeriesPoint(point.getTimestamp(), serverId, "Memory", point.getMemory(), rollingAvg.getAvgMem()));
                    points.add(new TimeSeriesPoint(point.getTimestamp(), serverId, "Disk", point.getDisk(), rollingAvg.getAvgDisk()));
                }
            }
        }
        return points;
    }

    // Cloud vs On-Prem Comparisons
    public static class ComparisonMetrics {
        private final AggregatedMetrics cloudMetrics;
        private final AggregatedMetrics onsiteMetrics;

        public ComparisonMetrics(AggregatedMetrics cloudMetrics, AggregatedMetrics onsiteMetrics) {
            this.cloudMetrics = cloudMetrics;
            this.onsiteMetrics = onsiteMetrics;
        }

        public AggregatedMetrics getCloudMetrics() { return cloudMetrics; }
        public AggregatedMetrics getOnsiteMetrics() { return onsiteMetrics; }

        @Override
        public String toString() {
            return "Cloud: " + cloudMetrics + "\nOnsite: " + onsiteMetrics;
        }
    }

    public ComparisonMetrics getCloudVsOnsiteMetrics() {
        List<Server> servers = balancer.getServers();
        if (servers == null || servers.isEmpty()) return new ComparisonMetrics(
            new AggregatedMetrics(0, 0, 0, 0), new AggregatedMetrics(0, 0, 0, 0));

        double cloudCpu = 0, cloudMem = 0, cloudDisk = 0;
        double onsiteCpu = 0, onsiteMem = 0, onsiteDisk = 0;
        int cloudCount = 0, onsiteCount = 0;

        for (Server server : servers) {
            if (server != null && server.isHealthy()) {
                if (server.getServerType() == ServerType.CLOUD) {
                    cloudCpu += server.getCpuUsage();
                    cloudMem += server.getMemoryUsage();
                    cloudDisk += server.getDiskUsage();
                    cloudCount++;
                } else if (server.getServerType() == ServerType.ONSITE) {
                    onsiteCpu += server.getCpuUsage();
                    onsiteMem += server.getMemoryUsage();
                    onsiteDisk += server.getDiskUsage();
                    onsiteCount++;
                }
            }
        }

        AggregatedMetrics cloudMetrics = cloudCount == 0 ? new AggregatedMetrics(0, 0, 0, 0)
            : new AggregatedMetrics(cloudCount, cloudCpu / cloudCount, cloudMem / cloudCount, cloudDisk / cloudCount);
        AggregatedMetrics onsiteMetrics = onsiteCount == 0 ? new AggregatedMetrics(0, 0, 0, 0)
            : new AggregatedMetrics(onsiteCount, onsiteCpu / onsiteCount, onsiteMem / onsiteCount, onsiteDisk / onsiteCount);

        return new ComparisonMetrics(cloudMetrics, onsiteMetrics);
    }

    // Predictive AI Flags
    public boolean isServerLikelyToOverload(Server server) {
        if (server == null) return false;
        AggregatedMetrics rolling = getRollingAverage(server);
        return rolling.getAvgCpu() > 85 || rolling.getAvgMem() > 85;
    }

    public boolean isServerLikelyToOverload(String serverId) {
        Server server = balancer.getServers().stream()
            .filter(s -> s != null && s.getServerId().equals(serverId))
            .findFirst().orElse(null);
        return isServerLikelyToOverload(server);
    }
}
