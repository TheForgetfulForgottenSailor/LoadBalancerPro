package test.util;

import core.LoadBalancer;
import core.Server;
import core.ServerType;
import util.Utils;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import java.io.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.ObjectMapper;

class UtilsTest {
    private static final Logger logger = LogManager.getLogger(UtilsTest.class);
    private static final Path TEST_DIR = Paths.get("test_data");
    private static final String CSV_FORMAT = "csv";
    private static final String JSON_FORMAT = "json";
    private static final String TEST_SERVER_ID_1 = "S1";
    private static final String TEST_SERVER_ID_2 = "S2";
    private static final String AWS_SERVER_ID = "AWS-1";
    private static final double DEFAULT_CAPACITY = 100.0;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LoadBalancer balancer;

    @BeforeAll
    static void setup() throws IOException {
        logger.info("Starting Utils test suite");
        if (Files.exists(TEST_DIR)) {
            Files.walk(TEST_DIR).sorted(Comparator.reverseOrder())
                 .forEach(path -> path.toFile().delete());
        }
        Files.createDirectories(TEST_DIR);
    }

    @BeforeEach
    void reset() throws InterruptedException {
        balancer = new LoadBalancer();
    }

    @AfterEach
    void shutdown() throws InterruptedException {
        balancer.shutdown();
    }

    @AfterAll
    static void cleanup() throws IOException {
        logger.info("Cleaning up test data directory: {}", TEST_DIR);
        Files.walk(TEST_DIR).sorted(Comparator.reverseOrder())
             .forEach(path -> path.toFile().delete());
    }

    private Path createTestFile(String filename, String content) throws IOException {
        Path file = TEST_DIR.resolve(filename);
        Files.writeString(file, content);
        logger.debug("Created test file {} with content: {}", filename, content);
        return file;
    }

    private void assertServerAttributes(Server server, String id, double cpu, double mem, double disk, double capacity) {
        assertEquals(id, server.getServerId(), "Server ID mismatch!");
        assertEquals(cpu, server.getCpuUsage(), 0.01, "CPU usage off!");
        assertEquals(mem, server.getMemoryUsage(), 0.01, "Memory usage off!");
        assertEquals(disk, server.getDiskUsage(), 0.01, "Disk usage off!");
        assertEquals(capacity, server.getCapacity(), 0.01, "Capacity off!");
    }

    private Server createTestServer(String id, double cpu, double mem, double disk, double capacity, ServerType serverType) {
        Server server = new Server(id, cpu, mem, disk, serverType);
        server.setCapacity(capacity);
        return server;
    }

    private Server createTestServer(String id, double cpu, double mem, double disk, double capacity) {
        return createTestServer(id, cpu, mem, disk, capacity, ServerType.ONSITE);
    }

