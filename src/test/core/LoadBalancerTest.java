package test.core;

import core.LoadBalancer;
import core.Server;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test class for the LoadBalancer, verifying its core functionality and load balancing strategies.
 *
 * This class uses JUnit 5 with a custom method order to test the LoadBalancer's ability to add servers,
 * distribute loads using various strategies, handle server failures, and recognize cloud servers.
 * Tests are executed in a specific order defined by the `@Order` annotation.
 *
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoadBalancerTest {
    /** Logger for tracking test execution and results. */
    private static final Logger logger = LogManager.getLogger(LoadBalancerTest.class);

    /** LoadBalancer instance under test for each test method. */
    private LoadBalancer balancer;

    /**
     * Sets up the test suite environment before all test methods.
     *
     * Prints a startup message to the console and logs the test suite initiation.
     */
    @BeforeAll
    static void setupMadness() {
        System.out.println("=== UNLEASHING LOAD BALANCER TESTING MADNESS ===");
        logger.info("Test suite firing up—buck wild style!");
    }

    /**
     * Resets the LoadBalancer instance before each test method.
     *
     * Logs the reset action and creates a new LoadBalancer instance to ensure a clean state.
     */
    @BeforeEach
    void resetTheBeast() {
        logger.info("Resetting LoadBalancer for next test...");
        balancer = new LoadBalancer();
    }

    /**
     * Tests the basic addition of a server to the LoadBalancer.
     *
     * Verifies that a server is correctly added to the server map and list,
     * checking for presence and object identity.
     *
     * @order 1
     */
    @Test
    @Order(1)
    void testAddServer_BasicFlex() {
        logger.info("=== TESTING SERVER ADDITION ===");
        Server server = new Server("S1", 30.0, 40.0, 50.0);
        balancer.addServer(server);
        assertTrue(balancer.serverMap.containsKey("S1"), "Server S1 didn’t stick, fam!");
        assertEquals(1, balancer.getServers().size(), "Server count off—should be 1!");
        assertEquals(server, balancer.serverMap.get("S1"), "Server object mismatch!");
    }

    /**
     * Tests the round-robin load balancing strategy with an even split.
     *
     * Adds two servers and verifies that the total data (100.0 GB) is evenly
     * distributed (50.0 GB each) between them.
     *
     * @order 2
     */
    @Test
    @Order(2)
    void testRoundRobinLoadBalancing_EvenSplit() {
        logger.info("=== ROUND ROBIN LOAD BLAST ===");
        balancer.addServer(new Server("A", 30.0, 40.0, 50.0));
        balancer.addServer(new Server("B", 20.0, 30.0, 40.0));
        Map<String, Double> result = balancer.roundRobin(100.0);
        assertEquals(50.0, result.get("A"), 0.01, "Server A didn’t get 50GB!");
        assertEquals(50.0, result.get("B"), 0.01, "Server B didn’t get 50GB!");
        assertEquals(2, result.size(), "Wrong number of servers in round robin!");
    }

    /**
     * Tests the least-loaded load balancing strategy with smart data split.
     *
     * Adds two servers with different loads and verifies that the least loaded
     * server (B) receives more data, with the total summing to 100.0 GB.
     *
     * @order 3
     */
    @Test
    @Order(3)
    void testLeastLoadedDistribution_SmartSplit() {
        logger.info("=== LEAST LOADED LOAD BLAST ===");
        Server server1 = new Server("A", 30.0, 40.0, 50.0);
        Server server2 = new Server("B", 10.0, 20.0, 30.0);
        balancer.addServer(server1);
        balancer.addServer(server2);
        Map<String, Double> result = balancer.leastLoaded(100.0);
        assertTrue(result.get("B") >= result.get("A"), "Least loaded didn’t favor B!");
        assertEquals(100.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
                     "Total distribution ain’t 100GB!");
    }

    /**
     * Tests server failure detection and health check functionality.
     *
     * Adds a server, sets its CPU to 100%, runs a health check, and verifies
     * the server is marked unhealthy and removed.
     *
     * @order 4
     */
    @Test
    @Order(4)
    void testServerFailure_HealthCheck() {
        logger.info("=== SMOKING A SERVER ===");
        Server server = new Server("FailingServer", 95.0, 95.0, 95.0);
        balancer.addServer(server);
        server.updateMetrics(100.0, 95.0, 95.0);
        balancer.checkServerHealth();
        assertFalse(server.isHealthy(), "Server should be toast after 100% CPU!");
        assertEquals(0, balancer.serverMap.size(), "Failed server didn’t get yanked!");
    }

    /**
     * Tests the weighted distribution strategy based on server weights.
     *
     * Adds two servers with weights (2.0 and 1.0), distributes 90.0 GB,
     * and verifies the weighted split (60.0 GB and 30.0 GB).
     *
     * @order 5
     */
    @Test
    @Order(5)
    void testWeightedDistribution_WeightMatters() {
        logger.info("=== WEIGHTED LOAD BLAST ===");
        Server s1 = new Server("W1", 30.0, 40.0, 50.0);
        s1.setWeight(2.0);
        Server s2 = new Server("W2", 20.0, 30.0, 40.0);
        s2.setWeight(1.0);
        balancer.addServer(s1);
        balancer.addServer(s2);
        Map<String, Double> result = balancer.weightedDistribution(90.0);
        assertEquals(60.0, result.get("W1"), 0.01, "W1 should get 2/3 of 90GB!");
        assertEquals(30.0, result.get("W2"), 0.01, "W2 should get 1/3 of 90GB!");
    }

    /**
     * Tests the consistent hashing strategy for data key distribution.
     *
     * Adds two servers, distributes 100.0 GB across 10 keys, and verifies
     * both servers are used and the total matches.
     *
     * @order 6
     */
    @Test
    @Order(6)
    void testConsistentHashing_KeySpread() {
        logger.info("=== CONSISTENT HASHING BLAST ===");
        balancer.addServer(new Server("H1", 10.0, 20.0, 30.0));
        balancer.addServer(new Server("H2", 20.0, 30.0, 40.0));
        Map<String, Double> result = balancer.consistentHashing(100.0, 10);
        assertEquals(2, result.size(), "Should hit both servers!");
        assertEquals(100.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
                     "Total data ain’t 100GB!");
    }

    /**
     * Tests the capacity-aware distribution strategy.
     *
     * Adds two servers with different capacities, distributes 100.0 GB,
     * and verifies the server with higher capacity gets more data.
     *
     * @order 7
     */
    @Test
    @Order(7)
    void testCapacityAware_CapacityRules() {
        logger.info("=== CAPACITY-AWARE BLAST ===");
        Server s1 = new Server("C1", 20.0, 20.0, 20.0);
        s1.setCapacity(200.0);
        Server s2 = new Server("C2", 50.0, 50.0, 50.0);
        s2.setCapacity(100.0);
        balancer.addServer(s1);
        balancer.addServer(s2);
        Map<String, Double> result = balancer.capacityAware(100.0);
        assertTrue(result.get("C1") > result.get("C2"), "C1 should get more with higher capacity!");
    }

    /**
     * Tests the predictive load balancing strategy.
     *
     * Adds two servers with different loads and capacities, distributes 100.0 GB,
     * and verifies the server with lower predicted load gets more data.
     *
     * @order 8
     */
    @Test
    @Order(8)
    void testPredictiveLoadBalancing_FutureProof() {
        logger.info("=== PREDICTIVE LOAD BLAST ===");
        Server s1 = new Server("P1", 20.0, 20.0, 20.0);
        s1.setCapacity(150.0);
        Server s2 = new Server("P2", 80.0, 80.0, 80.0);
        s2.setCapacity(100.0);
        balancer.addServer(s1);
        balancer.addServer(s2);
        Map<String, Double> result = balancer.predictiveLoadBalancing(100.0);
        assertTrue(result.get("P1") > result.get("P2"), "P1 should dominate with lower predicted load!");
    }

    /**
     * Tests the behavior of an empty LoadBalancer.
     *
     * Verifies that the round-robin strategy returns an empty map when no servers are present.
     *
     * @order 9
     */
    @Test
    @Order(9)
    void testEmptyBalancer_NoServers() {
        logger.info("=== EMPTY BALANCER TEST ===");
        Map<String, Double> result = balancer.roundRobin(100.0);
        assertTrue(result.isEmpty(), "Should be empty with no servers, dawg!");
    }

    /**
     * Tests the recognition of cloud servers based on their IDs.
     *
     * Adds two servers with "AWS" prefixes and verifies their IDs start with "AWS".
     *
     * @order 10
     */
    @Test
    @Order(10)
    void testCloudServerRecognition() {
        logger.info("=== TESTING CLOUD SERVER IDENTIFICATION ===");
        Server cloud1 = new Server("AWS-1", 20.0, 30.0, 40.0);
        Server cloud2 = new Server("AWS-2", 25.0, 35.0, 45.0);
        balancer.addServer(cloud1);
        balancer.addServer(cloud2);

        assertTrue(cloud1.getServerId().startsWith("AWS"), "AWS-1 should be recognized as a cloud instance!");
        assertTrue(cloud2.getServerId().startsWith("AWS"), "AWS-2 should be recognized as a cloud instance!");
    }

    /**
     * Tears down the test suite environment after all test methods.
     *
     * Logs the shutdown of the test suite and prints a completion message.
     */
    @AfterAll
    static void tearDown() {
        logger.info("=== LOAD BALANCER TEST MADNESS SHUTS DOWN ===");
        System.out.println("=== TEST SUITE DONE ===");
    }
}
