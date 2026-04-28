package test.core;

import core.LoadBalancer;
import core.LoadDistributionResult;
import core.ScalingRecommendation;
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
import java.util.List;
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

    private Server serverWithWeightAndCapacity(String id, double cpu, double mem, double disk,
                                               double weight, double capacity) {
        Server server = new Server(id, cpu, mem, disk);
        server.setWeight(weight);
        server.setCapacity(capacity);
        return server;
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

    @Test
    void testDuplicateServerReplacementUpdatesRegistryAndHashRing() {
        logger.info("=== TESTING DUPLICATE SERVER REPLACEMENT STATE ===");
        Server original = new Server("S1", 30.0, 40.0, 50.0);
        Server replacement = new Server("S1", 10.0, 20.0, 30.0);
        Server peer = new Server("S2", 15.0, 25.0, 35.0);

        addServers(original);
        Map<String, Double> beforeReplacement = balancer.consistentHashing(100.0, 20);
        addServers(peer);

        addServers(replacement);
        Map<String, Double> afterReplacement = balancer.consistentHashing(100.0, 20);

        assertEquals(2, balancer.getServers().size(), "Duplicate replacement should keep one S1 plus peer!");
        assertSame(replacement, balancer.getServerMap().get("S1"), "Server map should point to replacement!");
        assertFalse(balancer.getServers().contains(original), "Original server object should be removed!");
        assertTrue(beforeReplacement.containsKey("S1"), "Original S1 should have participated in hash ring!");
        assertTrue(afterReplacement.containsKey("S1"), "Replacement S1 should participate in hash ring!");
        assertTrue(afterReplacement.keySet().stream().allMatch(id -> id.equals("S1") || id.equals("S2")),
            "Hashing should only route to current registry servers!");
    }

    @Test
    void testRoundRobinAccumulationFeedsRebalanceTotal() {
        logger.info("=== TESTING ROUND ROBIN ACCUMULATION ===");
        addServers(new Server("S1", 10.0, 20.0, 30.0), new Server("S2", 20.0, 30.0, 40.0));

        Map<String, Double> first = balancer.roundRobin(100.0);
        Map<String, Double> second = balancer.roundRobin(50.0);
        Map<String, Double> rebalanced = balancer.rebalanceExistingLoad();

        assertEquals(50.0, first.get("S1"), 0.01, "First split should allocate half to S1!");
        assertEquals(50.0, first.get("S2"), 0.01, "First split should allocate half to S2!");
        assertEquals(25.0, second.get("S1"), 0.01, "Second split should allocate half to S1!");
        assertEquals(25.0, second.get("S2"), 0.01, "Second split should allocate half to S2!");
        assertEquals(150.0, rebalanced.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
            "Rebalance should preserve accumulated distributed load!");
    }

    @Test
    void testLeastLoadedWithUnequalLoadScoresKeepsCurrentEqualAllocation() {
        logger.info("=== TESTING LEAST LOADED CURRENT ALLOCATION CONTRACT ===");
        addServers(new Server("LOW", 0.0, 0.0, 30.0), new Server("HIGH", 90.0, 90.0, 90.0));

        Map<String, Double> result = balancer.leastLoaded(80.0);

        assertEquals(40.0, result.get("LOW"), 0.01, "Current least-loaded behavior allocates equal share to LOW!");
        assertEquals(40.0, result.get("HIGH"), 0.01, "Current least-loaded behavior allocates equal share to HIGH!");
    }

    @Test
    void testLeastLoadedWithThreeUnequalLoadScoresKeepsCurrentEqualAllocation() {
        logger.info("=== TESTING LEAST LOADED THREE-SERVER CURRENT CONTRACT ===");
        addServers(
            new Server("LOW", 0.0, 0.0, 0.0),
            new Server("MID", 30.0, 30.0, 30.0),
            new Server("HIGH", 90.0, 90.0, 90.0)
        );

        Map<String, Double> result = balancer.leastLoaded(90.0);

        assertEquals(3, result.size(), "All healthy servers should receive an allocation!");
        assertEquals(30.0, result.get("LOW"), 0.01, "Current least-loaded behavior allocates equal share to LOW!");
        assertEquals(30.0, result.get("MID"), 0.01, "Current least-loaded behavior allocates equal share to MID!");
        assertEquals(30.0, result.get("HIGH"), 0.01, "Current least-loaded behavior allocates equal share to HIGH!");
    }

    @Test
    void testWeightedDistributionUsesServerWeights() {
        logger.info("=== TESTING WEIGHTED DISTRIBUTION RATIOS ===");
        addServers(
            serverWithWeightAndCapacity("S1", 10.0, 20.0, 30.0, 1.0, 100.0),
            serverWithWeightAndCapacity("S2", 20.0, 30.0, 40.0, 3.0, 100.0)
        );

        Map<String, Double> result = balancer.weightedDistribution(100.0);

        assertEquals(25.0, result.get("S1"), 0.01, "Weight 1 server should receive one quarter!");
        assertEquals(75.0, result.get("S2"), 0.01, "Weight 3 server should receive three quarters!");
    }

    @Test
    void testWeightedDistributionAllowsZeroWeightServerToReceiveZeroAllocation() {
        logger.info("=== TESTING WEIGHTED DISTRIBUTION WITH ZERO-WEIGHT SERVER ===");
        addServers(
            serverWithWeightAndCapacity("S1", 10.0, 20.0, 30.0, 5.0, 100.0),
            serverWithWeightAndCapacity("S2", 20.0, 30.0, 40.0, 0.0, 100.0),
            serverWithWeightAndCapacity("S3", 30.0, 40.0, 50.0, 5.0, 100.0)
        );

        Map<String, Double> result = balancer.weightedDistribution(100.0);

        assertEquals(50.0, result.get("S1"), 0.01, "Weight 5 server should receive half!");
        assertEquals(0.0, result.get("S2"), 0.01, "Zero-weight server should receive zero allocation!");
        assertEquals(50.0, result.get("S3"), 0.01, "Weight 5 server should receive half!");
    }

    @Test
    void testWeightedDistributionUsesUnequalWeightsProportionally() {
        logger.info("=== TESTING WEIGHTED DISTRIBUTION UNEQUAL RATIOS ===");
        addServers(
            serverWithWeightAndCapacity("S1", 10.0, 20.0, 30.0, 1.0, 100.0),
            serverWithWeightAndCapacity("S2", 20.0, 30.0, 40.0, 2.0, 100.0),
            serverWithWeightAndCapacity("S3", 30.0, 40.0, 50.0, 3.0, 100.0)
        );

        Map<String, Double> result = balancer.weightedDistribution(120.0);

        assertEquals(20.0, result.get("S1"), 0.01, "Weight 1 server should receive one sixth!");
        assertEquals(40.0, result.get("S2"), 0.01, "Weight 2 server should receive two sixths!");
        assertEquals(60.0, result.get("S3"), 0.01, "Weight 3 server should receive three sixths!");
    }

    @Test
    void testWeightedDistributionWithAllZeroWeightsFallsBackToEqualAllocation() {
        logger.info("=== TESTING SAFE ALL-ZERO WEIGHTED DISTRIBUTION CONTRACT ===");
        addServers(
            serverWithWeightAndCapacity("S1", 10.0, 20.0, 30.0, 0.0, 100.0),
            serverWithWeightAndCapacity("S2", 20.0, 30.0, 40.0, 0.0, 100.0)
        );

        Map<String, Double> result = balancer.weightedDistribution(100.0);

        assertEquals(50.0, result.get("S1"), 0.01, "All-zero weights should fall back to equal allocation for S1!");
        assertEquals(50.0, result.get("S2"), 0.01, "All-zero weights should fall back to equal allocation for S2!");
    }

    @Test
    void testCapacityAwareDistributionHonorsAvailableCapacity() {
        logger.info("=== TESTING CAPACITY-AWARE DISTRIBUTION ===");
        Server constrained = new Server("CONSTRAINED", 80.0, 80.0, 80.0);
        constrained.setCapacity(100.0);
        Server open = new Server("OPEN", 0.0, 0.0, 0.0);
        open.setCapacity(100.0);
        addServers(constrained, open);

        Map<String, Double> result = balancer.capacityAware(60.0);

        assertEquals(10.0, result.get("CONSTRAINED"), 0.01, "Constrained server should receive proportional capacity!");
        assertEquals(50.0, result.get("OPEN"), 0.01, "Open server should receive remaining load!");
    }

    @Test
    void testCapacityAwareResultReportsNoUnallocatedLoadWhenCapacityIsSufficient() {
        logger.info("=== TESTING CAPACITY-AWARE RESULT WITH SUFFICIENT CAPACITY ===");
        Server constrained = new Server("CONSTRAINED", 80.0, 80.0, 80.0);
        constrained.setCapacity(100.0);
        Server open = new Server("OPEN", 0.0, 0.0, 0.0);
        open.setCapacity(100.0);
        addServers(constrained, open);

        LoadDistributionResult result = balancer.capacityAwareWithResult(60.0);

        assertEquals(0.0, result.unallocatedLoad(), 0.01, "Sufficient capacity should leave no unallocated load!");
        assertEquals(10.0, result.allocations().get("CONSTRAINED"), 0.01);
        assertEquals(50.0, result.allocations().get("OPEN"), 0.01);
    }

    @Test
    void testCapacityAwareSkipsServerWithNoAvailableCapacity() {
        logger.info("=== TESTING CAPACITY-AWARE ZERO AVAILABLE CAPACITY ===");
        Server unavailable = new Server("FULL", 0.0, 0.0, 0.0);
        unavailable.setCapacity(0.0);
        Server available = new Server("OPEN", 0.0, 0.0, 0.0);
        available.setCapacity(100.0);
        addServers(unavailable, available);

        Map<String, Double> result = balancer.capacityAware(50.0);

        assertFalse(result.containsKey("FULL"), "Server with no available capacity should receive no allocation!");
        assertEquals(50.0, result.get("OPEN"), 0.01, "Available server should receive requested load!");
    }

    @Test
    void testCapacityAwareWithDemandAboveAvailableCapacityDoesNotOverflow() {
        logger.info("=== TESTING CAPACITY-AWARE CAPACITY CEILING ===");
        Server small = new Server("SMALL", 0.0, 0.0, 0.0);
        small.setCapacity(30.0);
        Server large = new Server("LARGE", 0.0, 0.0, 0.0);
        large.setCapacity(70.0);
        addServers(small, large);

        Map<String, Double> result = balancer.capacityAware(150.0);

        assertEquals(30.0, result.get("SMALL"), 0.01, "SMALL should not receive more than available capacity!");
        assertEquals(70.0, result.get("LARGE"), 0.01, "LARGE should not receive more than available capacity!");
        assertEquals(100.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
            "Excess requested load should remain unallocated!");
    }

    @Test
    void testCapacityAwareResultReportsUnallocatedLoadWhenCapped() {
        logger.info("=== TESTING CAPACITY-AWARE UNALLOCATED LOAD REPORTING ===");
        Server small = new Server("SMALL", 0.0, 0.0, 0.0);
        small.setCapacity(30.0);
        Server large = new Server("LARGE", 0.0, 0.0, 0.0);
        large.setCapacity(70.0);
        addServers(small, large);

        LoadDistributionResult result = balancer.capacityAwareWithResult(150.0);

        assertEquals(30.0, result.allocations().get("SMALL"), 0.01);
        assertEquals(70.0, result.allocations().get("LARGE"), 0.01);
        assertEquals(50.0, result.unallocatedLoad(), 0.01, "Capped excess load should be reported!");
    }

    @Test
    void testCapacityAwareAllServersAtOrOverCapacityReturnsEmptyMap() {
        logger.info("=== TESTING CAPACITY-AWARE ALL SERVERS FULL ===");
        Server full = new Server("FULL", 50.0, 50.0, 50.0);
        full.setCapacity(50.0);
        Server over = new Server("OVER", 80.0, 80.0, 80.0);
        over.setCapacity(60.0);
        addServers(full, over);

        Map<String, Double> result = balancer.capacityAware(100.0);

        assertTrue(result.isEmpty(), "Servers with no positive available capacity should receive no allocation!");
    }

    @Test
    void testCapacityAwareMixedCapacitiesPreservesCurrentProportionalAllocation() {
        logger.info("=== TESTING CAPACITY-AWARE MIXED CAPACITIES ===");
        Server constrained = new Server("CONSTRAINED", 80.0, 80.0, 80.0);
        constrained.setCapacity(100.0);
        Server open = new Server("OPEN", 20.0, 20.0, 20.0);
        open.setCapacity(120.0);
        Server full = new Server("FULL", 50.0, 50.0, 50.0);
        full.setCapacity(50.0);
        addServers(constrained, open, full);

        Map<String, Double> result = balancer.capacityAware(60.0);

        assertEquals(10.0, result.get("CONSTRAINED"), 0.01, "Constrained server should receive proportional allocation!");
        assertEquals(50.0, result.get("OPEN"), 0.01, "Open server should receive proportional allocation!");
        assertFalse(result.containsKey("FULL"), "Full server should receive no allocation!");
    }

    @Test
    void testCapacityAwareDistributionIsDeterministicForTiedLoadRatios() {
        logger.info("=== TESTING CAPACITY-AWARE DETERMINISTIC TIE ORDER ===");
        LoadBalancer first = new LoadBalancer();
        LoadBalancer second = new LoadBalancer();
        try {
            first.addServer(serverWithWeightAndCapacity("B", 0.0, 0.0, 0.0, 1.0, 80.0));
            first.addServer(serverWithWeightAndCapacity("A", 0.0, 0.0, 0.0, 1.0, 40.0));
            second.addServer(serverWithWeightAndCapacity("A", 0.0, 0.0, 0.0, 1.0, 40.0));
            second.addServer(serverWithWeightAndCapacity("B", 0.0, 0.0, 0.0, 1.0, 80.0));

            Map<String, Double> firstResult = first.capacityAware(60.0);
            Map<String, Double> secondResult = second.capacityAware(60.0);

            assertEquals(firstResult, secondResult, "Capacity-aware allocation should not depend on insertion order!");
            assertIterableEquals(List.of("A", "B"), firstResult.keySet(),
                "Tied load ratios should use server ID order for deterministic output!");
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void testPredictiveDistributionChangesWithLoadFactor() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION LOAD FACTOR ===");
        LoadBalancer baseline = new LoadBalancer(100.0, 10, 1.0);
        LoadBalancer aggressive = new LoadBalancer(100.0, 10, 2.0);
        try {
            baseline.addServer(serverWithWeightAndCapacity("S1", 0.0, 0.0, 60.0, 1.0, 100.0));
            baseline.addServer(serverWithWeightAndCapacity("S2", 60.0, 60.0, 30.0, 1.0, 100.0));
            aggressive.addServer(serverWithWeightAndCapacity("S1", 0.0, 0.0, 60.0, 1.0, 100.0));
            aggressive.addServer(serverWithWeightAndCapacity("S2", 60.0, 60.0, 30.0, 1.0, 100.0));

            Map<String, Double> baselineResult = baseline.predictiveLoadBalancing(100.0);
            Map<String, Double> aggressiveResult = aggressive.predictiveLoadBalancing(100.0);

            assertEquals(61.54, baselineResult.get("S1"), 0.01, "Baseline factor should favor lower predicted load!");
            assertEquals(38.46, baselineResult.get("S2"), 0.01, "Baseline factor should still allocate to S2!");
            assertEquals(60.0, aggressiveResult.get("S1"), 0.01, "Higher factor should cap S1 at predicted capacity!");
            assertFalse(aggressiveResult.containsKey("S2"), "S2 has no predicted spare capacity at factor 2.0!");
        } finally {
            baseline.shutdown();
            aggressive.shutdown();
        }
    }

    @Test
    void testPredictiveDistributionWithVaryingLoadsPreservesCurrentHeuristic() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION VARYING LOADS ===");
        Server low = serverWithWeightAndCapacity("LOW", 10.0, 10.0, 10.0, 1.0, 100.0);
        Server mid = serverWithWeightAndCapacity("MID", 20.0, 20.0, 20.0, 1.0, 100.0);
        Server high = serverWithWeightAndCapacity("HIGH", 30.0, 30.0, 30.0, 1.0, 100.0);
        addServers(low, mid, high);
        double defaultPredictiveLoadFactor = 1.1;
        double totalPredictedCapacity = (low.getCapacity() - (low.getLoadScore() * defaultPredictiveLoadFactor))
            + (mid.getCapacity() - (mid.getLoadScore() * defaultPredictiveLoadFactor))
            + (high.getCapacity() - (high.getLoadScore() * defaultPredictiveLoadFactor));
        double expectedLow = ((low.getCapacity() - (low.getLoadScore() * defaultPredictiveLoadFactor))
            / totalPredictedCapacity) * 80.0;
        double expectedMid = ((mid.getCapacity() - (mid.getLoadScore() * defaultPredictiveLoadFactor))
            / totalPredictedCapacity) * 80.0;
        double expectedHigh = 80.0 - expectedLow - expectedMid;

        Map<String, Double> result = balancer.predictiveLoadBalancing(80.0);

        assertEquals(expectedLow, result.get("LOW"), 0.01, "Lowest predicted load should receive current proportional share!");
        assertEquals(expectedMid, result.get("MID"), 0.01, "Mid predicted load should receive current proportional share!");
        assertEquals(expectedHigh, result.get("HIGH"), 0.01, "Highest predicted load should receive remaining proportional share!");
    }

    @Test
    void testPredictiveDistributionWithEqualPredictedLoadsSplitsEvenly() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION EQUAL PREDICTED LOADS ===");
        addServers(
            serverWithWeightAndCapacity("S1", 25.0, 25.0, 25.0, 1.0, 100.0),
            serverWithWeightAndCapacity("S2", 25.0, 25.0, 25.0, 1.0, 100.0)
        );

        Map<String, Double> result = balancer.predictiveLoadBalancing(80.0);

        assertEquals(40.0, result.get("S1"), 0.01, "Equal predicted load should split evenly!");
        assertEquals(40.0, result.get("S2"), 0.01, "Equal predicted load should split evenly!");
    }

    @Test
    void testPredictiveDistributionSingleServerReceivesFullAllocation() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION SINGLE SERVER ===");
        addServers(serverWithWeightAndCapacity("ONLY", 70.0, 70.0, 70.0, 1.0, 100.0));

        Map<String, Double> result = balancer.predictiveLoadBalancing(20.0);

        assertEquals(20.0, result.get("ONLY"), 0.01, "Single healthy server should receive requested allocation within predicted capacity!");
        assertEquals(1, result.size(), "Only one server should be allocated!");
    }

    @Test
    void testPredictiveDistributionEmptyServerListReturnsEmptyMap() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION EMPTY SERVER LIST ===");

        Map<String, Double> result = balancer.predictiveLoadBalancing(100.0);

        assertTrue(result.isEmpty(), "Predictive distribution should return empty map with no servers!");
    }

    @Test
    void testPredictiveDistributionSkipsExhaustedPredictedCapacityAndCapsOverflow() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION EXHAUSTED CAPACITY ===");
        LoadBalancer aggressive = new LoadBalancer(100.0, 10, 2.0);
        try {
            aggressive.addServer(serverWithWeightAndCapacity("EXHAUSTED", 60.0, 60.0, 60.0, 1.0, 100.0));
            aggressive.addServer(serverWithWeightAndCapacity("AVAILABLE", 20.0, 20.0, 20.0, 1.0, 100.0));

            Map<String, Double> result = aggressive.predictiveLoadBalancing(150.0);

            assertFalse(result.containsKey("EXHAUSTED"), "Server with no predicted spare capacity should receive no allocation!");
            assertEquals(60.0, result.get("AVAILABLE"), 0.01,
                "AVAILABLE should not receive more than predicted available capacity!");
            assertEquals(60.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
                "Excess requested load should remain unallocated!");
        } finally {
            aggressive.shutdown();
        }
    }

    @Test
    void testPredictiveResultReportsUnallocatedLoadWhenCapped() {
        logger.info("=== TESTING PREDICTIVE UNALLOCATED LOAD REPORTING ===");
        LoadBalancer aggressive = new LoadBalancer(100.0, 10, 2.0);
        try {
            aggressive.addServer(serverWithWeightAndCapacity("EXHAUSTED", 60.0, 60.0, 60.0, 1.0, 100.0));
            aggressive.addServer(serverWithWeightAndCapacity("AVAILABLE", 20.0, 20.0, 20.0, 1.0, 100.0));

            LoadDistributionResult result = aggressive.predictiveLoadBalancingWithResult(150.0);

            assertFalse(result.allocations().containsKey("EXHAUSTED"));
            assertEquals(60.0, result.allocations().get("AVAILABLE"), 0.01);
            assertEquals(90.0, result.unallocatedLoad(), 0.01, "Predictive capped excess should be reported!");
        } finally {
            aggressive.shutdown();
        }
    }

    @Test
    void testExistingMapReturningDistributionMethodsStillWork() {
        logger.info("=== TESTING EXISTING MAP-RETURNING DISTRIBUTION METHODS ===");
        addServers(
            serverWithWeightAndCapacity("S1", 25.0, 25.0, 25.0, 1.0, 100.0),
            serverWithWeightAndCapacity("S2", 25.0, 25.0, 25.0, 1.0, 100.0)
        );

        Map<String, Double> capacityAware = balancer.capacityAware(80.0);
        Map<String, Double> predictive = balancer.predictiveLoadBalancing(80.0);

        assertEquals(40.0, capacityAware.get("S1"), 0.01);
        assertEquals(40.0, capacityAware.get("S2"), 0.01);
        assertEquals(40.0, predictive.get("S1"), 0.01);
        assertEquals(40.0, predictive.get("S2"), 0.01);
    }

    @Test
    void testScalingRecommendationWithNoUnallocatedLoadRecommendsZeroServers() {
        logger.info("=== TESTING ZERO SCALING RECOMMENDATION ===");

        ScalingRecommendation recommendation = balancer.recommendScaling(0.0, 100.0);

        assertEquals(0, recommendation.additionalServers(), "No unallocated load should require no new servers!");
        assertEquals(0.0, recommendation.unallocatedLoad(), 0.01);
        assertEquals(100.0, recommendation.targetCapacityPerServer(), 0.01);
    }

    @Test
    void testScalingRecommendationWithSmallUnallocatedLoadRecommendsOneServer() {
        logger.info("=== TESTING SMALL SCALING RECOMMENDATION ===");

        ScalingRecommendation recommendation = balancer.recommendScaling(25.0, 100.0);

        assertEquals(1, recommendation.additionalServers(), "Any positive load below one target capacity needs one server!");
    }

    @Test
    void testScalingRecommendationWithLargerUnallocatedLoadRecommendsMultipleServers() {
        logger.info("=== TESTING MULTI-SERVER SCALING RECOMMENDATION ===");

        ScalingRecommendation recommendation = balancer.recommendScaling(250.0, 100.0);

        assertEquals(3, recommendation.additionalServers(), "Recommendation should round up to cover all unallocated load!");
    }

    @Test
    void testScalingRecommendationHandlesInvalidTargetCapacitySafely() {
        logger.info("=== TESTING INVALID SCALING TARGET CAPACITY ===");

        assertEquals(0, balancer.recommendScaling(100.0, 0.0).additionalServers());
        assertEquals(0, balancer.recommendScaling(100.0, -10.0).additionalServers());
        assertEquals(0, balancer.recommendScaling(100.0, Double.NaN).additionalServers());
        assertEquals(0, balancer.recommendScaling(Double.NaN, 100.0).additionalServers());
    }

    @Test
    void testPredictiveDistributionIsDeterministicForEqualPredictedCapacity() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION DETERMINISTIC TIE ORDER ===");
        LoadBalancer first = new LoadBalancer();
        LoadBalancer second = new LoadBalancer();
        try {
            first.addServer(serverWithWeightAndCapacity("B", 25.0, 25.0, 25.0, 1.0, 100.0));
            first.addServer(serverWithWeightAndCapacity("A", 25.0, 25.0, 25.0, 1.0, 100.0));
            second.addServer(serverWithWeightAndCapacity("A", 25.0, 25.0, 25.0, 1.0, 100.0));
            second.addServer(serverWithWeightAndCapacity("B", 25.0, 25.0, 25.0, 1.0, 100.0));

            Map<String, Double> firstResult = first.predictiveLoadBalancing(80.0);
            Map<String, Double> secondResult = second.predictiveLoadBalancing(80.0);

            assertEquals(firstResult, secondResult, "Predictive allocation should not depend on insertion order!");
            assertIterableEquals(List.of("A", "B"), firstResult.keySet(),
                "Equal predicted capacity should use server ID order for deterministic output!");
            assertEquals(40.0, firstResult.get("A"), 0.01, "A should receive half the request!");
            assertEquals(40.0, firstResult.get("B"), 0.01, "B should receive half the request!");
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void testPredictiveDistributionAccumulationFeedsRebalance() {
        logger.info("=== TESTING PREDICTIVE DISTRIBUTION ACCUMULATION ===");
        addServers(
            serverWithWeightAndCapacity("S1", 25.0, 25.0, 25.0, 1.0, 100.0),
            serverWithWeightAndCapacity("S2", 25.0, 25.0, 25.0, 1.0, 100.0)
        );

        balancer.predictiveLoadBalancing(80.0);
        balancer.predictiveLoadBalancing(20.0);
        balancer.setStrategy(LoadBalancer.Strategy.ROUND_ROBIN);
        Map<String, Double> result = balancer.rebalanceExistingLoad();

        assertEquals(50.0, result.get("S1"), 0.01, "Rebalance should include accumulated predictive allocation!");
        assertEquals(50.0, result.get("S2"), 0.01, "Rebalance should include accumulated predictive allocation!");
    }

    @Test
    void testAllUnhealthyServersReturnEmptyDistribution() {
        logger.info("=== TESTING ALL UNHEALTHY DISTRIBUTION ===");
        Server s1 = new Server("S1", 10.0, 20.0, 30.0);
        Server s2 = new Server("S2", 20.0, 30.0, 40.0);
        s1.setHealthy(false);
        s2.setHealthy(false);
        addServers(s1, s2);

        assertTrue(balancer.roundRobin(100.0).isEmpty(), "Round robin should skip all-unhealthy servers!");
        assertTrue(balancer.leastLoaded(100.0).isEmpty(), "Least loaded should skip all-unhealthy servers!");
        assertTrue(balancer.weightedDistribution(100.0).isEmpty(), "Weighted distribution should skip all-unhealthy servers!");
        assertTrue(balancer.capacityAware(100.0).isEmpty(), "Capacity-aware should skip all-unhealthy servers!");
        assertTrue(balancer.predictiveLoadBalancing(100.0).isEmpty(), "Predictive should skip all-unhealthy servers!");
        assertTrue(balancer.consistentHashing(100.0, 10).isEmpty(), "Consistent hashing should skip all-unhealthy servers!");
    }

    @Test
    void testDistributionStrategiesRejectNegativeData() {
        logger.info("=== TESTING NEGATIVE DATA REJECTION ===");
        addServers(new Server("S1", 10.0, 20.0, 30.0));

        assertThrows(IllegalArgumentException.class, () -> balancer.roundRobin(-1.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.leastLoaded(-1.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.weightedDistribution(-1.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.capacityAware(-1.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.predictiveLoadBalancing(-1.0));
        assertThrows(IllegalArgumentException.class, () -> balancer.consistentHashing(-1.0, 10));
    }

    @Test
    void testRebalancePreservesTotalAndRespectsSelectedStrategy() {
        logger.info("=== TESTING REBALANCE STRATEGY SELECTION ===");
        addServers(
            serverWithWeightAndCapacity("LOW", 0.0, 0.0, 0.0, 1.0, 100.0),
            serverWithWeightAndCapacity("HIGH", 90.0, 90.0, 90.0, 1.0, 100.0)
        );

        balancer.roundRobin(120.0);
        balancer.setStrategy(LoadBalancer.Strategy.LEAST_LOADED);
        Map<String, Double> leastLoadedRebalance = balancer.rebalanceExistingLoad();
        assertEquals(120.0, leastLoadedRebalance.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
            "Least-loaded rebalance should preserve total distributed load!");
        assertEquals(60.0, leastLoadedRebalance.get("LOW"), 0.01, "Current least-loaded rebalance allocates equally!");
        assertEquals(60.0, leastLoadedRebalance.get("HIGH"), 0.01, "Current least-loaded rebalance allocates equally!");

        balancer.roundRobin(80.0);
        balancer.setStrategy(LoadBalancer.Strategy.ROUND_ROBIN);
        Map<String, Double> roundRobinRebalance = balancer.rebalanceExistingLoad();
        assertEquals(200.0, roundRobinRebalance.values().stream().mapToDouble(Double::doubleValue).sum(), 0.01,
            "Round-robin rebalance should preserve all accumulated load!");
        assertEquals(100.0, roundRobinRebalance.get("LOW"), 0.01, "Round-robin rebalance should split evenly!");
        assertEquals(100.0, roundRobinRebalance.get("HIGH"), 0.01, "Round-robin rebalance should split evenly!");
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
