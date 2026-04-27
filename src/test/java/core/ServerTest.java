package test.core;

import core.Server;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test class for the Server class, verifying its core functionality, metric calculations,
 * JSON serialization/deserialization, cloud server attributes, and error handling.
 *
 * This class uses JUnit 5 to test the Server's load score calculation, dynamic metric updates,
 * JSON round-trip serialization, cloud detection, setter methods, concurrency, and edge cases.
 * Note: The updateMetrics method is synchronized, ensuring thread safety for concurrent updates;
 * other fields (e.g., weight, capacity) are not thread-safe unless explicitly synchronized by callers.
 */
class ServerTest {
    /** Logger for tracking test execution and results. */
    private static final Logger logger = LogManager.getLogger(ServerTest.class);

    /**
     * Sets up the test environment before all test methods.
     *
     * Logs the start of the server test suite.
     */
    @BeforeAll
    static void setup() {
        logger.info("=== SERVER TEST KICKOFF ===");
    }

    /**
     * Helper method to create a Server instance with specified parameters.
     *
     * Creates a server with the given ID and metrics, simplifying test setup.
     *
     * @param id the server ID
     * @param cpu the CPU usage percentage
     * @param mem the memory usage percentage
     * @param disk the disk usage percentage
     * @return a new Server instance
     */
    private Server createServer(String id, double cpu, double mem, double disk) {
        Server server = new Server(id, cpu, mem, disk);
        logger.debug("Created server {} with CPU: {}, Mem: {}, Disk: {}", id, cpu, mem, disk);
        return server;
    }

    /**
     * Tests the calculation of the server's load score.
     *
     * Creates a server with specific CPU, memory, and disk usage values,
     * calculates the expected load score as the average of CPU, memory, and disk usage,
     * and verifies the result matches the implementation.
     */
    @Test
    void testLoadScore_Calculation() {
        logger.info("=== TESTING LOAD SCORE ===");
        Server server = createServer("S1", 50.0, 60.0, 70.0);
        double expected = (50.0 + 60.0 + 70.0) / 3; // 60.0
        assertEquals(expected, server.getLoadScore(), 0.01, "Load score calculation incorrect!");
        logger.info("Load score test passed: Expected {}, got {}", expected, server.getLoadScore());
    }

    /**
     * Tests the dynamic update of server metrics with various input scenarios.
     *
     * Creates a server and updates its metrics with parameterized values,
     * verifying that CPU, memory, disk usage, and load score are correctly updated.
     *
     * @param initialCpu initial CPU usage
     * @param initialMem initial memory usage
     * @param initialDisk initial disk usage
     * @param newCpu new CPU usage
     * @param newMem new memory usage
     * @param newDisk new disk usage
     * @param expectedLoadScore expected load score after update
     */
    @ParameterizedTest
    @CsvSource({
        "30.0, 40.0, 50.0, 80.0, 90.0, 100.0, 90.0",  // Normal update
        "10.0, 20.0, 30.0, 0.0, 0.0, 0.0, 0.0",       // Minimum values
        "50.0, 60.0, 70.0, 100.0, 100.0, 100.0, 100.0" // Maximum values
    })
    void testUpdateMetrics_DynamicShift(double initialCpu, double initialMem, double initialDisk,
                                        double newCpu, double newMem, double newDisk, double expectedLoadScore) {
        logger.info("=== TESTING METRIC UPDATES ===");
        Server server = createServer("S1", initialCpu, initialMem, initialDisk);
        server.updateMetrics(newCpu, newMem, newDisk);
        assertEquals(newCpu, server.getCpuUsage(), 0.01, "CPU didn’t update!");
        assertEquals(newMem, server.getMemoryUsage(), 0.01, "Memory didn’t update!");
        assertEquals(newDisk, server.getDiskUsage(), 0.01, "Disk didn’t update!");
        assertEquals(expectedLoadScore, server.getLoadScore(), 0.01, "Load score didn’t update!");
        logger.info("Metric update test passed: Metrics updated to CPU: {}, Mem: {}, Disk: {}, Load: {}",
                    newCpu, newMem, newDisk, expectedLoadScore);
    }

