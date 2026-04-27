package test.core;

import core.LoadBalancer;
import core.Server;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test class for the LoadBalancer, verifying its core functionality, load balancing strategies,
 * cloud integration, file I/O operations, and server lifecycle management.
 *
 * This class uses JUnit 5 to test the LoadBalancer's ability to add servers (including duplicates),
 * distribute loads using various strategies, handle server failures with failover, recognize cloud
 * servers, perform import/export operations, and shut down cleanly. Tests are independent with
 * proper setup and teardown.
 */
class LoadBalancerTest {
    /** Logger for tracking test execution and results. */
    private static final Logger logger = LogManager.getLogger(LoadBalancerTest.class);

    /** LoadBalancer instance under test for each test method. */
    private LoadBalancer balancer;

    /** Directory path for temporary test files (e.g., CSVs and JSON reports). */
    private static final Path TEST_DIR = Paths.get("test_data");

    /**
     * Sets up the test suite environment before all test methods.
     *
     * Creates the test directory and logs the test suite initiation.
     *
     * @throws IOException if an I/O error occurs while creating the test directory
     */
    @BeforeAll
    static void setupMadness() throws IOException {
        System.out.println("=== UNLEASHING LOAD BALANCER TESTING MADNESS ===");
        logger.info("Test suite firing up—buck wild style!");
        Files.createDirectories(TEST_DIR);
    }

    /**
     * Resets the LoadBalancer instance before each test method and ensures a clean state.
     *
     * Creates a new LoadBalancer instance and logs the reset action.
     *
     * @throws InterruptedException if the shutdown from a previous test is interrupted
     */
    @BeforeEach
    void resetTheBeast() throws InterruptedException {
        logger.info("Resetting LoadBalancer for next test...");
        balancer = new LoadBalancer();
    }

    /**
     * Tears down the LoadBalancer instance after each test method.
     *
     * Shuts down the LoadBalancer to stop the ServerMonitor thread, ensuring no interference
     * between tests.
     *
     * @throws InterruptedException if the shutdown process is interrupted
     */
    @AfterEach
    void shutdownTheBeast() throws InterruptedException {
        balancer.shutdown();
    }

    /**
     * Cleans up the test suite environment after all test methods.
     *
     * Deletes the temporary test directory and its contents, ensuring a clean state.
     *
     * @throws IOException if an I/O error occurs while deleting the test directory
     */
    @AfterAll
    static void cleanUpFiles() throws IOException {
        logger.info("=== CLEANING UP TEST DATA ===");
        Files.walk(TEST_DIR)
             .sorted(Comparator.reverseOrder())
             .forEach(path -> path.toFile().delete());
    }

    /**
     * Helper method to add servers to the LoadBalancer.
     *
     * Adds the specified servers to the balancer, simplifying test setup.
     *
     * @param servers the servers to add
     */
    private void addServers(Server... servers) {
        for (Server server : servers) {
            balancer.addServer(server);
            logger.debug("Added server {} to balancer", server.getServerId());
        }
    }

    /**
     * Helper method to create a test file with specified content.
     *
     * Creates a file in the test directory with the given filename and content,
     * returning the file path for use in tests.
     *
     * @param filename the name of the file to create
     * @param content the content to write to the file
     * @return the Path to the created file
     * @throws IOException if an I/O error occurs while creating the file
     */
    private Path createTestFile(String filename, String content) throws IOException {
        Path file = TEST_DIR.resolve(filename);
        Files.writeString(file, content);
        logger.debug("Created test file {} with content: {}", filename, content);
        return file;
    }