    @Test
    void testImportServerLogsFromCSV() throws IOException {
        logger.info("Testing CSV import for multiple servers");
        Path csvFile = createTestFile("servers.csv", TEST_SERVER_ID_1 + ",30.0,40.0,50.0,200.0\n" + TEST_SERVER_ID_2 + ",20.0,30.0,40.0");
        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);
        assertEquals(2, balancer.getServers().size(), "Didn’t load 2 servers!");
        assertServerAttributes(balancer.getServerMap().get(TEST_SERVER_ID_1), TEST_SERVER_ID_1, 30.0, 40.0, 50.0, 200.0);
        assertServerAttributes(balancer.getServerMap().get(TEST_SERVER_ID_2), TEST_SERVER_ID_2, 20.0, 30.0, 40.0, DEFAULT_CAPACITY);
        logger.info("CSV import test passed: 2 servers loaded with correct attributes");
    }

    @Test
    void testImportServerLogsFromCsvWithQuotedComma() throws IOException {
        logger.info("Testing CSV import with quoted comma fields");
        Path csvFile = createTestFile("quoted-comma.csv", "\"S,1\",30.0,40.0,50.0,200.0,false\n");

        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);

        assertEquals(1, balancer.getServers().size(), "Quoted comma server ID should import as one field!");
        assertServerAttributes(balancer.getServerMap().get("S,1"), "S,1", 30.0, 40.0, 50.0, 200.0);
    }

    @Test
    void testImportServerLogsQuarantinesCsvRowsWithUnexpectedColumns() throws IOException {
        logger.info("Testing CSV import quarantines schema-invalid rows");
        Path csvFile = createTestFile("unexpected-columns.csv",
            TEST_SERVER_ID_1 + ",30.0,40.0,50.0,200.0,false\n"
                + "TOO-MANY,10.0,20.0,30.0,100.0,false,unexpected\n"
                + TEST_SERVER_ID_2 + ",20.0,30.0,40.0");

        assertDoesNotThrow(() -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer),
            "Schema-invalid CSV rows should be quarantined while valid rows continue importing!");

        assertEquals(2, balancer.getServers().size(), "Only schema-valid rows should be loaded!");
        assertTrue(balancer.getServerMap().containsKey(TEST_SERVER_ID_1), "First valid row should load!");
        assertTrue(balancer.getServerMap().containsKey(TEST_SERVER_ID_2), "Valid row after invalid row should load!");
        assertFalse(balancer.getServerMap().containsKey("TOO-MANY"), "Unexpected-column row should be rejected!");
    }

    @Test
    void testCsvInjectionIsRejectedOnImportAndNeutralizedOnExport() throws IOException {
        logger.info("Testing CSV injection protections");
        Path csvFile = createTestFile("csv-injection.csv",
            "=cmd,10.0,20.0,30.0,100.0,false\n"
                + TEST_SERVER_ID_1 + ",30.0,40.0,50.0,200.0,false");

        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);

        assertEquals(1, balancer.getServers().size(), "Formula-like server IDs should be rejected on import!");
        assertFalse(balancer.getServerMap().containsKey("=cmd"), "Formula-like server ID must not be registered!");

        Server dangerous = createTestServer("=SUM(A1:A2)", 10.0, 20.0, 30.0, 100.0);
        Path report = TEST_DIR.resolve("csv-injection-report.csv");
        Utils.exportReport(report.toString(), CSV_FORMAT, java.util.List.of(dangerous), java.util.List.of("@alert"));

        String exported = Files.readString(report);
        assertTrue(exported.contains("'=SUM(A1:A2)"), "Export should neutralize formula-like server IDs!");
        assertTrue(exported.contains("'@alert"), "Export should neutralize formula-like alerts!");
        assertFalse(exported.contains("\n=SUM"), "Exported rows must not start with formula syntax!");
        assertFalse(exported.contains("\n@alert"), "Exported alerts must not start with formula syntax!");
    }

    @Test
    void testImportServerLogsFromJSON() throws IOException {
        logger.info("Testing JSON import for server: {}", TEST_SERVER_ID_1);
        String json = "[{\"serverId\":\"" + TEST_SERVER_ID_1 + "\",\"cpuUsage\":30.0,\"memoryUsage\":40.0,\"diskUsage\":50.0,\"capacity\":200.0}]";
        Path jsonFile = createTestFile("servers.json", json);
        Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer);
        assertEquals(1, balancer.getServers().size(), "Didn’t load 1 server from JSON!");
        assertServerAttributes(balancer.getServerMap().get(TEST_SERVER_ID_1), TEST_SERVER_ID_1, 30.0, 40.0, 50.0, 200.0);
        logger.info("JSON import test passed for server: {}", TEST_SERVER_ID_1);
    }

    @Test
    void testExportReportAsJSON() throws IOException {
        logger.info("Testing JSON export for server: {}", TEST_SERVER_ID_1);
        Server server = createTestServer(TEST_SERVER_ID_1, 30.0, 40.0, 50.0, 200.0);
        server.setWeight(2.0);
        balancer.addServer(server);
        balancer.logAlert("Test Alert");
        Path jsonFile = TEST_DIR.resolve("report.json");
        Utils.exportReport(jsonFile.toString(), JSON_FORMAT, balancer.getServers(), balancer.getAlertLog());
        assertTrue(Files.exists(jsonFile), "JSON report didn’t drop!");
        
        Map<?, ?> report = OBJECT_MAPPER.readValue(jsonFile.toFile(), Map.class);
        assertTrue(report.containsKey("servers"), "Report should contain servers!");
        assertTrue(report.containsKey("alerts"), "Report should contain alerts!");
        logger.info("JSON export test passed for server: {}", TEST_SERVER_ID_1);
    }

    @Test
    void testJsonExportImportsAsRoundTripReport() throws IOException {
        logger.info("Testing JSON export/import round trip");
        Server server = createTestServer("ROUNDTRIP", 30.0, 40.0, 50.0, 200.0, ServerType.CLOUD);
        server.setWeight(2.0);
        balancer.addServer(server);
        balancer.logAlert("Roundtrip alert");
        Path jsonFile = TEST_DIR.resolve("roundtrip-report.json");

        Utils.exportReport(jsonFile.toString(), JSON_FORMAT, balancer.getServers(), balancer.getAlertLog());

        LoadBalancer imported = new LoadBalancer();
        try {
            Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, imported);

            assertEquals(1, imported.getServers().size(), "Exported report should import its server list!");
            Server importedServer = imported.getServerMap().get("ROUNDTRIP");
            assertNotNull(importedServer, "Round-tripped server should be registered!");
            assertServerAttributes(importedServer, "ROUNDTRIP", 30.0, 40.0, 50.0, 200.0);
            assertTrue(importedServer.isCloudInstance(), "Server type should survive JSON round trip!");
            assertEquals(java.util.List.of("Roundtrip alert"), imported.getAlertLog(),
                    "Exported alerts should import from the report contract!");
        } finally {
            imported.shutdown();
        }
    }

    @Test
    void testExportReportAsCSV() throws IOException {
        logger.info("Testing CSV export for server: {}", TEST_SERVER_ID_1);
        Server server = createTestServer(TEST_SERVER_ID_1, 30.0, 40.0, 50.0, 200.0);
        server.setWeight(2.0);
        balancer.addServer(server);
        balancer.logAlert("Test Alert");
        Path csvFile = TEST_DIR.resolve("report.csv");
        Utils.exportReport(csvFile.toString(), CSV_FORMAT, balancer.getServers(), balancer.getAlertLog());
        assertTrue(Files.exists(csvFile), "CSV report didn’t drop!");
        String content = Files.readString(csvFile);
        assertTrue(content.contains("S1,30.00,40.00,50.00,200.00"), "Server data missing!");
        assertTrue(content.contains("Test Alert"), "Alert missing!");
        logger.info("CSV export test passed for server: {}", TEST_SERVER_ID_1);
    }

    @Test
    void testExportReportWithEmptyData() throws IOException {
        logger.info("Testing export with no servers or alerts");
        Path jsonFile = TEST_DIR.resolve("empty_report.json");
        Utils.exportReport(jsonFile.toString(), JSON_FORMAT, balancer.getServers(), balancer.getAlertLog());
        assertTrue(Files.exists(jsonFile), "Empty JSON report didn’t generate!");
        String content = Files.readString(jsonFile);
        assertTrue(content.contains("[]"), "Empty report should contain empty arrays!");
        logger.info("Empty export test passed");
    }

    @Test
    void testHashReturnsConsistentValue() {
        logger.info("Testing hash consistency for input: S1-1");
        long hash1 = Utils.hash("S1-1");
        long hash2 = Utils.hash("S1-1");
        assertEquals(hash1, hash2, "Hash isn’t consistent!");
        logger.info("Hash consistency test passed");
    }

    @Test
    void testParallelHashingProducesExpectedStableHashes() throws Exception {
        logger.info("Testing parallel hash stability");
        int workerCount = 12;
        int keysPerWorker = 600;
        List<String> keys = IntStream.range(0, workerCount * keysPerWorker)
                .mapToObj(i -> "parallel-hash-key-" + i)
                .toList();
        Map<String, Long> expectedHashes = keys.stream()
                .collect(java.util.stream.Collectors.toMap(key -> key, UtilsTest::expectedMd5Long));
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<List<String>>> tasks = new ArrayList<>();
        for (int worker = 0; worker < workerCount; worker++) {
            int from = worker * keysPerWorker;
            int to = from + keysPerWorker;
            tasks.add(() -> {
                start.await();
                List<String> mismatches = new ArrayList<>();
                for (String key : keys.subList(from, to)) {
                    long actual = Utils.hash(key);
                    long expected = expectedHashes.get(key);
                    if (actual != expected) {
                        mismatches.add(key + " expected=" + expected + " actual=" + actual);
                    }
                }
                return mismatches;
            });
        }

        try {
            List<Future<List<String>>> futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            start.countDown();
            List<String> mismatches = new ArrayList<>();
            for (Future<List<String>> future : futures) {
                mismatches.addAll(future.get(10, TimeUnit.SECONDS));
            }
            assertEquals(Collections.emptyList(), mismatches,
                    "Parallel hashing must match independently computed MD5 values!");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Hash test executor should terminate!");
        }
    }

    @Test
    void testHashCacheDoesNotGrowWithoutBound() throws Exception {
        logger.info("Testing hash cache boundedness");
        int maxSafeRetainedKeys = 1000;
        for (int i = 0; i < maxSafeRetainedKeys + 250; i++) {
            Utils.hash("cache-boundary-key-" + i);
        }

        Field cacheField;
        try {
            cacheField = Utils.class.getDeclaredField("hashCache");
        } catch (NoSuchFieldException cacheRemoved) {
            return;
        }
        cacheField.setAccessible(true);
        Object cache = cacheField.get(null);
        assertTrue(cache instanceof Map<?, ?>, "Hash cache should be map-like when present!");
        assertTrue(((Map<?, ?>) cache).size() <= maxSafeRetainedKeys,
                "Hash cache must be explicitly bounded, not just initialized with a starting capacity!");
    }

    private static long expectedMd5Long(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return new BigInteger(1, digest.digest(key.getBytes(StandardCharsets.UTF_8))).longValue();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 should be available in the JVM", e);
        }
    }

    @Test
    void testExportCloudReportAsJSON() throws IOException {
        logger.info("Testing cloud JSON export");
        Server cloudServer = createTestServer("AWS-3", 20.0, 30.0, 40.0, DEFAULT_CAPACITY, ServerType.CLOUD);
        balancer.addServer(cloudServer);
        balancer.logAlert("AWS-3 High CPU Warning");
        Path jsonFile = TEST_DIR.resolve("cloud_report.json");
        Utils.exportReport(jsonFile.toString(), JSON_FORMAT, balancer.getServers(), balancer.getAlertLog());
        assertTrue(Files.exists(jsonFile), "Cloud JSON report didn’t generate!");
        Map<?, ?> report = OBJECT_MAPPER.readValue(jsonFile.toFile(), Map.class);
        assertTrue(report.containsKey("servers"), "Report should contain servers!");
        assertTrue(report.containsKey("alerts"), "Report should contain alerts!");
        logger.info("Cloud JSON export test passed");
    }

    @Test
    void testCloudHashReturnsConsistentValue() {
        logger.info("Testing cloud hash consistency for input: AWS-Server-10");
        long cloudHash1 = Utils.hash("AWS-Server-10");
        long cloudHash2 = Utils.hash("AWS-Server-10");
        assertEquals(cloudHash1, cloudHash2, "Cloud hash should be consistent!");
        logger.info("Cloud hash consistency test passed");
    }

    @Test
    void testImportServerLogsWithInvalidFormat() throws IOException {
        logger.info("Testing import with invalid format: xml");
        Path file = createTestFile("invalid.txt", "Some content");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
            Utils.importServerLogs(file.toString(), "xml", balancer), "Expected IllegalArgumentException!");
        assertTrue(thrown.getMessage().contains("Unsupported format"), "Exception message should mention unsupported format!");
        logger.info("Invalid format test passed");
    }

    @Test
    void testImportServerLogsFromMalformedCSV() throws IOException {
        logger.info("Testing import from malformed CSV");
        Path csvFile = createTestFile("malformed.csv", TEST_SERVER_ID_1 + ",30.0,abc,50.0\n" + TEST_SERVER_ID_2 + ",20.0,30.0,40.0");
        assertDoesNotThrow(() -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer),
            "Should handle malformed CSV gracefully!");
        assertEquals(1, balancer.getServers().size(), "Should skip invalid line and load 1 server!");
        assertEquals(TEST_SERVER_ID_2, balancer.getServers().get(0).getServerId(), "Only S2 should be loaded!");
        logger.info("Malformed CSV test passed");
    }

    @Test
    void testImportServerLogsContinuesAfterInvalidCsvRows() throws IOException {
        logger.info("Testing CSV import continues after invalid rows");
        Path csvFile = createTestFile("mixed-valid-invalid.csv",
            TEST_SERVER_ID_1 + ",30.0,40.0,50.0\n"
                + "BROKEN,30.0,not-a-number,50.0\n"
                + TEST_SERVER_ID_2 + ",20.0,30.0,40.0");

        assertDoesNotThrow(() -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer),
            "Invalid CSV rows should not abort the import!");

        assertEquals(2, balancer.getServers().size(), "Valid rows before and after the invalid row should be loaded!");
        assertTrue(balancer.getServerMap().containsKey(TEST_SERVER_ID_1), "First valid row should be loaded!");
        assertTrue(balancer.getServerMap().containsKey(TEST_SERVER_ID_2), "Valid row after invalid row should be loaded!");
        assertFalse(balancer.getServerMap().containsKey("BROKEN"), "Invalid row should be skipped!");
    }

    @Test
    void testImportServerLogsRejectsNonFiniteCsvMetrics() throws IOException {
        logger.info("Testing CSV import skips non-finite metrics");
        Path csvFile = createTestFile("nonfinite.csv",
            TEST_SERVER_ID_1 + ",NaN,30.0,40.0\n"
                + "S3,Infinity,30.0,40.0\n"
                + TEST_SERVER_ID_2 + ",20.0,30.0,40.0");

        assertDoesNotThrow(() -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer),
            "Should skip non-finite metrics and keep importing valid rows!");

        assertEquals(1, balancer.getServers().size(), "Only the valid CSV row should be loaded!");
        assertEquals(TEST_SERVER_ID_2, balancer.getServers().get(0).getServerId(), "Only S2 should be loaded!");
    }

    @Test
    void testImportServerLogsHandlesCsvCapacityEdgeCases() throws IOException {
        logger.info("Testing CSV import capacity edge cases");
        Path csvFile = createTestFile("capacity-edge-cases.csv",
            "CAP-VALID,10.0,20.0,30.0,250.0\n"
                + "CAP-MISSING,10.0,20.0,30.0\n"
                + "CAP-NEGATIVE,10.0,20.0,30.0,-1.0\n"
                + "CAP-NAN,10.0,20.0,30.0,NaN\n"
                + "CAP-INFINITY,10.0,20.0,30.0,Infinity\n"
                + "CAP-TEXT,10.0,20.0,30.0,not-a-number");

        assertDoesNotThrow(() -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer),
            "Invalid capacity values should be skipped while valid rows continue importing!");

        assertEquals(2, balancer.getServers().size(), "Only valid and missing-capacity rows should be loaded!");
        assertServerAttributes(balancer.getServerMap().get("CAP-VALID"), "CAP-VALID", 10.0, 20.0, 30.0, 250.0);
        assertServerAttributes(balancer.getServerMap().get("CAP-MISSING"), "CAP-MISSING", 10.0, 20.0, 30.0, DEFAULT_CAPACITY);
        assertFalse(balancer.getServerMap().containsKey("CAP-NEGATIVE"), "Negative capacity row should be skipped!");
        assertFalse(balancer.getServerMap().containsKey("CAP-NAN"), "NaN capacity row should be skipped!");
        assertFalse(balancer.getServerMap().containsKey("CAP-INFINITY"), "Infinite capacity row should be skipped!");
        assertFalse(balancer.getServerMap().containsKey("CAP-TEXT"), "Text capacity row should be skipped!");
    }

    @Test
    void testImportServerLogsHandlesCsvCloudFlagSemantics() throws IOException {
        logger.info("Testing CSV import cloud flag semantics");
        Path csvFile = createTestFile("cloud-flag-semantics.csv",
            "CLOUD-TRUE,10.0,20.0,30.0,100.0,true\n"
                + "CLOUD-FALSE,10.0,20.0,30.0,100.0,false\n"
                + "CLOUD-ONE,10.0,20.0,30.0,100.0,1\n"
                + "CLOUD-ZERO,10.0,20.0,30.0,100.0,0\n"
                + "CLOUD-BLANK,10.0,20.0,30.0,100.0,\n"
                + "CLOUD-INVALID,10.0,20.0,30.0,100.0,maybe");

        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);

        assertEquals(5, balancer.getServers().size(), "Only valid cloud flag rows should be parseable!");
        assertTrue(balancer.getServerMap().get("CLOUD-TRUE").isCloudInstance(), "true flag should import as cloud!");
        assertFalse(balancer.getServerMap().get("CLOUD-FALSE").isCloudInstance(), "false flag should import as onsite!");
        assertTrue(balancer.getServerMap().get("CLOUD-ONE").isCloudInstance(), "1 flag should import as cloud!");
        assertFalse(balancer.getServerMap().get("CLOUD-ZERO").isCloudInstance(), "0 flag should import as onsite!");
        assertFalse(balancer.getServerMap().get("CLOUD-BLANK").isCloudInstance(), "Blank flag should import as onsite!");
        assertFalse(balancer.getServerMap().containsKey("CLOUD-INVALID"), "Invalid cloud flag row should be rejected!");
    }

    @Test
    void testImportServerLogsTreatsCsvDelimiterLiterally() throws IOException {
        logger.info("Testing CSV import with a regex metacharacter delimiter");
        Path csvFile = createTestFile("pipe-delimited.csv", TEST_SERVER_ID_1 + "|30.0|40.0|50.0");

        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer, null, "|", true);

        assertEquals(1, balancer.getServers().size(), "Pipe-delimited row should be loaded!");
        assertServerAttributes(balancer.getServerMap().get(TEST_SERVER_ID_1), TEST_SERVER_ID_1, 30.0, 40.0, 50.0, DEFAULT_CAPACITY);
    }

    @Test
    void testImportServerLogsRejectsEmptyCsvDelimiter() throws IOException {
        logger.info("Testing CSV import rejects empty delimiters");
        Path csvFile = createTestFile("empty-delimiter.csv", TEST_SERVER_ID_1 + ",30.0,40.0,50.0");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer, null, "", true),
            "Empty CSV delimiter should be rejected!");

        assertTrue(thrown.getMessage().contains("delimiter"), "Exception should mention delimiter!");
        assertEquals(0, balancer.getServers().size(), "No servers should be loaded after delimiter rejection!");
    }

    @Test
    void testImportServerLogsFromMalformedJsonDocumentThrows() throws IOException {
        logger.info("Testing malformed JSON document fails fast");
        Path jsonFile = createTestFile("malformed.json", "[{\"serverId\":\"S1\"");

        assertThrows(JSONException.class, () -> Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer),
            "Malformed JSON document should throw a JSON parsing exception!");
        assertEquals(0, balancer.getServers().size(), "No servers should be loaded from malformed JSON!");
    }

    @Test
    void testImportServerLogsRejectsInvalidJsonEntryWithoutPartialImport() throws IOException {
        logger.info("Testing JSON import fails fast for invalid entries");
        String json = "["
            + "{\"cpuUsage\":10.0,\"memoryUsage\":20.0,\"diskUsage\":30.0},"
            + "{\"serverId\":\"" + TEST_SERVER_ID_2 + "\",\"cpuUsage\":20.0,\"memoryUsage\":30.0,\"diskUsage\":40.0}"
            + "]";
        Path jsonFile = createTestFile("partially-invalid.json", json);

        assertThrows(IllegalArgumentException.class, () -> Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer),
            "Invalid JSON schema should fail fast before importing later entries!");

        assertEquals(0, balancer.getServers().size(), "Fail-fast JSON validation should avoid partial imports!");
    }

    @Test
    void testImportServerLogsRejectsNonFiniteJsonNumericValues() throws IOException {
        logger.info("Testing JSON import rejects non-finite numeric values");
        String json = "["
            + "{\"serverId\":\"NONFINITE\",\"cpuUsage\":1e9999,\"memoryUsage\":20.0,\"diskUsage\":30.0},"
            + "{\"serverId\":\"" + TEST_SERVER_ID_2 + "\",\"cpuUsage\":20.0,\"memoryUsage\":30.0,\"diskUsage\":40.0}"
            + "]";
        Path jsonFile = createTestFile("nonfinite-json.json", json);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer),
            "Non-finite JSON metrics should fail closed before importing later entries!");

        assertTrue(thrown.getMessage().contains("must be finite"),
                "Schema error should identify the non-finite metric!");
        assertEquals(0, balancer.getServers().size(), "Rejected JSON should not partially import servers!");
    }

    @Test
    void testImportServerLogsRejectsUnexpectedJsonSchemaFields() throws IOException {
        logger.info("Testing JSON schema rejects unexpected fields");
        String json = "{\"version\":1,\"timestamp\":\"2026-04-29 15:00:00\",\"servers\":["
            + "{\"serverId\":\"" + TEST_SERVER_ID_1 + "\",\"cpuUsage\":10.0,\"memoryUsage\":20.0,"
            + "\"diskUsage\":30.0,\"unexpected\":\"drop-me\"}],\"alerts\":[]}";
        Path jsonFile = createTestFile("unexpected-schema.json", json);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer),
            "Unexpected JSON fields should be rejected instead of silently dropped!");

        assertTrue(thrown.getMessage().contains("unexpected"),
                "Schema error should identify the unexpected field!");
        assertEquals(0, balancer.getServers().size(), "Rejected JSON should not partially import servers!");
    }

    @Test
    void testImportServerLogsRejectsJsonWithTrailingData() throws IOException {
        logger.info("Testing JSON import rejects trailing data");
        Path jsonFile = createTestFile("trailing-json.json", "[] {\"unexpected\":true}");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer),
            "Trailing JSON content should be rejected as malformed input!");

        assertTrue(thrown.getMessage().contains("single JSON value"),
                "Schema error should identify trailing content!");
        assertEquals(0, balancer.getServers().size(), "Rejected JSON should not import servers!");
    }

    @Test
    void testImportServerLogsFromNonExistentFile() {
        logger.info("Testing import from non-existent file");
        String nonexistentFile = TEST_DIR.resolve("nonexistent.csv").toString();
        IOException thrown = assertThrows(IOException.class, () ->
            Utils.importServerLogs(nonexistentFile, CSV_FORMAT, balancer), "Expected IOException for missing file!");
        assertTrue(thrown.getMessage().contains("nonexistent.csv"), "Exception message should mention missing file!");
        logger.info("File not found test passed");
    }

    @Test
    void testImportServerLogsFromEmptyCSV() throws IOException {
        logger.info("Testing import from empty CSV");
        Path csvFile = createTestFile("empty.csv", "");
        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);
        assertEquals(0, balancer.getServers().size(), "No servers should be loaded from empty CSV!");
        logger.info("Empty CSV test passed");
    }

    @Test
    void testImportServerLogsFromEmptyJSON() throws IOException {
        logger.info("Testing import from empty JSON");
        Path jsonFile = createTestFile("empty.json", "[]");
        Utils.importServerLogs(jsonFile.toString(), JSON_FORMAT, balancer);
        assertEquals(0, balancer.getServers().size(), "No servers should be loaded from empty JSON!");
        logger.info("Empty JSON test passed");
    }

    @Test
    void testImportPerformanceWithLargeCSV() throws IOException {
        logger.info("Testing import performance with large CSV");
        StringBuilder csvContent = new StringBuilder();
        int serverCount = 1000;
        for (int i = 0; i < serverCount; i++) {
            csvContent.append(String.format("S%d,10.0,20.0,30.0,100.0\n", i));
        }
        Path csvFile = createTestFile("large_servers.csv", csvContent.toString());
        
        long startTime = System.nanoTime();
        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);
        long endTime = System.nanoTime();
        
        assertEquals(serverCount, balancer.getServers().size(), "Didn’t load all servers!");
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("Imported {} servers in {} ms", serverCount, durationMs);
    }

    @Test
    void testLargeCsvWithQuotedFieldsProcessesWithoutCrash() throws IOException {
        logger.info("Testing large CSV import with quoted fields");
        Path csvFile = TEST_DIR.resolve("large_quoted_servers.csv");
        int serverCount = 2500;
        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            for (int i = 0; i < serverCount; i++) {
                writer.write(String.format("\"S,%d\",10.0,20.0,30.0,100.0,false%n", i));
            }
        }

        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);

        assertEquals(serverCount, balancer.getServers().size(), "Large quoted CSV should import without crashing!");
        assertTrue(balancer.getServerMap().containsKey("S,2499"), "Last quoted server ID should import correctly!");
    }

    @Test
    void testImportCloudServerLogs_CSV() throws IOException {
        logger.info("Testing cloud CSV import");
        String csvContent = AWS_SERVER_ID + ",10.0,20.0,30.0,300.0\n" + TEST_SERVER_ID_1 + ",15.0,25.0,35.0,250.0";
        Path csvFile = createTestFile("cloud_servers.csv", csvContent);

        Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer);
        assertEquals(2, balancer.getServers().size(), "Didn’t load 2 servers!");
        assertTrue(balancer.getServerMap().get(AWS_SERVER_ID).isCloudInstance(), "AWS-1 should be a cloud instance!");
        assertEquals(300.0, balancer.getServerMap().get(AWS_SERVER_ID).getCapacity(), 0.01, "AWS-1 capacity mismatch!");
        assertFalse(balancer.getServerMap().get(TEST_SERVER_ID_1).isCloudInstance(), "S1 should not be a cloud instance!");
        logger.info("Cloud CSV import test passed");
    }
}