    /**
     * Tests the round-trip JSON serialization and deserialization of a Server object.
     *
     * Creates a server, sets its weight, capacity, and health, serializes to JSON,
     * deserializes back to a new Server object, and verifies all attributes match.
     */
    @Test
    void testJsonSerialization_RoundTrip() {
        logger.info("=== JSON ROUND TRIP TEST ===");
        Server server = createServer("S1", 30.0, 40.0, 50.0);
        server.setWeight(2.0);
        server.setCapacity(200.0);
        server.setHealthy(false);
        Server fromJson = Server.fromJson(server.toJson());

        assertEquals("S1", fromJson.getServerId(), "Server ID lost in JSON!");
        assertEquals(30.0, fromJson.getCpuUsage(), 0.01, "CPU usage off!");
        assertEquals(40.0, fromJson.getMemoryUsage(), 0.01, "Memory usage off!");
        assertEquals(50.0, fromJson.getDiskUsage(), 0.01, "Disk usage off!");
        assertEquals(2.0, fromJson.getWeight(), 0.01, "Weight didn’t stick!");
        assertEquals(200.0, fromJson.getCapacity(), 0.01, "Capacity didn’t match!");
        assertFalse(fromJson.isHealthy(), "Health status lost in JSON!");
        assertFalse(fromJson.isCloudInstance(), "Cloud instance status should default to false!");
        logger.info("JSON round trip test passed: All attributes preserved.");
    }

    /**
     * Tests the attributes of a cloud server.
     *
     * Creates a server with an "AWS" ID, sets it as a cloud instance and capacity,
     * and verifies the cloud instance status and capacity values.
     */
    @Test
    void testCloudServerAttributes() {
        logger.info("=== CLOUD SERVER ATTRIBUTE TEST ===");
        Server cloudServer = createServer("AWS-Cloud", 15.0, 25.0, 35.0);
        cloudServer.setCloudInstance(true);
        cloudServer.setCapacity(500.0);

        assertTrue(cloudServer.isCloudInstance(), "Server should be recognized as a cloud instance!");
        assertEquals(500.0, cloudServer.getCapacity(), 0.01, "Cloud server capacity should be 500.0!");
        logger.info("Cloud server attributes test passed: Cloud status and capacity correct.");
    }

    /**
     * Tests the JSON serialization and deserialization of a cloud server.
     *
     * Creates a cloud server, sets it as a cloud instance, serializes to JSON,
     * deserializes back, and verifies the ID and cloud instance flag.
     */
    @Test
    void testCloudServerJsonSerialization() {
        logger.info("=== CLOUD SERVER JSON TEST ===");
        Server cloudServer = createServer("AWS-Cloud", 20.0, 30.0, 40.0);
        cloudServer.setCloudInstance(true);
        Server fromJson = Server.fromJson(cloudServer.toJson());

        assertEquals("AWS-Cloud", fromJson.getServerId(), "Cloud server ID mismatch!");
        assertTrue(fromJson.isCloudInstance(), "Cloud instance flag lost after JSON serialization!");
        logger.info("Cloud server JSON test passed: ID and cloud flag preserved.");
    }

    /**
     * Tests the constructor with invalid metric inputs.
     *
     * Attempts to create servers with negative or out-of-range metrics and verifies
     * that an IllegalArgumentException is thrown with the appropriate message.
     */
    @Test
    void testConstructor_InvalidMetrics() {
        logger.info("=== TESTING INVALID METRICS IN CONSTRUCTOR ===");
        assertThrows(IllegalArgumentException.class, () -> new Server("S1", -1.0, 40.0, 50.0),
            "Should reject negative CPU usage!");
        assertThrows(IllegalArgumentException.class, () -> new Server("S1", 30.0, 40.0, 101.0),
            "Should reject disk usage above 100!");
        logger.info("Invalid metrics test passed: Constructor rejects out-of-range values.");
    }

    /**
     * Tests the constructor with an invalid server ID.
     *
     * Attempts to create a server with a null or empty server ID and verifies
     * that an IllegalArgumentException is thrown with the appropriate message.
     */
    @Test
    void testConstructor_InvalidServerId() {
        logger.info("=== TESTING INVALID SERVER ID IN CONSTRUCTOR ===");
        assertThrows(IllegalArgumentException.class, () -> new Server(null, 30.0, 40.0, 50.0),
            "Should reject null server ID!");
        assertThrows(IllegalArgumentException.class, () -> new Server("", 30.0, 40.0, 50.0),
            "Should reject empty server ID!");
        logger.info("Invalid server ID test passed: Constructor rejects null/empty IDs.");
    }