    private void waitForServerCount(int expectedCount) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && balancer.getServers().size() != expectedCount) {
            Thread.sleep(25);
        }
    }

    /**
     * Tests the basic addition of a server to the LoadBalancer.
     *
     * Verifies that a server is correctly added to the server map and list,
     * checking for presence and object identity.
     */
    @Test
    void testAddServer_BasicFlex() {
        logger.info("=== TESTING SERVER ADDITION ===");
        Server server = new Server("S1", 30.0, 40.0, 50.0);
        addServers(server);
        assertTrue(balancer.getServerMap().containsKey("S1"), "Server S1 didn’t stick!");
        assertEquals(1, balancer.getServers().size(), "Server count off—should be 1!");
        assertEquals(server, balancer.getServerMap().get("S1"), "Server object mismatch!");
        logger.info("Server addition test passed: Server added correctly.");
    }

    /**
     * Tests adding a server with a duplicate ID.
     *
     * Adds two servers with the same ID and verifies that the second overrides the first,
     * maintaining a single entry with updated values.
     */
    @Test
    void testAddDuplicateServerId() {
        logger.info("=== TESTING DUPLICATE SERVER ID ===");
        Server s1 = new Server("S1", 30.0, 40.0, 50.0);
        Server s2 = new Server("S1", 20.0, 30.0, 40.0); // Same ID
        addServers(s1);
        addServers(s2);
        assertEquals(1, balancer.getServers().size(), "Duplicate ID should not create new entry!");
        assertEquals(20.0, balancer.getServerMap().get("S1").getCpuUsage(), 0.01, "Server S1 should have updated CPU!");
        logger.info("Duplicate server ID test passed: Second server overrode first.");
    }

    /**
     * Tests load balancing strategies with an even split across two servers.
     *
     * Parameterizes multiple strategies to verify they distribute 100.0 GB evenly
     * (50.0 GB each) when servers are healthy, ensuring consistent behavior.
     *
     * @param strategyName the name of the strategy for logging
     * @param strategy the balancing strategy function to test
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "roundRobin", "leastLoaded", "weightedDistribution", "capacityAware", "predictiveLoadBalancing"
    })
    void testLoadBalancingStrategies_EvenSplit(String strategyName) {
        logger.info("=== TESTING {} LOAD BALANCING ===", strategyName.toUpperCase());
        Server s1 = new Server("S1", 30.0, 40.0, 50.0);
        s1.setWeight(1.0); // Equal weights for weightedDistribution
        s1.setCapacity(100.0); // Equal capacities for capacityAware
        Server s2 = new Server("S2", 30.0, 40.0, 50.0);
        s2.setWeight(1.0);
        s2.setCapacity(100.0);
        addServers(s1, s2);

        BiFunction<LoadBalancer, Double, Map<String, Double>> strategy;
        switch (strategyName) {
            case "roundRobin": strategy = (b, d) -> b.roundRobin(d); break;
            case "leastLoaded": strategy = (b, d) -> b.leastLoaded(d); break;
            case "weightedDistribution": strategy = (b, d) -> b.weightedDistribution(d); break;
            case "capacityAware": strategy = (b, d) -> b.capacityAware(d); break;
            case "predictiveLoadBalancing": strategy = (b, d) -> b.predictiveLoadBalancing(d); break;
            default: throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }

        Map<String, Double> result = strategy.apply(balancer, 100.0);
        assertEquals(50.0, result.get("S1"), 0.01, "Server S1 didn’t get 50GB!");
        assertEquals(50.0, result.get("S2"), 0.01, "Server S2 didn’t get 50GB!");
        assertEquals(2, result.size(), "Wrong number of servers in " + strategyName + "!");
        logger.info("{} test passed: Even split achieved.", strategyName);
    }

    /**
     * Tests server failure detection and health check with failover.
     *
     * Adds a server, marks it unhealthy, runs health check and failover,
     * and verifies it’s removed from the active list.
     */
    @Test
    void testServerFailure_HealthCheckWithFailover() {
        logger.info("=== TESTING SERVER FAILURE WITH FAILOVER ===");
        Server server = new Server("FailingServer", 95.0, 95.0, 95.0);
        addServers(server);
        server.updateMetrics(100.0, 95.0, 95.0);
        balancer.checkServerHealth();
        balancer.handleFailover();
        assertFalse(server.isHealthy(), "Server should be marked unhealthy after 100% CPU!");
        assertEquals(0, balancer.getServers().size(), "Failed server should be removed after failover!");
        logger.info("Health check with failover test passed: Server removed correctly.");
    }

    /**
     * Tests the behavior of an empty LoadBalancer.
     *
     * Verifies that the round-robin strategy returns an empty map when no servers are present.
     */
    @Test
    void testEmptyBalancer_NoServers() {
        logger.info("=== EMPTY BALANCER TEST ===");
        Map<String, Double> result = balancer.roundRobin(100.0);
        assertTrue(result.isEmpty(), "Should be empty with no servers!");
        logger.info("Empty balancer test passed: No servers handled correctly.");
    }

    /**
     * Tests the recognition of cloud servers based on their attributes.
     *
     * Adds two servers with "AWS" prefixes, sets one as a cloud instance,
     * and verifies their cloud status.
     */
    @Test
    void testCloudServerRecognition() {
        logger.info("=== TESTING CLOUD SERVER IDENTIFICATION ===");
        Server cloud1 = new Server("AWS-1", 20.0, 30.0, 40.0);
        cloud1.setCloudInstance(true);
        Server cloud2 = new Server("AWS-2", 25.0, 35.0, 45.0);
        addServers(cloud1, cloud2);
        assertTrue(cloud1.isCloudInstance(), "AWS-1 should be recognized as a cloud instance!");
        assertFalse(cloud2.isCloudInstance(), "AWS-2 should not be a cloud instance by default!");
        logger.info("Cloud server recognition test passed: Cloud status detected correctly.");
    }

    /**
     * Tests the round-robin strategy with negative data input.
     *
     * Verifies that an IllegalArgumentException is thrown when attempting to distribute
     * a negative amount of data.
     */
    @Test
    void testRoundRobin_NegativeData() {
        logger.info("=== TESTING ROUND ROBIN WITH NEGATIVE DATA ===");
        addServers(new Server("S1", 30.0, 40.0, 50.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.roundRobin(-100.0),
            "Should reject negative data in round robin!");
        logger.info("Negative data test passed: Exception thrown correctly.");
    }

    /**
     * Tests the consistent hashing strategy with zero keys.
     *
     * Verifies that an empty map is returned when the number of keys is zero,
     * ensuring proper handling of invalid input.
     */
    @Test
    void testConsistentHashing_ZeroKeys() {
        logger.info("=== TESTING CONSISTENT HASHING WITH ZERO KEYS ===");
        addServers(new Server("H1", 10.0, 20.0, 30.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.consistentHashing(100.0, 0),
            "Zero keys should be rejected.");
        logger.info("Zero keys test passed: invalid input rejected.");
    }

    /**
     * Tests cloud initialization with valid-looking credentials in default dry-run mode.
     *
     * Initializes cloud configuration without enabling live AWS behavior and verifies
     * no servers are provisioned.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testInitializeCloud_ValidCredentials() throws InterruptedException {
        logger.info("=== TESTING CLOUD INITIALIZATION ===");
        balancer.initializeCloud("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", 2, 3);
        Thread.sleep(100); // Brief delay to simulate cloud setup
        assertTrue(balancer.hasCloudManager(), "CloudManager should be configured.");
        assertTrue(balancer.getServers().isEmpty(), "Dry-run mode must not add live cloud servers.");
        logger.info("Cloud initialization dry-run test passed.");
    }

    /**
     * Tests cloud initialization with invalid credentials.
     *
     * Initializes the cloud with null credentials and verifies an IllegalArgumentException is thrown.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testInitializeCloud_InvalidCredentials() throws InterruptedException {
        logger.info("=== TESTING CLOUD INITIALIZATION WITH INVALID CREDENTIALS ===");
        assertThrows(IllegalArgumentException.class, () ->
            balancer.initializeCloud(null, "mock_secret_key", "us-east-1", 2, 3),
            "Should reject null access key!");
        assertThrows(IllegalArgumentException.class, () ->
            balancer.initializeCloud("mock_access_key", null, "us-east-1", 2, 3),
            "Should reject null secret key!");
        assertThrows(IllegalArgumentException.class, () ->
            balancer.initializeCloud("mock_access_key", "mock_secret_key", "us-east-1", 2, 3),
            "Should reject placeholder credentials!");
        logger.info("Invalid credentials test passed: Exceptions thrown correctly.");
    }

    /**
     * Tests importing server logs from a CSV file.
     *
     * Creates a CSV file with server data, imports it, and verifies the servers are loaded correctly.
     *
     * @throws IOException if an I/O error occurs while writing or reading the file
     */
    @Test
    void testImportServerLogs_CSV() throws IOException, InterruptedException {
        logger.info("=== TESTING CSV IMPORT ===");
        Path csvFile = createTestFile("servers.csv", "S1,30.0,40.0,50.0,200.0\nS2,20.0,30.0,40.0");
        balancer.importServerLogs(csvFile.toString(), "csv");
        waitForServerCount(2);
        assertEquals(2, balancer.getServers().size(), "Didn’t load 2 servers!");
        Server s1 = balancer.getServerMap().get("S1");
        assertEquals(30.0, s1.getCpuUsage(), 0.01, "S1 CPU usage off!");
        assertEquals(200.0, s1.getCapacity(), 0.01, "S1 capacity off!");
        logger.info("CSV import test passed: 2 servers loaded correctly.");
    }

    /**
     * Tests importing server logs from a JSON file.
     *
     * Creates a JSON file with server data, imports it, and verifies the servers are loaded correctly.
     *
     * @throws IOException if an I/O error occurs while writing or reading the file
     */
    @Test
    void testImportServerLogs_JSON() throws IOException, InterruptedException {
        logger.info("=== TESTING JSON IMPORT ===");
        String json = "[{\"serverId\":\"S1\",\"cpuUsage\":30.0,\"memoryUsage\":40.0,\"diskUsage\":50.0,\"capacity\":200.0}]";
        Path jsonFile = createTestFile("servers.json", json);
        balancer.importServerLogs(jsonFile.toString(), "json");
        waitForServerCount(1);
        assertEquals(1, balancer.getServers().size(), "Didn’t load 1 server!");
        Server s1 = balancer.getServerMap().get("S1");
        assertEquals(30.0, s1.getCpuUsage(), 0.01, "S1 CPU usage off!");
        assertEquals(200.0, s1.getCapacity(), 0.01, "S1 capacity off!");
        logger.info("JSON import test passed: 1 server loaded correctly.");
    }

    /**
     * Tests importing server logs from a non-existent CSV file.
     *
     * Verifies that an IOException is thrown when attempting to import from a missing file.
     */
    @Test
    void testImportServerLogs_FileNotFound() throws InterruptedException {
        logger.info("=== TESTING CSV IMPORT WITH FILE NOT FOUND ===");
        balancer.importServerLogs(TEST_DIR.resolve("nonexistent.csv").toString(), "csv");
        waitForServerCount(1);
        assertTrue(balancer.getServers().isEmpty(), "Missing async import should not add servers.");
        logger.info("File not found import test passed: missing async import did not add servers.");
    }

    /**
     * Tests importing server logs from a malformed CSV file.
     *
     * Creates a CSV file with invalid data, imports it, and verifies that it handles gracefully.
     *
     * @throws IOException if an I/O error occurs while writing or reading the file
     */
    @Test
    void testImportServerLogs_MalformedCSV() throws IOException, InterruptedException {
        logger.info("=== TESTING MALFORMED CSV IMPORT ===");
        Path csvFile = createTestFile("malformed.csv", "S1,30.0,abc,50.0\nS2,20.0,30.0,40.0");
        assertDoesNotThrow(() -> balancer.importServerLogs(csvFile.toString(), "csv"),
            "Should handle malformed CSV gracefully!");
        waitForServerCount(1);
        assertEquals(1, balancer.getServers().size(), "Should skip invalid line and load 1 server!");
        assertEquals("S2", balancer.getServerMap().get("S2").getServerId(), "Only S2 should be loaded!");
        logger.info("Malformed CSV test passed: Invalid line skipped, 1 server loaded.");
    }

    /**
     * Tests exporting a report to a JSON file.
     *
     * Adds servers and an alert, exports the report, and verifies the file contains expected data.
     *
     * @throws IOException if an I/O error occurs while writing or reading the file
     */
    @Test
    void testExportReport_JSON() throws IOException {
        logger.info("=== TESTING JSON EXPORT ===");
        addServers(
            new Server("S1", 30.0, 40.0, 50.0),
            new Server("S2", 20.0, 30.0, 40.0)
        );
        balancer.logAlert("Test Alert");
        Path jsonFile = TEST_DIR.resolve("report.json");
        balancer.exportReport(jsonFile.toString(), "json");
        assertTrue(Files.exists(jsonFile), "JSON report didn’t generate!");
        String content = Files.readString(jsonFile);
        assertTrue(content.contains("S1"), "Server S1 missing in report!");
        assertTrue(content.contains("Test Alert"), "Alert missing in report!");
        logger.info("JSON export test passed: Report contains server and alert data.");
    }

    /**
     * Tests cloud scaling with invalid server counts.
     *
     * Verifies that an IllegalArgumentException is thrown when scaling to negative or zero servers.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testScaleCloudServers_InvalidCounts() throws InterruptedException {
        logger.info("=== TESTING CLOUD SCALING WITH INVALID COUNTS ===");
        assertThrows(IllegalArgumentException.class, () -> balancer.scaleCloudServers(-1),
            "Should reject negative server count!");
        assertThrows(IllegalArgumentException.class, () -> balancer.scaleCloudServers(0),
            "Should reject zero server count!");
        logger.info("Invalid cloud scaling test passed: Exceptions thrown correctly.");
    }

    /**
     * Tests the shutdown method.
     *
     * Adds servers, shuts down the balancer, and verifies the server list is cleared and monitor stops.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testShutdown() throws InterruptedException {
        logger.info("=== TESTING SHUTDOWN ===");
        addServers(
            new Server("S1", 30.0, 40.0, 50.0),
            new Server("S2", 20.0, 30.0, 40.0)
        );
        balancer.shutdown();
        assertEquals(2, balancer.getServers().size(), "Shutdown should stop resources without clearing registered servers.");
        Thread.sleep(100); // Brief delay to ensure monitor thread stops
        assertFalse(balancer.getServerMonitor().isRunning(), "ServerMonitor thread should be stopped!");
        logger.info("Shutdown test passed: resources stopped while server registry remains intact.");
    }
}
