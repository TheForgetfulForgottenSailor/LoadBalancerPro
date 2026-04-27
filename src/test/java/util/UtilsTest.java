package test.util;

import core.LoadBalancer;
import core.Server;
import core.ServerType;
import util.Utils;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Map;
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
    void testImportServerLogsRejectsNonFiniteCsvMetrics() throws IOException {
        logger.info("Testing CSV import skips non-finite metrics");
        Path csvFile = createTestFile("nonfinite.csv",
            TEST_SERVER_ID_1 + ",NaN,30.0,40.0\n" + TEST_SERVER_ID_2 + ",20.0,30.0,40.0");

        assertDoesNotThrow(() -> Utils.importServerLogs(csvFile.toString(), CSV_FORMAT, balancer),
            "Should skip non-finite metrics and keep importing valid rows!");

        assertEquals(1, balancer.getServers().size(), "Only the valid CSV row should be loaded!");
        assertEquals(TEST_SERVER_ID_2, balancer.getServers().get(0).getServerId(), "Only S2 should be loaded!");
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
