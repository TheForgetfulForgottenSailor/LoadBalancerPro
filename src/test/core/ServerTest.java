package test.core;

import core.Server;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ServerTest {
    private static final Logger logger = LogManager.getLogger(ServerTest.class);

    @BeforeAll
    static void setup() {
        logger.info("=== SERVER TEST KICKOFF ===");
    }

    @Test
    void testLoadScore_Calculation() {
        logger.info("=== TESTING LOAD SCORE ===");
        Server server = new Server("S1", 50.0, 60.0, 70.0);
        double expected = (50.0 * 0.4) + (60.0 * 0.3) + (70.0 * 0.3); // 20 + 18 + 21 = 59
        assertEquals(59.0, server.getLoadScore(), 0.01, "Load score calc ain’t right!");
    }

    @Test
    void testUpdateMetrics_DynamicShift() {
        logger.info("=== TESTING METRIC UPDATES ===");
        Server server = new Server("S1", 30.0, 40.0, 50.0);
        server.updateMetrics(80.0, 90.0, 100.0);
        assertEquals(80.0, server.getCpuUsage(), "CPU didn’t update!");
        assertEquals(90.0, server.getMemoryUsage(), "Memory didn’t update!");
        assertEquals(100.0, server.getDiskUsage(), "Disk didn’t update!");
    }

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
    @Test
void testCloudServerAttributes() {
    logger.info("=== CLOUD SERVER ATTRIBUTE TEST ===");
    Server cloudServer = new Server("AWS-Cloud", 15.0, 25.0, 35.0);
    cloudServer.setCloudInstance(true);
    cloudServer.setCapacity(500.0);

    assertTrue(cloudServer.isCloudInstance(), "Server should be recognized as a cloud instance!");
    assertEquals(500.0, cloudServer.getCapacity(), "Cloud server capacity should be 500.0!");
}

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