    /**
     * Tests the updateMetrics method with invalid metric inputs.
     *
     * Attempts to update server metrics with negative or out-of-range values and verifies
     * that an IllegalArgumentException is thrown with the appropriate message.
     */
    @Test
    void testUpdateMetrics_InvalidInputs() {
        logger.info("=== TESTING INVALID METRICS IN UPDATE ===");
        Server server = createServer("S1", 30.0, 40.0, 50.0);
        assertThrows(IllegalArgumentException.class, () -> server.updateMetrics(-1.0, 40.0, 50.0),
            "Should reject negative CPU usage!");
        assertThrows(IllegalArgumentException.class, () -> server.updateMetrics(30.0, 40.0, 101.0),
            "Should reject disk usage above 100!");
        logger.info("Invalid update metrics test passed: Method rejects out-of-range values.");
    }

    /**
     * Tests the cloud detection override via system property.
     *
     * Creates servers with and without the "isCloudServer" system property set,
     * and verifies the cloud instance status reflects the override.
     */
    @Test
    void testCloudDetection_Override() {
        logger.info("=== TESTING CLOUD DETECTION OVERRIDE ===");
        System.setProperty("isCloudServer", "true");
        Server cloudServer = createServer("S1", 30.0, 40.0, 50.0);
        assertTrue(cloudServer.isCloudInstance(), "Server should be cloud with override!");

        System.clearProperty("isCloudServer");
        Server nonCloudServer = createServer("S2", 30.0, 40.0, 50.0);
        assertFalse(nonCloudServer.isCloudInstance(), "Server should not be cloud without override!");
        logger.info("Cloud detection override test passed: Override works correctly.");
    }

    /**
     * Tests the setWeight method with various valid and invalid inputs.
     *
     * Sets weights using parameterized values and verifies updates or exceptions as appropriate.
     *
     * @param weight the weight value to set
     * @param shouldThrow whether an exception is expected (true) or not (false)
     */
    @ParameterizedTest
    @CsvSource({
        "3.5, false",   // Valid positive weight
        "0.0, false",   // Valid zero weight
        "-1.0, true"    // Invalid negative weight
    })
    void testSetWeight(double weight, boolean shouldThrow) {
        logger.info("=== TESTING SET WEIGHT ===");
        Server server = createServer("S1", 30.0, 40.0, 50.0);
        if (shouldThrow) {
            assertThrows(IllegalArgumentException.class, () -> server.setWeight(weight),
                "Should reject negative weight!");
        } else {
            server.setWeight(weight);
            assertEquals(weight, server.getWeight(), 0.01, "Weight didn’t update to " + weight + "!");
        }
        logger.info("Set weight test passed: Weight {} handled correctly.", weight);
    }

    /**
     * Tests the setCapacity method with various valid and invalid inputs.
     *
     * Sets capacities using parameterized values and verifies updates or exceptions as appropriate.
     *
     * @param capacity the capacity value to set
     * @param shouldThrow whether an exception is expected (true) or not (false)
     */
    @ParameterizedTest
    @CsvSource({
        "300.0, false",  // Valid positive capacity
        "0.0, false",    // Valid zero capacity
        "-1.0, true"     // Invalid negative capacity
    })
    void testSetCapacity(double capacity, boolean shouldThrow) {
        logger.info("=== TESTING SET CAPACITY ===");
        Server server = createServer("S1", 30.0, 40.0, 50.0);
        if (shouldThrow) {
            assertThrows(IllegalArgumentException.class, () -> server.setCapacity(capacity),
                "Should reject negative capacity!");
        } else {
            server.setCapacity(capacity);
            assertEquals(capacity, server.getCapacity(), 0.01, "Capacity didn’t update to " + capacity + "!");
        }
        logger.info("Set capacity test passed: Capacity {} handled correctly.", capacity);
    }

    /**
     * Tests metric updates with edge case values (0.0 and 100.0).
     *
     * Updates server metrics to boundary values and verifies they are accepted
     * and load score is calculated correctly.
     */
    @Test
    void testUpdateMetrics_EdgeCases() {
        logger.info("=== TESTING METRIC UPDATES WITH EDGE CASES ===");
        Server server = createServer("S1", 30.0, 40.0, 50.0);
        server.updateMetrics(0.0, 0.0, 0.0);
        assertEquals(0.0, server.getCpuUsage(), 0.01, "CPU should update to 0!");
        assertEquals(0.0, server.getLoadScore(), 0.01, "Load score should be 0!");

        server.updateMetrics(100.0, 100.0, 100.0);
        assertEquals(100.0, server.getCpuUsage(), 0.01, "CPU should update to 100!");
        assertEquals(100.0, server.getLoadScore(), 0.01, "Load score should be 100!");
        logger.info("Edge cases test passed: Metrics updated to 0 and 100 correctly.");
    }

