package test.core;

import core.Server;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test class for the Server class, verifying its core functionality, metric calculations,
 * and JSON serialization/deserialization, including cloud server attributes.
 *
 * This class uses JUnit 5 to test the Server's load score calculation, dynamic metric updates,
 * JSON round-trip serialization, and cloud-specific attributes.
 *
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
     * Tests the calculation of the server's load score.
     *
     * Creates a server with specific CPU, memory, and disk usage values,
     * calculates the expected load score (weighted sum: CPU*0.4 + Memory*0.3 + Disk*0.3),
     * and verifies the result matches.
     */
    @Test
    void testLoadScore_Calculation() {
        logger.info("=== TESTING LOAD SCORE ===");
        Server server = new Server("S1", 50.0, 60.0, 70.0);
        double expected = (50.0 * 0.4) + (60.0 * 0.3) + (70.0 * 0.3); // 20 + 18 + 21 = 59
        assertEquals(59.0, server.getLoadScore(), 0.01, "Load score calc ain’t right!");
    }

    /**
     * Tests the dynamic update of server metrics.
     *
     * Creates a server with initial metrics, updates them with new values,
     * and verifies that CPU, memory, and disk usage are correctly updated.
     */
    @Test
    void testUpdateMetrics_DynamicShift() {
        logger.info("=== TESTING METRIC UPDATES ===");
        Server server = new Server("S1", 30.0, 40.0, 50.0);
        server.updateMetrics(80.0, 90.0, 100.0);
        assertEquals(80.0, server.getCpuUsage(), "CPU didn’t update!");
        assertEquals(90.0, server.getMemoryUsage(), "Memory didn’t update!");
        assertEquals(100.0, server.getDiskUsage(), "Disk didn’t update!");
    }

    /**
     * Tests the round-trip JSON serialization and deserialization of a Server object.
     *
     * Creates a server, sets its weight and capacity, serializes to JSON,
     * deserializes back to a new Server object, and verifies all attributes match.
     */
    @Test
    void testJsonSerialization_RoundTrip() {
        logger.info("=== JSON ROUND TRIP TEST ===");
        Server server = new Server("S1", 30.0, 40.0, 50.0);
        server.setWeight(2.0);
        server.setCapacity(200.0);
        Server fromJson = Server.fromJson(server.toJson());

        assertEquals("S1", fromJson.getServerId(), "Server ID lost in JSON!");
        assertEquals(30.0, fromJson.getCpuUsage(), "CPU usage off!");
        assertEquals(2.0, fromJson.getWeight(), "Weight didn’t stick!");
        assertEquals(200.0, fromJson.getCapacity(), "Capacity got jacked!");
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
        Server cloudServer = new Server("AWS-Cloud", 15.0, 25.0, 35.0);
        cloudServer.setCloudInstance(true);
        cloudServer.setCapacity(500.0);

        assertTrue(cloudServer.isCloudInstance(), "Server should be recognized as a cloud instance!");
        assertEquals(500.0, cloudServer.getCapacity(), "Cloud server capacity should be 500.0!");
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
        Server cloudServer = new Server("AWS-Cloud", 20.0, 30.0, 40.0);
        cloudServer.setCloudInstance(true);

        Server fromJson = Server.fromJson(cloudServer.toJson());

        assertEquals("AWS-Cloud", fromJson.getServerId(), "Cloud server ID mismatch!");
        assertTrue(fromJson.isCloudInstance(), "Cloud instance flag lost after JSON serialization!");
    }
}
