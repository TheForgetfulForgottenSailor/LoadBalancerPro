package test.util;

import core.LoadBalancer;
import core.Server;
import util.Utils;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;

class UtilsTest {
    private static final Logger logger = LogManager.getLogger(UtilsTest.class);
    private static final Path TEST_DIR = Paths.get("test_data");
    private LoadBalancer balancer;

    @BeforeAll
    static void setup() throws IOException {
        logger.info("=== UTILS TEST KICKOFF ===");
        Files.createDirectories(TEST_DIR);
    }

    @BeforeEach
    void reset() {
        balancer = new LoadBalancer();
    }

    @Test
    void testImportServerLogs_CSV() throws IOException {
        logger.info("=== CSV IMPORT TEST ===");
        Path csvFile = TEST_DIR.resolve("servers.csv");
        Files.writeString(csvFile, "S1,30.0,40.0,50.0,200.0\nS2,20.0,30.0,40.0");
        Utils.importServerLogs(csvFile.toString(), "csv", balancer);
        assertEquals(2, balancer.getServers().size(), "Didn’t load 2 servers!");
        assertEquals(200.0, balancer.serverMap.get("S1").getCapacity(), "S1 capacity off!");
    }

    @Test
    void testExportReport_JSON() throws IOException {
        logger.info("=== JSON EXPORT TEST ===");
        balancer.addServer(new Server("S1", 30.0, 40.0, 50.0));
        balancer.logAlert("Test Alert");
        Path jsonFile = TEST_DIR.resolve("report.json");
        Utils.exportReport(jsonFile.toString(), "json", balancer.getServers(), balancer.getAlertLog());
        assertTrue(Files.exists(jsonFile), "JSON report didn’t drop!");
        String content = Files.readString(jsonFile);
        assertTrue(content.contains("S1"), "Server S1 missing in report!");
        assertTrue(content.contains("Test Alert"), "Alert missing in report!");
    }

    @Test
    void testHash_Consistency() {
        logger.info("=== HASH CONSISTENCY TEST ===");
        long hash1 = Utils.hash("S1-1");
        long hash2 = Utils.hash("S1-1");
        assertEquals(hash1, hash2, "Hash ain’t consistent!");
    }

    @AfterAll
    static void cleanup() throws IOException {
        logger.info("=== CLEANING UP TEST DATA ===");
        Files.walk(TEST_DIR).sorted(Comparator.reverseOrder())
             .forEach(path -> path.toFile().delete());
    }
    @Test
	void testImportCloudServerLogs_CSV() throws IOException {
		logger.info("=== CLOUD CSV IMPORT TEST ===");
		Path csvFile = TEST_DIR.resolve("cloud_servers.csv");
		Files.writeString(csvFile, "AWS-1,10.0,20.0,30.0,300.0,true\nAWS-2,15.0,25.0,35.0,250.0,true");

		Utils.importServerLogs(csvFile.toString(), "csv", balancer);

		assertEquals(2, balancer.getServers().size(), "Didn’t load 2 cloud servers!");
		assertTrue(balancer.serverMap.get("AWS-1").isCloudInstance(), "AWS-1 should be a cloud instance!");
		assertEquals(300.0, balancer.serverMap.get("AWS-1").getCapacity(), "AWS-1 capacity mismatch!");
	}
	@Test
	void testExportCloudReport_JSON() throws IOException {
		logger.info("=== CLOUD JSON EXPORT TEST ===");
		Server cloudServer = new Server("AWS-3", 20.0, 30.0, 40.0);
		cloudServer.setCloudInstance(true);
		balancer.addServer(cloudServer);
		balancer.logAlert("AWS-3 High CPU Warning");

		Path jsonFile = TEST_DIR.resolve("cloud_report.json");
		Utils.exportReport(jsonFile.toString(), "json", balancer.getServers(), balancer.getAlertLog());

		assertTrue(Files.exists(jsonFile), "Cloud JSON report didn’t generate!");
		String content = Files.readString(jsonFile);
		assertTrue(content.contains("AWS-3"), "Cloud server AWS-3 missing in report!");
		assertTrue(content.contains("High CPU Warning"), "Cloud alert missing in report!");
	}
	@Test
	void testCloudHash_Consistency() {
		logger.info("=== CLOUD HASH CONSISTENCY TEST ===");
		long cloudHash1 = Utils.hash("AWS-Server-10");
		long cloudHash2 = Utils.hash("AWS-Server-10");

		assertEquals(cloudHash1, cloudHash2, "Cloud hash should be consistent!");
	}
	
}
