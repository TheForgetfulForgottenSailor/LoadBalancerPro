package util;

import core.LoadBalancer;
import core.Server;
import core.ServerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for LoadBalancerPro, providing server log import/export and hashing functionalities.
 */
public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);
    private static final int CSV_MIN_FIELDS = 4;
    private static final int CSV_CAPACITY_INDEX = 4;
    private static final int CSV_CLOUD_INDEX = 5;
    private static final double DEFAULT_CAPACITY = 100.0;
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final int HASH_CACHE_SIZE = 1000;
    private static final int CSV_VERSION = 1;
    private static final int JSON_VERSION = 1;
    private static final double MIN_VALUE = 0.0;
    private static final double MAX_VALUE = 100.0;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final int IO_RETRY_ATTEMPTS = 3;
    private static final long IO_RETRY_DELAY_MS = 500;
    private static final int BATCH_SIZE = 100;

    private static final MessageDigest MD5_DIGEST;
    private static final ConcurrentHashMap<String, Long> hashCache = new ConcurrentHashMap<>(HASH_CACHE_SIZE);
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().availableProcessors() * 2,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(1000),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    static {
        try {
            MD5_DIGEST = MessageDigest.getInstance("MD5");
            Runtime.getRuntime().addShutdownHook(new Thread(Utils::shutdownExecutor));
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to initialize MD5: " + e.getMessage());
        }
    }

    /**
     * Computes a hash for the given key using MD5, with caching for performance.
     */
    public static long hash(String key) {
        if (key == null) {
            logger.warn("Hash called with null key; returning 0.");
            return 0L;
        }
        return hashCache.computeIfAbsent(key, k -> {
            try {
                byte[] bytes = MD5_DIGEST.digest(k.getBytes(StandardCharsets.UTF_8));
                return new BigInteger(1, bytes).longValue();
            } catch (Exception e) {
                logger.error("MD5 hash failed for key '{}': {}", k, e.getMessage(), e);
                return (long) k.hashCode();
            }
        });
    }

    /**
     * Imports server logs from a file into the LoadBalancer, supporting CSV and JSON formats.
     */
    public static void importServerLogs(String filePath, String format, LoadBalancer balancer,
                                        Consumer<Integer> progressCallback, String csvDelimiter,
                                        boolean cloudEnabled) throws IOException {
        Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(format, "Format cannot be null");
        Objects.requireNonNull(balancer, "Balancer cannot be null");
        if (filePath.trim().isEmpty()) throw new IllegalArgumentException("File path cannot be empty");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT));
        logger.info("[{}] Starting import from {} with format {}, delimiter '{}', cloudEnabled={}",
                    timestamp, filePath, format, csvDelimiter, cloudEnabled);

        AtomicLong startTime = new AtomicLong(System.nanoTime());
        File file = new File(filePath);
        int totalLines = estimateLineCount(file);
        AtomicInteger processed = new AtomicInteger(0);

        format = format.toLowerCase();
        if (format.startsWith("csv")) {
            importFromCsv(file, balancer, processed, totalLines, progressCallback, csvDelimiter, cloudEnabled, timestamp);
        } else if (format.startsWith("json")) {
            importFromJson(file, balancer, processed, totalLines, progressCallback, cloudEnabled, timestamp);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format + ". Use 'csv[.gz]' or 'json[.gz]'");
        }

        long elapsedMs = (System.nanoTime() - startTime.get()) / 1_000_000;
        logger.info("[{}] Import completed: {} servers processed in {}ms", timestamp, processed.get(), elapsedMs);
    }

    public static void importServerLogs(String filePath, String format, LoadBalancer balancer) throws IOException {
        importServerLogs(filePath, format, balancer, null, ",", true);
    }

    private static int estimateLineCount(File file) throws IOException {
        return retryIOOperation("estimate line count", file.getPath(), () -> {
            try (BufferedReader br = openBufferedReader(file)) {
                return (int) br.lines().filter(line -> !line.trim().isEmpty()).count();
            }
        }, LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)));
    }

    private static BufferedReader openBufferedReader(File file) throws IOException {
        InputStream fis = new FileInputStream(file);
        return new BufferedReader(new InputStreamReader(
            file.getName().endsWith(".gz") ? new GZIPInputStream(fis) : fis, StandardCharsets.UTF_8));
    }

    private static void importFromCsv(File file, LoadBalancer balancer, AtomicInteger processed, int totalLines,
                                      Consumer<Integer> progressCallback, String csvDelimiter, boolean cloudEnabled,
                                      String timestamp) throws IOException {
        String delimiter = csvDelimiter != null ? csvDelimiter : ",";
        if (delimiter.isEmpty()) {
            throw new IllegalArgumentException("CSV delimiter cannot be empty");
        }
        try (BufferedReader br = openBufferedReader(file)) {
            List<String> batch = new ArrayList<>(BATCH_SIZE);
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                batch.add(line);
                if (batch.size() >= BATCH_SIZE) {
                    processCsvBatch(batch, lineNum - batch.size() + 1, balancer, processed, totalLines,
                                    progressCallback, delimiter, cloudEnabled, timestamp);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                processCsvBatch(batch, lineNum - batch.size() + 1, balancer, processed, totalLines,
                                progressCallback, delimiter, cloudEnabled, timestamp);
            }
        } catch (IOException e) {
            logger.error("[{}] Failed to import CSV from {}: {}", timestamp, file.getPath(), e.getMessage(), e);
            throw e;
        }
    }

    private static void processCsvBatch(List<String> batch, int startLineNum, LoadBalancer balancer,
                                        AtomicInteger processed, int totalLines, Consumer<Integer> progressCallback,
                                        String delimiter, boolean cloudEnabled, String timestamp) {
        for (int i = 0; i < batch.size(); i++) {
            String line = batch.get(i);
            int lineNum = startLineNum + i;
            try {
                Server server = parseCsvLine(line, lineNum, delimiter);
                if (cloudEnabled || server.getServerType() != ServerType.CLOUD) {
                    balancer.addServer(server);
                } else {
                    logger.debug("[{}] Line {}: Skipping non-cloud server {} as cloud is disabled.", 
                                 timestamp, lineNum, server.getServerId());
                }
                updateProgress(processed, totalLines, progressCallback);
            } catch (Exception e) {
                logger.error("[{}] Line {}: Failed to parse CSV line '{}': {}", timestamp, lineNum, line, e.getMessage(), e);
            }
        }
    }

    private static Server parseCsvLine(String line, int lineNum, String delimiter) {
        String[] parts = line.split(Pattern.quote(delimiter), -1);
        if (parts.length < CSV_MIN_FIELDS) {
            throw new IllegalArgumentException("Insufficient fields (" + parts.length + " < " + CSV_MIN_FIELDS + ")");
        }
        String serverId = parts[0].trim();
        if (serverId.isEmpty()) throw new IllegalArgumentException("Server ID cannot be empty");
        double cpu = parseDouble(parts[1].trim(), "CPU", lineNum);
        double mem = parseDouble(parts[2].trim(), "memory", lineNum);
        double disk = parseDouble(parts[3].trim(), "disk", lineNum);
        double capacity = parts.length > CSV_CAPACITY_INDEX ?
                         parseNonNegativeDouble(parts[CSV_CAPACITY_INDEX].trim(), "capacity") : DEFAULT_CAPACITY;
        ServerType serverType = determineServerType(parts, serverId, lineNum);
        Server server = new Server(serverId, cpu, mem, disk, serverType);
        server.setCapacity(capacity);
        return server;
    }

    private static void importFromJson(File file, LoadBalancer balancer, AtomicInteger processed, int totalLines,
                                       Consumer<Integer> progressCallback, boolean cloudEnabled, String timestamp) 
                                       throws IOException {
        String jsonContent = readJsonWithRetries(file, timestamp);
        if (jsonContent.trim().isEmpty()) {
            logger.warn("[{}] Empty JSON file: {}", timestamp, file.getName());
            return;
        }
        JSONArray jsonArray = new JSONArray(jsonContent);
        for (int i = 0; i < jsonArray.length(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, jsonArray.length());
            processJsonBatch(jsonArray, i, end, balancer, processed, totalLines, progressCallback, cloudEnabled, timestamp);
        }
    }

    private static String readJsonWithRetries(File file, String timestamp) throws IOException {
        return retryIOOperation("read JSON", file.getPath(), () -> {
            if (file.getName().endsWith(".gz")) {
                return readGzippedFile(file);
            } else {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
        }, timestamp);
    }

    private static String readGzippedFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) content.append(line);
            return content.toString();
        }
    }

    private static void processJsonBatch(JSONArray jsonArray, int start, int end, LoadBalancer balancer,
                                         AtomicInteger processed, int totalLines, Consumer<Integer> progressCallback,
                                         boolean cloudEnabled, String timestamp) {
        for (int i = start; i < end; i++) {
            try {
                JSONObject json = jsonArray.getJSONObject(i);
                int version = json.optInt("version", JSON_VERSION);
                if (version != JSON_VERSION) {
                    logger.warn("[{}] JSON entry {}: Unsupported version {}; using v{}", timestamp, i, version, JSON_VERSION);
                }
                Server server = Server.fromJson(json);
                if (cloudEnabled || server.getServerType() != ServerType.CLOUD) {
                    balancer.addServer(server);
                } else {
                    logger.debug("[{}] JSON entry {}: Skipping non-cloud server {} as cloud is disabled.", 
                                 timestamp, i, server.getServerId());
                }
                updateProgress(processed, totalLines, progressCallback);
            } catch (Exception e) {
                logger.error("[{}] JSON entry {}: Failed to parse: {}", timestamp, i, e.getMessage(), e);
            }
        }
    }

    private static void updateProgress(AtomicInteger processed, int total, Consumer<Integer> callback) {
        if (callback != null) {
            int count = processed.incrementAndGet();
            int progress = (int) ((count * 100L) / total);
            int previousProgress = count > 1 ? (int) (((count - 1) * 100L) / total) : -1;
            if (count == 1 || progress > previousProgress) {
                try {
                    callback.accept(progress);
                    logger.debug("Progress: {}% ({} of {})", progress, count, total);
                } catch (Exception e) {
                    logger.warn("Progress callback failed: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Exports a server report to a file in CSV or JSON format.
     */
    public static void exportReport(String filename, String format, List<Server> servers, List<String> alertLog,
                                    Consumer<Integer> progressCallback, boolean cloudEnabled) throws IOException {
        Objects.requireNonNull(filename, "Filename cannot be null");
        Objects.requireNonNull(format, "Format cannot be null");
        Objects.requireNonNull(servers, "Servers list cannot be null");
        Objects.requireNonNull(alertLog, "Alert log cannot be null");
        if (filename.trim().isEmpty()) throw new IllegalArgumentException("Filename cannot be empty");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT));
        logger.info("[{}] Starting export to {} with format {}, cloudEnabled={}", timestamp, filename, format, cloudEnabled);

        AtomicLong startTime = new AtomicLong(System.nanoTime());
        List<Server> filteredServers = cloudEnabled ? servers : 
                                       servers.stream().filter(s -> s.getServerType() != ServerType.CLOUD).collect(Collectors.toList());
        int totalItems = filteredServers.size() + alertLog.size();
        AtomicInteger processed = new AtomicInteger(0);

        format = format.toLowerCase();
        if (format.startsWith("csv")) {
            exportToCsv(filename, filteredServers, alertLog, timestamp, processed, totalItems, progressCallback, startTime);
        } else if (format.startsWith("json")) {
            exportToJson(filename, filteredServers, alertLog, timestamp, processed, totalItems, progressCallback, startTime);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format + ". Use 'csv[.gz]' or 'json[.gz]'");
        }

        long elapsedMs = (System.nanoTime() - startTime.get()) / 1_000_000;
        logger.info("[{}] Export completed: {} items processed in {}ms", timestamp, processed.get(), elapsedMs);
    }

    public static void exportReport(String filename, String format, List<Server> servers, List<String> alertLog) throws IOException {
        exportReport(filename, format, servers, alertLog, null, true);
    }

    private static void exportToCsv(String filename, List<Server> servers, List<String> alertLog, String timestamp,
                                    AtomicInteger processed, int totalItems, Consumer<Integer> progressCallback, 
                                    AtomicLong startTime) throws IOException {
        try (BufferedWriter bw = openBufferedWriter(filename)) {
            bw.write("Server Load Report - " + timestamp + "\n");
            bw.write("Server ID,CPU Usage,Memory Usage,Disk Usage,Capacity,Load Score,Healthy,Server Type\n");
            if (servers.isEmpty()) {
                bw.write("No servers to report.\n");
            } else {
                for (Server server : servers) {
                    bw.write(String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%b,%s\n",
                            server.getServerId(), server.getCpuUsage(), server.getMemoryUsage(),
                            server.getDiskUsage(), server.getCapacity(), server.getLoadScore(),
                            server.isHealthy(), server.getServerType()));
                    updateProgress(processed, totalItems, progressCallback);
                }
            }
            bw.write("\nAlerts:\n");
            if (alertLog.isEmpty()) {
                bw.write("No alerts recorded.\n");
            } else {
                for (String alert : alertLog) {
                    bw.write(alert + "\n");
                    updateProgress(processed, totalItems, progressCallback);
                }
            }
        } catch (IOException e) {
            logger.error("[{}] Failed to export CSV to {}: {}", timestamp, filename, e.getMessage(), e);
            retryIOOperation("CSV export", filename, () -> { throw e; }, timestamp);
            throw e;
        }
    }

    private static BufferedWriter openBufferedWriter(String filename) throws IOException {
        OutputStream fos = new FileOutputStream(filename);
        return new BufferedWriter(new OutputStreamWriter(
            filename.endsWith(".gz") ? new GZIPOutputStream(fos) : fos, StandardCharsets.UTF_8));
    }

    private static void exportToJson(String filename, List<Server> servers, List<String> alertLog, String timestamp,
                                     AtomicInteger processed, int totalItems, Consumer<Integer> progressCallback, 
                                     AtomicLong startTime) throws IOException {
        try (BufferedWriter bw = openBufferedWriter(filename)) {
            JSONObject report = new JSONObject();
            report.put("version", JSON_VERSION);
            report.put("timestamp", timestamp);
            JSONArray serverArray = new JSONArray();
            for (Server server : servers) {
                serverArray.put(server.toJson());
                updateProgress(processed, totalItems, progressCallback);
            }
            report.put("servers", serverArray);
            report.put("alerts", new JSONArray(alertLog));
            bw.write(report.toString(4));
            updateProgress(processed, totalItems, progressCallback);
        } catch (IOException e) {
            logger.error("[{}] Failed to export JSON to {}: {}", timestamp, filename, e.getMessage(), e);
            retryIOOperation("JSON export", filename, () -> { throw e; }, timestamp);
            throw e;
        }
    }

    private static <T> T retryIOOperation(String operation, String filename, IOOperation<T> action, String timestamp) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= IO_RETRY_ATTEMPTS; attempt++) {
            try {
                return action.execute();
            } catch (IOException e) {
                lastException = e;
                if (attempt < IO_RETRY_ATTEMPTS) {
                    logger.warn("[{}] {} failed for {} (attempt {}/{}): {}. Retrying in {}ms", 
                                timestamp, operation, filename, attempt, IO_RETRY_ATTEMPTS, e.getMessage(), IO_RETRY_DELAY_MS);
                    try {
                        Thread.sleep(IO_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("[{}] Retry interrupted for {}: {}", timestamp, filename, ie.getMessage(), ie);
                        throw lastException;
                    }
                } else {
                    logger.error("[{}] {} failed after {} attempts for {}: {}", 
                                 timestamp, operation, IO_RETRY_ATTEMPTS, filename, e.getMessage(), e);
                    throw lastException;
                }
            }
        }
        throw lastException; // Unreachable due to throw in loop
    }

    @FunctionalInterface
    private interface IOOperation<T> {
        T execute() throws IOException;
    }

    private static double parseDouble(String value, String field, int lineNum) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new IllegalArgumentException(field + " must be finite: " + value);
            }
            if (parsed < MIN_VALUE || parsed > MAX_VALUE) {
                logger.warn("Line {}: {} value {} out of range [0, 100]; clamping.", lineNum, field, parsed);
                return clamp(parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be numeric: " + value);
        }
    }

    private static double parseNonNegativeDouble(String value, String field) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < MIN_VALUE) {
                throw new IllegalArgumentException(field + " must be non-negative: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be numeric: " + value);
        }
    }

    private static ServerType determineServerType(String[] parts, String serverId, int lineNum) {
        if (parts.length > CSV_CLOUD_INDEX) {
            String isCloudStr = parts[CSV_CLOUD_INDEX].trim().toLowerCase();
            try {
                return Boolean.parseBoolean(isCloudStr) || isCloudStr.equals("1") ? 
                       ServerType.CLOUD : ServerType.ONSITE;
            } catch (Exception e) {
                logger.warn("Line {}: Invalid cloud flag '{}'; inferring from ID.", lineNum, isCloudStr);
            }
        }
        ServerType inferred = serverId.startsWith("AWS-") ? ServerType.CLOUD : ServerType.ONSITE;
        logger.info("Line {}: Inferred serverType for {}: {}", lineNum, serverId, inferred);
        return inferred;
    }

    private static double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    public static void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Executor shutdown timed out after {}s; forcing termination.", SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.error("Executor failed to terminate cleanly after forced shutdown.");
                }
            } else {
                logger.info("Executor service shut down gracefully.");
            }
        } catch (InterruptedException e) {
            logger.error("Executor shutdown interrupted: {}", e.getMessage(), e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