    /**
     * Tests concurrent updates to server metrics with reads.
     *
     * Creates a server and performs multiple concurrent updates and reads to its metrics,
     * verifying that the final state is consistent and within valid ranges under stress.
     *
     * @throws InterruptedException if thread operations are interrupted
     */
    @Test
    void testUpdateMetrics_ConcurrentStress() throws InterruptedException {
        logger.info("=== TESTING CONCURRENT METRIC UPDATES AND READS ===");
        Server server = createServer("S1", 30.0, 40.0, 50.0);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Runnable updater1 = () -> server.updateMetrics(80.0, 90.0, 100.0);
        Runnable updater2 = () -> server.updateMetrics(60.0, 70.0, 80.0);
        Runnable reader = () -> {
            server.getCpuUsage(); server.getMemoryUsage(); server.getDiskUsage(); server.getLoadScore();
        };

        // Submit 10 tasks: 5 updates, 5 reads
        for (int i = 0; i < 5; i++) {
            executor.submit(updater1);
            executor.submit(updater2);
            executor.submit(reader);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        double cpu = server.getCpuUsage();
        double mem = server.getMemoryUsage();
        double disk = server.getDiskUsage();
        assertTrue(cpu >= 0 && cpu <= 100, "CPU should be within 0-100 after concurrent updates!");
        assertTrue(mem >= 0 && mem <= 100, "Memory should be within 0-100 after concurrent updates!");
        assertTrue(disk >= 0 && disk <= 100, "Disk should be within 0-100 after concurrent updates!");
        assertEquals((cpu + mem + disk) / 3, server.getLoadScore(), 0.01, "Load score should match average!");
        logger.info("Concurrent stress test passed: Final metrics - CPU: {}, Mem: {}, Disk: {}, Load: {}",
                    cpu, mem, disk, server.getLoadScore());
    }

    /**
     * Tests JSON deserialization with missing required fields.
     *
     * Attempts to deserialize a JSON object missing required fields (e.g., serverId)
     * and verifies that an IllegalArgumentException is thrown.
     */
    @Test
    void testJsonDeserialization_MissingFields() {
        logger.info("=== TESTING JSON DESERIALIZATION WITH MISSING FIELDS ===");
        JSONObject json = new JSONObject();
        json.put("cpuUsage", 30.0);
        json.put("memoryUsage", 40.0);
        json.put("diskUsage", 50.0);
        assertThrows(IllegalArgumentException.class, () -> Server.fromJson(json),
            "Should reject JSON missing serverId!");
        logger.info("Missing fields test passed: Exception thrown for incomplete JSON.");
    }

    /**
     * Tests JSON deserialization with null input.
     *
     * Attempts to deserialize a null JSON object and verifies that an IllegalArgumentException
     * is thrown with the appropriate message.
     */
    @Test
    void testJsonDeserialization_NullInput() {
        logger.info("=== TESTING NULL JSON DESERIALIZATION ===");
        assertThrows(IllegalArgumentException.class, () -> Server.fromJson(null),
            "Should reject null JSON input!");
        logger.info("Null JSON test passed: Exception thrown for null input.");
    }

    /**
     * Tests JSON deserialization with invalid field types.
     *
     * Attempts to deserialize a JSON object with incorrectly typed fields (e.g., string for cpuUsage)
     * and verifies that an IllegalArgumentException is thrown.
     */
    @Test
    void testJsonDeserialization_InvalidTypes() {
        logger.info("=== TESTING JSON DESERIALIZATION WITH INVALID TYPES ===");
        JSONObject json = new JSONObject();
        json.put("serverId", "S1");
        json.put("cpuUsage", "high"); // Invalid type
        json.put("memoryUsage", 40.0);
        json.put("diskUsage", 50.0);
        assertThrows(IllegalArgumentException.class, () -> Server.fromJson(json),
            "Should reject JSON with invalid field types!");
        logger.info("Invalid types test passed: Exception thrown for incorrect field type.");
    }
    @Test
	void testSetHealthyTrue() {
		Server server = createServer("S1", 30.0, 40.0, 50.0);
		server.setHealthy(true);
		assertTrue(server.isHealthy(), "Server should be healthy after setting true!");
	}

}
