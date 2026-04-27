package core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {
    private static final Logger logger = LogManager.getLogger(Server.class);
    private static final double DEFAULT_CAPACITY = 100.0;
    private static final double DEFAULT_WEIGHT = 1.0;
    private static final double MIN_VALUE = 0.0;
    private static final double MAX_VALUE = 100.0;
    private static final double DEFAULT_HEALTH_THRESHOLD = 100.0;
    private static final int METRIC_HISTORY_SIZE = 5;
    private static final int JSON_VERSION = 2;
    private static final double TREND_ALERT_THRESHOLD = 0.1;

    private final String serverId;
    private final AtomicLongArray cpuHistory;
    private final AtomicLongArray memHistory;
    private final AtomicLongArray diskHistory;
    private final AtomicInteger historyIndex;
    private volatile double cpuUsage;
    private volatile double memoryUsage;
    private volatile double diskUsage;
    private volatile double loadScore;
    private volatile double weight;
    private volatile double capacity;
    private volatile boolean isHealthy;
    private volatile ServerType serverType;
    private final double healthThreshold;
    private final ToDoubleFunction<Server> loadScoreFormula;
    private final AtomicBoolean healthCheckEnabled = new AtomicBoolean(true);
    private final AtomicBoolean trendAlertTriggered = new AtomicBoolean(false);
    private volatile ServerSnapshot lastSnapshot;
    private final Consumer<String> healthAlertCallback;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    private static final VarHandle CPU_USAGE_HANDLE;
    private static final VarHandle MEMORY_USAGE_HANDLE;
    private static final VarHandle DISK_USAGE_HANDLE;
    private static final VarHandle LOAD_SCORE_HANDLE;
    private static final VarHandle WEIGHT_HANDLE;
    private static final VarHandle CAPACITY_HANDLE;
    private static final VarHandle IS_HEALTHY_HANDLE;
    private static final VarHandle SERVER_TYPE_HANDLE;
    private static final VarHandle LAST_SNAPSHOT_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            CPU_USAGE_HANDLE = lookup.findVarHandle(Server.class, "cpuUsage", double.class);
            MEMORY_USAGE_HANDLE = lookup.findVarHandle(Server.class, "memoryUsage", double.class);
            DISK_USAGE_HANDLE = lookup.findVarHandle(Server.class, "diskUsage", double.class);
            LOAD_SCORE_HANDLE = lookup.findVarHandle(Server.class, "loadScore", double.class);
            WEIGHT_HANDLE = lookup.findVarHandle(Server.class, "weight", double.class);
            CAPACITY_HANDLE = lookup.findVarHandle(Server.class, "capacity", double.class);
            IS_HEALTHY_HANDLE = lookup.findVarHandle(Server.class, "isHealthy", boolean.class);
            SERVER_TYPE_HANDLE = lookup.findVarHandle(Server.class, "serverType", ServerType.class);
            LAST_SNAPSHOT_HANDLE = lookup.findVarHandle(Server.class, "lastSnapshot", ServerSnapshot.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage, ServerType serverType, 
                  Consumer<String> healthAlertCallback) {
        this(serverId, cpuUsage, memoryUsage, diskUsage, serverType, DEFAULT_HEALTH_THRESHOLD, 
             s -> (s.getCpuUsage() + s.getMemoryUsage() + s.getDiskUsage()) / 3.0, false, healthAlertCallback);
    }

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage) {
        this(serverId, cpuUsage, memoryUsage, diskUsage, ServerType.ONSITE, null);
    }

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage, ServerType serverType) {
        this(serverId, cpuUsage, memoryUsage, diskUsage, serverType, null);
    }

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage, ServerType serverType,
                  double capacity) {
        this(serverId, cpuUsage, memoryUsage, diskUsage, serverType, null);
        setCapacity(capacity);
    }

    public Server(String serverId, ServerType serverType, double cpuUsage, double memoryUsage, double diskUsage,
                  double capacity) {
        this(serverId, cpuUsage, memoryUsage, diskUsage, serverType, capacity);
    }

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage, ServerType serverType, 
                  double healthThreshold, ToDoubleFunction<Server> loadScoreFormula, boolean appendUUID, 
                  Consumer<String> healthAlertCallback) {
        if (serverId == null || serverId.isEmpty()) throw new IllegalArgumentException("Server ID cannot be null or empty");
        this.serverId = appendUUID ? serverId + "-" + UUID.randomUUID().toString().substring(0, 8) : serverId;
        this.cpuHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
        this.memHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
        this.diskHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
        this.historyIndex = new AtomicInteger(0);
        this.cpuUsage = validateMetric(cpuUsage, "CPU usage");
        this.memoryUsage = validateMetric(memoryUsage, "Memory usage");
        this.diskUsage = validateMetric(diskUsage, "Disk usage");
        this.loadScore = calculateLoadScore(loadScoreFormula);
        this.weight = DEFAULT_WEIGHT;
        this.capacity = DEFAULT_CAPACITY;
        this.isHealthy = true;
        this.serverType = Boolean.getBoolean("isCloudServer")
            ? ServerType.CLOUD
            : (serverType != null ? serverType : ServerType.ONSITE);
        this.healthThreshold = clamp(healthThreshold);
        this.loadScoreFormula = loadScoreFormula != null ? loadScoreFormula : 
                                s -> (s.getCpuUsage() + s.getMemoryUsage() + s.getDiskUsage()) / 3.0;
        this.healthAlertCallback = healthAlertCallback != null ? healthAlertCallback : msg -> {};
        this.lastSnapshot = new ServerSnapshot(cpuUsage, memoryUsage, diskUsage, loadScore, weight, capacity, 
                                               isHealthy, serverType, cpuHistory, memHistory, diskHistory, 0);
        updateHistory(cpuUsage, memoryUsage, diskUsage);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("version", JSON_VERSION);
        json.put("serverId", serverId);
        json.put("cpuUsage", cpuUsage);
        json.put("memoryUsage", memoryUsage);
        json.put("diskUsage", diskUsage);
        json.put("loadScore", loadScore);
        json.put("weight", weight);
        json.put("capacity", capacity);
        json.put("healthy", isHealthy);
        json.put("serverType", serverType.name());
        json.put("healthThreshold", healthThreshold);
        json.put("cpuHistory", compressHistory(cpuHistory));
        json.put("memHistory", compressHistory(memHistory));
        json.put("diskHistory", compressHistory(diskHistory));
        json.put("snapshot", lastSnapshot.toJson());
        return json;
    }

    private JSONArray compressHistory(AtomicLongArray history) {
        JSONArray array = new JSONArray();
        byte[] input = new byte[METRIC_HISTORY_SIZE * Double.BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(input).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < METRIC_HISTORY_SIZE; i++) {
            buffer.putLong(history.get(i));
        }
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        byte[] output = new byte[METRIC_HISTORY_SIZE * Double.BYTES];
        int compressedLength = deflater.deflate(output);
        for (int i = 0; i < compressedLength; i++) {
            array.put(output[i] & 0xFF);
        }
        deflater.end();
        return array;
    }

    public static Server fromJson(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON object cannot be null");
        }
        int version = json.optInt("version", 1);
        if (!json.has("serverId") || json.optString("serverId").isBlank()) {
            throw new IllegalArgumentException("serverId is required");
        }
        String serverId = json.getString("serverId");
        double cpuUsage = getRequiredDouble(json, "cpuUsage");
        double memoryUsage = getRequiredDouble(json, "memoryUsage");
        double diskUsage = getRequiredDouble(json, "diskUsage");
        ServerType serverType = parseServerType(json);
        double healthThreshold = json.optDouble("healthThreshold", DEFAULT_HEALTH_THRESHOLD);
        Server server = new Server(serverId, cpuUsage, memoryUsage, diskUsage, serverType, healthThreshold, null, false, null);
        server.loadScore = json.optDouble("loadScore", server.calculateLoadScore(null));
        server.weight = json.optDouble("weight", DEFAULT_WEIGHT);
        server.capacity = json.optDouble("capacity", DEFAULT_CAPACITY);
        server.isHealthy = json.optBoolean("healthy", true);
        if (version >= 1) {
            loadHistoryFromJson(json.optJSONArray("cpuHistory"), server.cpuHistory);
            loadHistoryFromJson(json.optJSONArray("memHistory"), server.memHistory);
            loadHistoryFromJson(json.optJSONArray("diskHistory"), server.diskHistory);
        }
        if (version >= 2 && json.has("snapshot")) {
            ServerSnapshot snapshot = ServerSnapshot.fromJson(json.getJSONObject("snapshot"));
            if (snapshot != null && snapshot.isValid()) {
                server.lastSnapshot = snapshot;
            } else {
                logger.warn("Invalid snapshot for server {} during deserialization; using default.", serverId);
            }
        } else if (version < 2) {
            logger.info("Upgrading server {} from JSON version {} to 2; initializing snapshot.", serverId, version);
        }
        return server;
    }

    private static void loadHistoryFromJson(JSONArray array, AtomicLongArray history) {
        if (array != null && array.length() > 0) {
            byte[] compressed = new byte[array.length()];
            for (int i = 0; i < array.length(); i++) {
                compressed[i] = (byte) array.optInt(i, 0);
            }
            byte[] decompressed = decompressHistory(compressed);
            ByteBuffer buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < METRIC_HISTORY_SIZE && buffer.hasRemaining(); i++) {
                history.set(i, buffer.getLong());
            }
        }
    }

    private static byte[] decompressHistory(byte[] compressed) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] output = new byte[METRIC_HISTORY_SIZE * Double.BYTES];
        try {
            int decompressedLength = inflater.inflate(output);
            if (decompressedLength != output.length) {
                logger.warn("Decompression mismatch: expected {} bytes, got {}", output.length, decompressedLength);
                byte[] adjusted = new byte[output.length];
                System.arraycopy(output, 0, adjusted, 0, Math.min(decompressedLength, output.length));
                return adjusted;
            }
            return output;
        } catch (DataFormatException e) {
            logger.error("Failed to decompress history data: {}", e.getMessage());
            return new byte[METRIC_HISTORY_SIZE * Double.BYTES]; // Zeros on failure
        } finally {
            inflater.end();
        }
    }

    private static ServerType parseServerType(JSONObject json) {
        if (json.has("serverType")) {
            try {
                return ServerType.valueOf(json.getString("serverType"));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid serverType in JSON: {}; defaulting to ONSITE", json.optString("serverType"));
                return ServerType.ONSITE;
            }
        }
        return json.optBoolean("cloudInstance", false) ? ServerType.CLOUD : ServerType.ONSITE;
    }

    public void updateMetrics(double cpu, double mem, double disk) {
        double oldCpu = cpuUsage;
        double oldMem = memoryUsage;
        double oldDisk = diskUsage;
        LAST_SNAPSHOT_HANDLE.setVolatile(this, new ServerSnapshot(cpuUsage, memoryUsage, diskUsage, loadScore, 
                                                                 weight, capacity, isHealthy, serverType, 
                                                                 cpuHistory, memHistory, diskHistory, 
                                                                 historyIndex.get()));
        double sanitizedCpu = validateMetric(cpu, "CPU usage");
        double sanitizedMem = validateMetric(mem, "Memory usage");
        double sanitizedDisk = validateMetric(disk, "Disk usage");
        CPU_USAGE_HANDLE.setVolatile(this, sanitizedCpu);
        MEMORY_USAGE_HANDLE.setVolatile(this, sanitizedMem);
        DISK_USAGE_HANDLE.setVolatile(this, sanitizedDisk);
        LOAD_SCORE_HANDLE.setVolatile(this, calculateLoadScore(loadScoreFormula));
        updateHistory(sanitizedCpu, sanitizedMem, sanitizedDisk);
        checkMetricTrends(sanitizedCpu, sanitizedMem, sanitizedDisk);
        if (healthCheckEnabled.get()) {
            IS_HEALTHY_HANDLE.setVolatile(this, 
                sanitizedCpu < healthThreshold && 
                sanitizedMem < healthThreshold && 
                sanitizedDisk < healthThreshold);
        }
        propertyChangeSupport.firePropertyChange("cpuUsage", oldCpu, sanitizedCpu);
        propertyChangeSupport.firePropertyChange("memoryUsage", oldMem, sanitizedMem);
        propertyChangeSupport.firePropertyChange("diskUsage", oldDisk, sanitizedDisk);
    }

    private void updateHistory(double cpu, double mem, double disk) {
        int index = historyIndex.getAndIncrement() % METRIC_HISTORY_SIZE;
        cpuHistory.set(index, Double.doubleToLongBits(cpu));
        memHistory.set(index, Double.doubleToLongBits(mem));
        diskHistory.set(index, Double.doubleToLongBits(disk));
    }

    private void checkMetricTrends(double cpu, double mem, double disk) {
        if (!trendAlertTriggered.get()) {
            double[] cpuTrend = getHistorySnapshot(cpuHistory);
            double[] memTrend = getHistorySnapshot(memHistory);
            double[] diskTrend = getHistorySnapshot(diskHistory);
            if (isTrendingUp(cpuTrend, cpu) || isTrendingUp(memTrend, mem) || isTrendingUp(diskTrend, disk)) {
                trendAlertTriggered.set(true);
                String alert = String.format("Server %s: Metric trend exceeds threshold - CPU: %.2f, Mem: %.2f, Disk: %.2f", 
                                             serverId, cpu, mem, disk);
                logger.warn(alert);
                healthAlertCallback.accept(alert);
            }
        }
    }

    private boolean isTrendingUp(double[] history, double current) {
        double avg = Arrays.stream(history).average().orElse(0.0);
        return current > avg * (1 + TREND_ALERT_THRESHOLD);
    }

    public void resetMetrics() {
        CPU_USAGE_HANDLE.setVolatile(this, MIN_VALUE);
        MEMORY_USAGE_HANDLE.setVolatile(this, MIN_VALUE);
        DISK_USAGE_HANDLE.setVolatile(this, MIN_VALUE);
        LOAD_SCORE_HANDLE.setVolatile(this, MIN_VALUE);
        for (int i = 0; i < METRIC_HISTORY_SIZE; i++) {
            cpuHistory.set(i, 0L);
            memHistory.set(i, 0L);
            diskHistory.set(i, 0L);
        }
        historyIndex.set(0);
        IS_HEALTHY_HANDLE.setVolatile(this, true);
        trendAlertTriggered.set(false);
    }

    public void rollbackToLastSnapshot() {
        if (lastSnapshot != null) {
            if (!lastSnapshot.isValid()) {
                logger.warn("Invalid snapshot for server {}; skipping rollback.", serverId);
                return;
            }
            CPU_USAGE_HANDLE.setVolatile(this, lastSnapshot.cpuUsage);
            MEMORY_USAGE_HANDLE.setVolatile(this, lastSnapshot.memoryUsage);
            DISK_USAGE_HANDLE.setVolatile(this, lastSnapshot.diskUsage);
            LOAD_SCORE_HANDLE.setVolatile(this, lastSnapshot.loadScore);
            WEIGHT_HANDLE.setVolatile(this, lastSnapshot.weight);
            CAPACITY_HANDLE.setVolatile(this, lastSnapshot.capacity);
            IS_HEALTHY_HANDLE.setVolatile(this, lastSnapshot.isHealthy);
            SERVER_TYPE_HANDLE.setVolatile(this, lastSnapshot.serverType);
            for (int i = 0; i < METRIC_HISTORY_SIZE; i++) {
                cpuHistory.set(i, lastSnapshot.cpuHistory.get(i));
                memHistory.set(i, lastSnapshot.memHistory.get(i));
                diskHistory.set(i, lastSnapshot.diskHistory.get(i));
            }
            historyIndex.set(lastSnapshot.historyIndex);
            trendAlertTriggered.set(false);
            logger.info("Rolled back server {} to last snapshot.", serverId);
        } else {
            logger.warn("No snapshot available for rollback on server {}.", serverId);
        }
    }

    public String getServerId() {
        return serverId;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public double getDiskUsage() {
        return diskUsage;
    }

    public double getLoadScore() {
        return loadScore;
    }

    public double getWeight() {
        return weight;
    }

    public double getCapacity() {
        return capacity;
    }

    public boolean isHealthy() {
        return isHealthy;
    }

    public ServerType getServerType() {
        return serverType;
    }

    public boolean isCloudInstance() {
        return serverType == ServerType.CLOUD;
    }

    public double[] getCpuHistorySnapshot() {
        return getHistorySnapshot(cpuHistory);
    }

    public double[] getMemoryHistorySnapshot() {
        return getHistorySnapshot(memHistory);
    }

    public double[] getDiskHistorySnapshot() {
        return getHistorySnapshot(diskHistory);
    }

    private double[] getHistorySnapshot(AtomicLongArray history) {
        double[] snapshot = new double[METRIC_HISTORY_SIZE];
        for (int i = 0; i < METRIC_HISTORY_SIZE; i++) {
            snapshot[i] = Double.longBitsToDouble(history.get(i));
        }
        return snapshot;
    }

    public void setWeight(double weight) {
        WEIGHT_HANDLE.setVolatile(this, validateNonNegative(weight, "Weight"));
    }

    public void setHealthy(boolean healthy) {
        boolean oldHealthy = isHealthy;
        IS_HEALTHY_HANDLE.setVolatile(this, healthy);
        propertyChangeSupport.firePropertyChange("healthy", oldHealthy, healthy);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void setCapacity(double capacity) {
        double sanitizedCapacity = validateNonNegative(capacity, "Capacity");
        CAPACITY_HANDLE.setVolatile(this, sanitizedCapacity);
        if (healthCheckEnabled.get()) {
            IS_HEALTHY_HANDLE.setVolatile(this, 
                cpuUsage < healthThreshold && 
                memoryUsage < healthThreshold && 
                diskUsage < healthThreshold);
        }
    }

    public void setServerType(ServerType serverType) {
        SERVER_TYPE_HANDLE.setVolatile(this, serverType != null ? serverType : ServerType.ONSITE);
    }

    public void setCloudInstance(boolean cloudInstance) {
        setServerType(cloudInstance ? ServerType.CLOUD : ServerType.ONSITE);
    }

    public void setCpuUsage(double cpuUsage) {
        updateMetrics(cpuUsage, memoryUsage, diskUsage);
    }

    public void setMemoryUsage(double memoryUsage) {
        updateMetrics(cpuUsage, memoryUsage, diskUsage);
    }

    public void setDiskUsage(double diskUsage) {
        updateMetrics(cpuUsage, memoryUsage, diskUsage);
    }

    public void enableHealthCheck() {
        healthCheckEnabled.set(true);
        IS_HEALTHY_HANDLE.setVolatile(this, 
            cpuUsage < healthThreshold && 
            memoryUsage < healthThreshold && 
            diskUsage < healthThreshold);
    }

    public void disableHealthCheck() {
        healthCheckEnabled.set(false);
    }

    private double sanitize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            logger.warn("Sanitizing NaN or Infinite value to 0 for server {}", serverId);
            return MIN_VALUE;
        }
        return clamp(value);
    }

    private static double getRequiredDouble(JSONObject json, String fieldName) {
        if (!json.has(fieldName) || !(json.get(fieldName) instanceof Number)) {
            throw new IllegalArgumentException(fieldName + " must be numeric");
        }
        return json.getDouble(fieldName);
    }

    private double validateMetric(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100");
        }
        return value;
    }

    private double validateNonNegative(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < MIN_VALUE) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        return value;
    }

    private double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    private double calculateLoadScore(ToDoubleFunction<Server> formula) {
        return formula != null ? formula.applyAsDouble(this) : 
               (cpuUsage + memoryUsage + diskUsage) / 3.0;
    }

    private static class ServerSnapshot {
        private final double cpuUsage;
        private final double memoryUsage;
        private final double diskUsage;
        private final double loadScore;
        private final double weight;
        private final double capacity;
        private final boolean isHealthy;
        private final ServerType serverType;
        private final AtomicLongArray cpuHistory;
        private final AtomicLongArray memHistory;
        private final AtomicLongArray diskHistory;
        private final int historyIndex;

        public ServerSnapshot(double cpuUsage, double memoryUsage, double diskUsage, double loadScore, 
                              double weight, double capacity, boolean isHealthy, ServerType serverType, 
                              AtomicLongArray cpuHistory, AtomicLongArray memHistory, AtomicLongArray diskHistory, 
                              int historyIndex) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.diskUsage = diskUsage;
            this.loadScore = loadScore;
            this.weight = weight;
            this.capacity = capacity;
            this.isHealthy = isHealthy;
            this.serverType = serverType;
            this.cpuHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
            this.memHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
            this.diskHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
            for (int i = 0; i < METRIC_HISTORY_SIZE; i++) {
                this.cpuHistory.set(i, cpuHistory.get(i));
                this.memHistory.set(i, memHistory.get(i));
                this.diskHistory.set(i, diskHistory.get(i));
            }
            this.historyIndex = historyIndex;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("cpuUsage", cpuUsage);
            json.put("memoryUsage", memoryUsage);
            json.put("diskUsage", diskUsage);
            json.put("loadScore", loadScore);
            json.put("weight", weight);
            json.put("capacity", capacity);
            json.put("healthy", isHealthy);
            json.put("serverType", serverType.name());
            json.put("cpuHistory", historyToJson(cpuHistory));
            json.put("memHistory", historyToJson(memHistory));
            json.put("diskHistory", historyToJson(diskHistory));
            json.put("historyIndex", historyIndex);
            return json;
        }

        private JSONArray historyToJson(AtomicLongArray history) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < METRIC_HISTORY_SIZE; i++) {
                array.put(history.get(i));
            }
            return array;
        }

        public static ServerSnapshot fromJson(JSONObject json) {
            if (json == null) return null;
            double cpuUsage = json.optDouble("cpuUsage", MIN_VALUE);
            double memoryUsage = json.optDouble("memoryUsage", MIN_VALUE);
            double diskUsage = json.optDouble("diskUsage", MIN_VALUE);
            double loadScore = json.optDouble("loadScore", MIN_VALUE);
            double weight = json.optDouble("weight", DEFAULT_WEIGHT);
            double capacity = json.optDouble("capacity", DEFAULT_CAPACITY);
            boolean isHealthy = json.optBoolean("healthy", true);
            ServerType serverType = parseServerType(json);
            AtomicLongArray cpuHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
            AtomicLongArray memHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
            AtomicLongArray diskHistory = new AtomicLongArray(METRIC_HISTORY_SIZE);
            loadHistoryFromJson(json.optJSONArray("cpuHistory"), cpuHistory);
            loadHistoryFromJson(json.optJSONArray("memHistory"), memHistory);
            loadHistoryFromJson(json.optJSONArray("diskHistory"), diskHistory);
            int historyIndex = json.optInt("historyIndex", 0);
            return new ServerSnapshot(cpuUsage, memoryUsage, diskUsage, loadScore, weight, capacity, 
                                      isHealthy, serverType, cpuHistory, memHistory, diskHistory, historyIndex);
        }

        public boolean isValid() {
            return cpuUsage >= MIN_VALUE && cpuUsage <= MAX_VALUE &&
                   memoryUsage >= MIN_VALUE && memoryUsage <= MAX_VALUE &&
                   diskUsage >= MIN_VALUE && diskUsage <= MAX_VALUE &&
                   loadScore >= MIN_VALUE && loadScore <= MAX_VALUE &&
                   weight >= MIN_VALUE && capacity >= MIN_VALUE &&
                   serverType != null;
        }
    }
}
