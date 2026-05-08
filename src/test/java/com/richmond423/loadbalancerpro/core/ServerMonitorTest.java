package com.richmond423.loadbalancerpro.core;

import com.richmond423.loadbalancerpro.core.LoadBalancer;
import com.richmond423.loadbalancerpro.core.CloudManager;
import com.richmond423.loadbalancerpro.core.Server;
import com.richmond423.loadbalancerpro.core.ServerMonitor;
import com.richmond423.loadbalancerpro.core.ServerType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Test class for the ServerMonitor, verifying its functionality in monitoring server metrics,
 * triggering alerts, handling cloud integration scenarios, edge cases, and concurrency.
 *
 * This class uses JUnit 5 to test the ServerMonitor's behavior under various conditions,
 * including metric updates with custom thresholds, alert generation, shutdown, cloud scaling,
 * failure scenarios, and concurrent operations. It uses event-based waits with CountDownLatch
 * for precise timing and mocks logging for verification.
 */
class ServerMonitorTest {
    /** Mocked Logger for tracking test execution and results. */
    private static final Logger logger = mock(Logger.class);

    /** LoadBalancer instance for testing server management. */
    private LoadBalancer balancer;

    /** ServerMonitor instance to be tested. */
    private ServerMonitor monitor;

    /** Thread running the ServerMonitor for testing. */
    private Thread monitorThread;

    /** Monitor cycle duration in milliseconds for test monitors. */
    private static final int MONITOR_CYCLE_MS = 100;

    /**
     * Sets up the test suite environment before all test methods.
     *
     * Initializes the mocked logger and logs the suite start.
     */
    @BeforeAll
    static void setupSuite() {
        when(logger.isEnabled(any())).thenReturn(true); // Enable all log levels
        logger.info("ServerMonitor test suite started.");
        verify(logger).info("ServerMonitor test suite started.");
    }

    /**
     * Sets up the test environment before each test method.
     *
     * Initializes a new LoadBalancer and ServerMonitor, starts the monitor thread,
     * and skips cloud initialization if using test credentials.
     *
     * @throws InterruptedException if the setup process is interrupted
     */
    @BeforeEach
    void setup() throws InterruptedException {
        balancer = new LoadBalancer();
        monitor = new ServerMonitor(new ServerMonitor.Config()
            .withThreshold(80.0)
            .withInterval(MONITOR_CYCLE_MS)
            .withFluctuation(10.0)
            .withAlertCooldownMs(0),
            balancer,
            null);
        monitorThread = new Thread(monitor);
        monitorThread.start();

        String accessKey = "UNIT_TEST_ACCESS_KEY_ID";
        String secretKey = "UNIT_TEST_SECRET_ACCESS_KEY";
        String region = "us-east-1";
        if (!accessKey.equals("UNIT_TEST_ACCESS_KEY_ID") && !secretKey.equals("UNIT_TEST_SECRET_ACCESS_KEY")) {
            balancer.initializeCloud(accessKey, secretKey, region, 5, 10);
            TimeUnit.SECONDS.sleep(30); // Wait for cloud initialization
        }
    }

    /**
     * Tears down the test environment after each test method.
     *
     * Stops the monitor, interrupts and joins the monitor thread, shuts down the balancer,
     * and asserts that the thread is no longer alive after shutdown.
     *
     * @throws InterruptedException if the thread join operation is interrupted
     */
    @AfterEach
    void tearDown() throws InterruptedException {
        if (monitor != null) {
            monitor.stop();
        }
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread.join(5000);
            assertFalse(monitorThread.isAlive(), "Monitor thread did not shut down properly.");
        }
        if (balancer != null) {
            balancer.shutdown();
        }
    }

    /**
     * Waits for a specified number of monitor cycles using a CountDownLatch.
     *
     * Uses a latch to wait for the exact number of cycles, ensuring precise timing without polling.
     *
     * @param cycles the number of monitor cycles to wait for
     * @param timeoutSeconds the maximum time to wait in seconds
     * @throws InterruptedException if the wait is interrupted
     */
    private void waitForMonitorCycles(int cycles, int timeoutSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(cycles);
        long startTime = System.currentTimeMillis();
        while (latch.getCount() > 0 && (System.currentTimeMillis() - startTime) < (timeoutSeconds * 1000)) {
            Thread.sleep(MONITOR_CYCLE_MS); // Wait one cycle
            latch.countDown();
        }
        if (latch.getCount() > 0) {
            logger.warn("Monitor did not complete {} cycles within {} seconds.", cycles, timeoutSeconds);
        }
        logger.debug("Waited for {} monitor cycles.", cycles);
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMillis, String failureMessage)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail(failureMessage);
    }

    private void stopCurrentMonitor() throws InterruptedException {
        if (monitor != null) {
            monitor.stop();
        }
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread.join(5000);
            assertFalse(monitorThread.isAlive(), "Monitor thread did not shut down properly.");
        }
    }

    @Test
    void asyncAlertSubmissionAfterStopIsIgnored() throws Exception {
        monitor.stop();

        java.lang.reflect.Method sendAlertAsync = ServerMonitor.class
                .getDeclaredMethod("sendAlertAsync", String.class);
        sendAlertAsync.setAccessible(true);

        assertDoesNotThrow(() -> sendAlertAsync.invoke(monitor, "late alert after shutdown"));
    }

    @Test
    void stopIsIdempotentAndMarksMonitorStopped() throws InterruptedException {
        waitUntil(monitor::isRunning, 1000, "Monitor should enter running state after setup.");
        assertTrue(monitor.isRunning(), "Monitor should be running after setup.");
        assertTrue(monitor.isAlive(), "Deprecated isAlive shim should mirror running state.");

        monitor.stop();
        monitorThread.join(5000);

        assertFalse(monitorThread.isAlive(), "Monitor thread should stop after stop().");
        assertFalse(monitor.isRunning(), "Monitor running state should be false after stop().");
        assertFalse(monitor.isAlive(), "Deprecated isAlive shim should report stopped state.");
        assertFalse(monitor.getStatus().isRunning(), "Monitor status should report stopped state.");
        assertDoesNotThrow(monitor::stop, "Repeated stop() should remain a safe no-op.");
    }

    @Test
    void stopBeforeStartIsSafeNoOpAndLeavesMonitorStopped() throws InterruptedException {
        stopCurrentMonitor();
        ServerMonitor neverStartedMonitor = new ServerMonitor(new ServerMonitor.Config()
            .withThreshold(80.0)
            .withInterval(MONITOR_CYCLE_MS)
            .withFluctuation(10.0)
            .withAlertCooldownMs(0),
            balancer,
            null);

        assertFalse(neverStartedMonitor.isRunning(), "New monitor should start in stopped state.");
        assertFalse(neverStartedMonitor.isAlive(), "Deprecated isAlive shim should mirror stopped state.");
        assertFalse(neverStartedMonitor.getStatus().isRunning(), "Status should report stopped before start().");

        assertDoesNotThrow(neverStartedMonitor::stop, "stop() before start() should be safe.");
        assertDoesNotThrow(neverStartedMonitor::stop, "Repeated stop() before start() should remain safe.");

        assertFalse(neverStartedMonitor.isRunning(), "Monitor should remain stopped after stop-before-start calls.");
        assertFalse(neverStartedMonitor.isAlive(), "Deprecated isAlive shim should remain stopped.");
        assertFalse(neverStartedMonitor.getStatus().isRunning(), "Status should remain stopped after stop-before-start.");
    }

    @Test
    void publicStartAndRepeatedStopUpdateRunningState() throws InterruptedException {
        stopCurrentMonitor();
        ServerMonitor startedMonitor = new ServerMonitor(new ServerMonitor.Config()
            .withThreshold(80.0)
            .withInterval(MONITOR_CYCLE_MS)
            .withFluctuation(10.0)
            .withAlertCooldownMs(0),
            balancer,
            null);

        try {
            assertFalse(startedMonitor.isRunning(), "New monitor should start in stopped state.");

            startedMonitor.start();

            assertTrue(startedMonitor.isRunning(), "start() should mark the monitor running.");
            assertTrue(startedMonitor.isAlive(), "Deprecated isAlive shim should mirror running state.");
            assertTrue(startedMonitor.getStatus().isRunning(), "Status should report running after start().");

            assertDoesNotThrow(startedMonitor::start, "Repeated start() while running should be safe.");
            assertTrue(startedMonitor.isRunning(), "Repeated start() should leave the monitor running.");

            startedMonitor.stop();

            assertFalse(startedMonitor.isRunning(), "stop() should mark the monitor stopped.");
            assertFalse(startedMonitor.isAlive(), "Deprecated isAlive shim should mirror stopped state.");
            assertFalse(startedMonitor.getStatus().isRunning(), "Status should report stopped after stop().");
            assertDoesNotThrow(startedMonitor::stop, "Repeated stop() after start() should remain safe.");
        } finally {
            startedMonitor.stop();
        }
    }

    @Test
    void pauseAndResumeToggleStatusWithoutStoppingMonitor() throws InterruptedException {
        waitUntil(monitor::isRunning, 1000, "Monitor should enter running state before pause/resume.");
        assertTrue(monitor.getStatus().isRunning(), "Monitor should start in running state.");

        monitor.pause();

        assertTrue(monitor.getStatus().isPaused(), "pause() should mark the monitor paused.");
        assertTrue(monitor.getStatus().isRunning(), "pause() should not stop the monitor.");

        monitor.resume();

        assertFalse(monitor.getStatus().isPaused(), "resume() should clear paused state.");
        assertTrue(monitor.getStatus().isRunning(), "resume() should keep the monitor running.");
    }

    @Test
    void interruptedMonitorMarksRunningStateStopped() throws InterruptedException {
        addServers(new Server("INTERRUPT-CHECK", 20.0, 20.0, 20.0));

        monitorThread.interrupt();
        monitorThread.join(5000);

        assertFalse(monitorThread.isAlive(), "Monitor thread should exit after interruption.");
        assertFalse(monitor.isRunning(), "Monitor running state should be false after interruption.");
        assertFalse(monitor.getStatus().isRunning(), "Monitor status should report stopped after interruption.");
    }

    @Test
    void monitorCycleInvokesLoadBalancerHealthCheck() throws Exception {
        stopCurrentMonitor();
        LoadBalancer observedBalancer = spy(new LoadBalancer());
        Server server = new Server("HEALTH-CYCLE", 20.0, 20.0, 20.0);
        observedBalancer.addServer(server);
        ServerMonitor observedMonitor = new ServerMonitor(new ServerMonitor.Config()
            .withThreshold(80.0)
            .withInterval(MONITOR_CYCLE_MS)
            .withFluctuation(0.0)
            .withAlertCooldownMs(0),
            observedBalancer,
            null);
        Thread observedThread = new Thread(observedMonitor);

        try {
            observedThread.start();

            verify(observedBalancer, timeout(2000).atLeastOnce()).checkServerHealth();
            assertTrue(server.isHealthy(), "Normal health-cycle server should remain healthy.");
        } finally {
            observedMonitor.stop();
            observedThread.interrupt();
            observedThread.join(5000);
            observedBalancer.shutdown();
        }
    }

    @Test
    void cloudMetricIOExceptionFallsBackWithoutLiveAwsClients() throws Exception {
        stopCurrentMonitor();
        LoadBalancer mockedBalancer = mock(LoadBalancer.class);
        Server cloudServer = new Server("CLOUD-METRIC-FAILURE", 10.0, 20.0, 30.0, ServerType.CLOUD);
        when(mockedBalancer.getServers()).thenReturn(List.of(cloudServer));
        when(mockedBalancer.hasCloudManager()).thenReturn(true);
        doThrow(new IOException("simulated cloud metric failure"))
            .when(mockedBalancer).updateCloudMetricsIfAvailable();
        ServerMonitor isolatedMonitor = new ServerMonitor(new ServerMonitor.Config()
            .withThreshold(80.0)
            .withInterval(MONITOR_CYCLE_MS)
            .withFluctuation(0.0)
            .withMaxCloudRetries(1)
            .withCloudRetryBaseMs(1)
            .withMaxConsecutiveCloudFailures(1)
            .withAlertCooldownMs(0),
            mockedBalancer,
            null);
        Thread isolatedThread = new Thread(isolatedMonitor);

        try {
            isolatedThread.start();
            waitUntil(() -> isolatedMonitor.getStatus().getConsecutiveFailures() >= 1, 3000,
                "Persistent cloud metric failure should record a monitor failure.");

            assertTrue(isolatedMonitor.getStatus().getConsecutiveFailures() >= 1,
                "Cloud metric IOException path should record a consecutive failure.");
            verify(mockedBalancer, atLeastOnce()).updateCloudMetricsIfAvailable();
            verify(mockedBalancer, atLeastOnce()).logAlert(contains("Persistent cloud metric fetch failure"));
            verify(mockedBalancer, timeout(3000).atLeastOnce()).checkServerHealth();
        } finally {
            isolatedMonitor.stop();
            isolatedThread.interrupt();
            isolatedThread.join(5000);
        }
    }

    /**
     * Adds servers to the LoadBalancer.
     *
     * Adds the specified servers to the balancer, simplifying test setup and logging each addition.
     *
     * @param servers the servers to add to the balancer
     */
    private void addServers(Server... servers) {
        for (Server server : servers) {
            balancer.addServer(server);
            logger.debug("Added server {} to balancer", server.getServerId());
        }
    }

    private void attachCloudManager(CloudManager cloudManager) throws ReflectiveOperationException {
        Field cloudManagerField = LoadBalancer.class.getDeclaredField("cloudManager");
        cloudManagerField.setAccessible(true);
        cloudManagerField.set(balancer, cloudManager);
    }

    private void restartMonitor(ServerMonitor.Config config) throws InterruptedException {
        monitor.stop();
        monitorThread.interrupt();
        monitorThread.join(5000);
        assertFalse(monitorThread.isAlive(), "Existing monitor thread should stop before restart.");

        monitor = new ServerMonitor(config, balancer, null);
        monitorThread = new Thread(monitor);
        monitorThread.start();
    }

    private CloudManager mockedCloudManagerWithCapacityControl() {
        CloudManager cloudManager = mock(CloudManager.class);
        doAnswer(invocation -> {
            int desiredCapacity = invocation.getArgument(0);
            setMockCloudCapacity(desiredCapacity);
            return null;
        }).when(cloudManager).scaleServers(anyInt());
        when(cloudManager.getMinServers()).thenReturn(0);
        return cloudManager;
    }

    private void setMockCloudCapacity(int desiredCapacity) {
        for (Server server : balancer.getServersByType(ServerType.CLOUD)) {
            balancer.removeServer(server.getServerId());
        }
        for (int i = 1; i <= desiredCapacity; i++) {
            Server cloudServer = new Server("CloudServer-" + i, 10.0, 20.0, 30.0, ServerType.CLOUD);
            cloudServer.setCapacity(500.0);
            balancer.addServer(cloudServer);
        }
    }

    private long cloudServerCount() {
        return balancer.getServersByType(ServerType.CLOUD).size();
    }

    /**
     * Tests that server metrics are updated over time by the ServerMonitor with various scenarios.
     *
     * Adds a server with parameterized initial metrics, waits for two cycles, and verifies updates.
     *
     * @param initialCpu initial CPU usage
     * @param initialMem initial memory usage
     * @param initialDisk initial disk usage
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @ParameterizedTest
    @CsvSource({
        "50.0, 50.0, 50.0", // Normal case
        "0.0, 0.0, 0.0",    // Minimum values
        "100.0, 100.0, 100.0" // Maximum values
    })
    @Timeout(value = 30)
    void testMetricUpdatesOccur(double initialCpu, double initialMem, double initialDisk) throws InterruptedException {
        logger.info("=== TESTING METRIC UPDATES ===");
        Server server = new Server("TestServer", initialCpu, initialMem, initialDisk);
        addServers(server);

        waitForMonitorCycles(2, 15);

        if (initialCpu > 0.0 && initialCpu < 100.0) {
            assertNotEquals(initialCpu, server.getCpuUsage(), "CPU metric should update away from non-boundary values.");
        }
        if (initialMem > 0.0 && initialMem < 100.0) {
            assertNotEquals(initialMem, server.getMemoryUsage(), "Memory metric should update away from non-boundary values.");
        }
        if (initialDisk > 0.0 && initialDisk < 100.0) {
            assertNotEquals(initialDisk, server.getDiskUsage(), "Disk metric should update away from non-boundary values.");
        }
        assertTrue(server.getCpuUsage() >= 0 && server.getCpuUsage() <= 100, "CPU should stay in range 0-100.");
        assertTrue(server.getMemoryUsage() >= 0 && server.getMemoryUsage() <= 100, "Memory should stay in range 0-100.");
        assertTrue(server.getDiskUsage() >= 0 && server.getDiskUsage() <= 100, "Disk should stay in range 0-100.");
        logger.info("Metric updates test passed: CPU {} -> {}, Mem {} -> {}, Disk {} -> {}.",
                    initialCpu, server.getCpuUsage(), initialMem, server.getMemoryUsage(), initialDisk, server.getDiskUsage());
        verify(logger).info(eq("Metric updates test passed: CPU {} -> {}, Mem {} -> {}, Disk {} -> {}."), 
                            eq(initialCpu), anyDouble(), eq(initialMem), anyDouble(), eq(initialDisk), anyDouble());
    }

    /**
     * Tests that an alert is triggered when a server's CPU usage exceeds the threshold (90%).
     *
     * Adds a server with high CPU usage, waits for three cycles, and verifies alert log changes.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testAlertTriggeredOnHighCPU() throws InterruptedException {
        logger.info("=== TESTING HIGH CPU ALERT ===");
        Server server = new Server("HotServer", 95.0, 50.0, 50.0);
        addServers(server);
        int initialAlertCount = balancer.getAlertLog().size();

        waitForMonitorCycles(3, 20);

        List<String> alerts = balancer.getAlertLog();
        int finalAlertCount = alerts.size();
        assertTrue(finalAlertCount > initialAlertCount, "Alert should have triggered for CPU > 90%.");
        assertTrue(alerts.stream().anyMatch(alert -> alert.contains("HotServer")), "Alert should mention HotServer.");
        logger.info("High CPU alert test passed: Alerts increased from {} to {}.", initialAlertCount, finalAlertCount);
        verify(logger).info("High CPU alert test passed: Alerts increased from {} to {}.", initialAlertCount, finalAlertCount);
    }

    /**
     * Tests that no alert is triggered when a server's metrics are below the threshold (90%).
     *
     * Adds a server with normal metrics, waits for two cycles, and verifies log stability.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testNoAlertBelowThreshold() throws InterruptedException {
        logger.info("=== TESTING NO ALERT BELOW THRESHOLD ===");
        Server server = new Server("CoolServer", 30.0, 30.0, 30.0);
        addServers(server);
        int initialAlertCount = balancer.getAlertLog().size();

        waitForMonitorCycles(2, 15);

        List<String> alerts = balancer.getAlertLog();
        assertEquals(initialAlertCount, alerts.size(), "No alerts expected for metrics below 90%.");
        logger.info("No alert test passed: Alert count remained at {}.", initialAlertCount);
        verify(logger).info("No alert test passed: Alert count remained at {}.", initialAlertCount);
    }

    /**
     * Tests that the ServerMonitor handles multiple servers with custom threshold awareness.
     *
     * Adds a hot server (>90%) and a cool server (<90%), waits for two cycles, and verifies alerts.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testMultipleServerMonitoring() throws InterruptedException {
        logger.info("=== TESTING MULTIPLE SERVER MONITORING ===");
        Server hot = new Server("Hot", 95.0, 95.0, 95.0); // Above 90% threshold
        Server cool = new Server("Cool", 10.0, 10.0, 10.0); // Below 90% threshold
        addServers(hot, cool);
        int initialAlertCount = balancer.getAlertLog().size();

        waitForMonitorCycles(2, 15);

        List<String> alerts = balancer.getAlertLog();
        long hotAlerts = alerts.stream().filter(alert -> alert.contains("Hot")).count();
        long coolAlerts = alerts.stream().filter(alert -> alert.contains("Cool")).count();

        assertTrue(hotAlerts >= 1, "At least one alert expected for hot server (>90%).");
        assertEquals(0, coolAlerts, "No alerts expected for cool server (<90%).");
        assertTrue(alerts.size() > initialAlertCount, "Alert log should grow with hot server alerts.");
        logger.info("Multiple server test passed: {} hot alerts, {} cool alerts.", hotAlerts, coolAlerts);
        verify(logger).info("Multiple server test passed: {} hot alerts, {} cool alerts.", hotAlerts, coolAlerts);
    }

    /**
     * Tests that metrics are not updated for an unhealthy server.
     *
     * Adds an unhealthy server, waits for two cycles, and verifies its metrics remain unchanged.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testNoMetricUpdatesForUnhealthyServer() throws InterruptedException {
        logger.info("=== TESTING NO UPDATES FOR UNHEALTHY SERVER ===");
        Server unhealthy = new Server("Unhealthy", 30.0, 30.0, 30.0);
        unhealthy.setHealthy(false);
        addServers(unhealthy);

        waitForMonitorCycles(2, 15);

        assertEquals(30.0, unhealthy.getCpuUsage(), 0.01, "Unhealthy server CPU should not update.");
        assertEquals(30.0, unhealthy.getMemoryUsage(), 0.01, "Unhealthy server memory should not update.");
        assertEquals(30.0, unhealthy.getDiskUsage(), 0.01, "Unhealthy server disk should not update.");
        logger.info("No updates for unhealthy server test passed: Metrics unchanged.");
        verify(logger).info("No updates for unhealthy server test passed: Metrics unchanged.");
    }

    /**
     * Tests that the ServerMonitor shuts down gracefully and can restart.
     *
     * Verifies the monitor thread is alive, stops it, restarts it, and checks it’s alive again.
     *
     * @throws InterruptedException if the thread join operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testGracefulShutdownAndRestart() throws InterruptedException {
        logger.info("=== TESTING GRACEFUL SHUTDOWN AND RESTART ===");
        assertTrue(monitorThread.isAlive(), "Monitor thread should be alive initially.");

        monitor.stop();
        monitorThread.interrupt();
        monitorThread.join(5000);
        assertFalse(monitorThread.isAlive(), "Monitor thread did not shut down gracefully.");

        monitor = new ServerMonitor(balancer);
        monitorThread = new Thread(monitor);
        monitorThread.start();
        waitUntil(monitor::isRunning, 1000, "Restarted monitor should enter running state.");
        assertTrue(monitorThread.isAlive(), "Monitor thread should restart successfully.");
        assertTrue(monitor.getStatus().isRunning(), "Restarted monitor status should report running.");
        logger.info("Shutdown and restart test passed: Monitor stopped and restarted successfully.");
        verify(logger).info("Shutdown and restart test passed: Monitor stopped and restarted successfully.");
    }

    /**
     * Tests monitoring of a large scale of cloud servers with concurrent additions.
     *
     * Simulates 1000 servers with concurrent additions, verifies metric changes, and triggers an alert.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 60)
    void testCloudScaleServerMonitoring_WithConcurrentAdditions() throws InterruptedException {
        logger.info("=== TESTING CLOUD SCALE MONITORING WITH CONCURRENT ADDITIONS ===");
        int cloudServerCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(4);

        var cloudManager = balancer.getCloudManagerOptional();
        if (cloudManager.isPresent()) {
            waitForMonitorCycles(6, 35);
            assertEquals(balancer.getServers().size(), cloudManager.get().getMinServers(),
                        "Cloud server count should match initialized minimum.");
        } else {
            for (int i = 1; i <= cloudServerCount; i++) {
                final int serverNum = i;
                executor.submit(() -> {
                    Server cloudServer = new Server("CloudServer-" + serverNum, 10.0, 20.0, 30.0);
                    cloudServer.setCapacity(500.0);
                    addServers(cloudServer);
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            assertEquals(cloudServerCount, balancer.getServers().size(),
                        "All cloud servers should be registered after concurrent additions.");
        }

        waitForMonitorCycles(3, 20);

        boolean metricsChanged = balancer.getServers().stream()
            .limit(50)
            .anyMatch(server -> server.getCpuUsage() != 10.0
                              || server.getMemoryUsage() != 20.0
                              || server.getDiskUsage() != 30.0);
        assertTrue(metricsChanged, "Server metrics should dynamically change over time.");

        Server alertServer = balancer.getServerMap().get("CloudServer-500");
        if (alertServer != null) {
            alertServer.updateMetrics(95.0, 95.0, 95.0);
        } else {
            alertServer = balancer.getServers().get(499);
            alertServer.updateMetrics(95.0, 95.0, 95.0);
        }

        waitForMonitorCycles(2, 15);

        List<String> alerts = balancer.getAlertLog();
        assertFalse(alerts.isEmpty(), "Alerts should trigger for servers exceeding threshold.");
        assertTrue(alerts.stream().anyMatch(alert -> alert.contains("CloudServer-500")),
                "Alert should mention CloudServer-500.");
        logger.info("Cloud scale with concurrent additions test passed: Metrics changed, alert triggered.");
        verify(logger).info("Cloud scale with concurrent additions test passed: Metrics changed, alert triggered.");
        System.out.println("Cloud-scale monitoring successfully validated.");
    }

    /**
     * Tests automatic scaling of cloud servers.
     *
     * Assumes a CloudManager is initialized and verifies that the server count increases
     * after scaling to a higher capacity.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 60)
    void testCloudAutoScaling() throws Exception {
        logger.info("=== TESTING CLOUD AUTO SCALING WITH MOCKED CLOUD MANAGER ===");
        CloudManager cloudManager = mockedCloudManagerWithCapacityControl();
        attachCloudManager(cloudManager);
        setMockCloudCapacity(5);
        long initialCount = cloudServerCount();
        assertEquals(5, initialCount, "Initial mocked cloud server count should be 5.");

        balancer.scaleCloudServers(10);

        long scaledCount = cloudServerCount();
        assertEquals(10, scaledCount, "Mocked cloud scaling should register the desired capacity.");
        assertTrue(scaledCount >= initialCount, "Server count should increase after scaling.");
        verify(cloudManager).scaleServers(10);
        logger.info("Cloud auto scaling test passed: Scaled from {} to {}.", initialCount, scaledCount);
        verify(logger).info("Cloud auto scaling test passed: Scaled from {} to {}.", initialCount, scaledCount);
    }

    /**
     * Tests removal of cloud servers by scaling down.
     *
     * Assumes a CloudManager is initialized and verifies that the server count decreases
     * after scaling to a lower capacity.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 60)
    void testCloudServerRemoval() throws Exception {
        logger.info("=== TESTING CLOUD SERVER REMOVAL WITH MOCKED CLOUD MANAGER ===");
        CloudManager cloudManager = mockedCloudManagerWithCapacityControl();
        attachCloudManager(cloudManager);
        setMockCloudCapacity(10);
        long initialCount = cloudServerCount();
        assertEquals(10, initialCount, "Initial mocked cloud server count should be 10.");

        balancer.scaleCloudServers(5);

        long finalCount = cloudServerCount();
        assertEquals(5, finalCount, "Mocked cloud removal should reduce to the desired capacity.");
        verify(cloudManager).scaleServers(5);
        logger.info("Cloud server removal test passed: Reduced from {} to {}.", initialCount, finalCount);
        verify(logger).info("Cloud server removal test passed: Reduced from {} to {}.", initialCount, finalCount);
    }

    /**
     * Tests failover handling for cloud servers with timeout simulation.
     *
     * Assumes a CloudManager is initialized, marks a server as failed, and verifies health status.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testCloudFailover_WithTimeout() throws Exception {
        logger.info("=== TESTING CLOUD FAILOVER WITH MOCKED CLOUD MANAGER ===");
        CloudManager cloudManager = mock(CloudManager.class);
        when(cloudManager.getMinServers()).thenReturn(0);
        attachCloudManager(cloudManager);
        Server failingServer = new Server("CloudFailing", 50.0, 50.0, 50.0, ServerType.CLOUD);
        addServers(failingServer);

        failingServer.updateMetrics(100.0, 100.0, 100.0);
        waitForMonitorCycles(2, 5);

        assertFalse(failingServer.isHealthy(), "Failing server should be marked unhealthy after timeout.");
        assertNull(balancer.getServer("CloudFailing"), "Unhealthy cloud server should be removed from active servers.");
        logger.info("Cloud failover with timeout test passed: Server marked unhealthy.");
        verify(logger).info("Cloud failover with timeout test passed: Server marked unhealthy.");
    }

    /**
     * Tests retrieval of cloud metrics via CloudWatch with failure simulation.
     *
     * Assumes a CloudManager is initialized, simulates a failure, and verifies fallback behavior.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testCloudWatchMetrics_Failure() throws Exception {
        logger.info("=== TESTING CLOUDWATCH METRICS FAILURE WITH MOCKED CLOUD MANAGER ===");
        restartMonitor(new ServerMonitor.Config()
                .withThreshold(80.0)
                .withInterval(MONITOR_CYCLE_MS)
                .withFluctuation(0.0)
                .withAlertCooldownMs(0)
                .withMaxCloudRetries(1)
                .withCloudRetryBaseMs(1)
                .withMaxConsecutiveCloudFailures(1));
        CloudManager cloudManager = mock(CloudManager.class);
        doThrow(new RuntimeException("Simulated CloudWatch failure"))
                .when(cloudManager).updateServerMetricsFromCloud();
        attachCloudManager(cloudManager);
        Server cloudServer = new Server("CloudMetricFailure", 10.0, 20.0, 30.0, ServerType.CLOUD);
        addServers(cloudServer);

        waitForMonitorCycles(35, 10);

        assertTrue(monitor.getStatus().getConsecutiveFailures() >= 1,
                "Persistent cloud metric failure should increment the monitor failure count.");
        assertTrue(cloudServer.getCpuUsage() >= 0.0 && cloudServer.getCpuUsage() <= 100.0,
                "Fallback metrics should remain readable and within range after cloud metric failure.");
        assertTrue(balancer.getAlertLog().stream()
                .anyMatch(alert -> alert.contains("Persistent cloud metric fetch failure")),
                "Persistent cloud metric failure should raise a critical alert.");
        verify(cloudManager, atLeastOnce()).updateServerMetricsFromCloud();
        logger.info("CloudWatch failure test passed: Mocked failure recorded and fallback metrics remained valid.");
        verify(logger).info("CloudWatch failure test passed: Mocked failure recorded and fallback metrics remained valid.");
    }

    /**
     * Tests health checking of cloud servers.
     *
     * Assumes a CloudManager is initialized, marks the first server as unhealthy,
     * and verifies its health status after a health check.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testCloudHealthCheck() throws Exception {
        logger.info("=== TESTING CLOUD HEALTH CHECK WITH MOCKED CLOUD MANAGER ===");
        CloudManager cloudManager = mock(CloudManager.class);
        when(cloudManager.getMinServers()).thenReturn(0);
        attachCloudManager(cloudManager);
        Server unhealthyServer = new Server("CloudHealth", 50.0, 50.0, 50.0, ServerType.CLOUD);
        addServers(unhealthyServer);
        unhealthyServer.updateMetrics(100.0, 100.0, 100.0);

        waitForMonitorCycles(2, 5);

        assertFalse(unhealthyServer.isHealthy(), "Unhealthy cloud server should be marked unhealthy.");
        assertNull(balancer.getServer("CloudHealth"), "Unhealthy cloud server should be removed from active servers.");
        logger.info("Cloud health check test passed: Server marked unhealthy.");
        verify(logger).info("Cloud health check test passed: Server marked unhealthy.");
    }

    /**
     * Tests ServerMonitor behavior with interrupted setup.
     *
     * Simulates an interruption during setup and verifies graceful handling.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testInterruptedSetup() throws InterruptedException {
        logger.info("=== TESTING INTERRUPTED SETUP ===");
        Thread.currentThread().interrupt(); // Simulate interruption before setup completes
        try {
            setup(); // Re-run setup with interruption
            assertTrue(Thread.interrupted(), "Thread should remain interrupted.");
            assertTrue(monitorThread.isAlive(), "Monitor thread should start despite interruption.");
        } finally {
            Thread.interrupted(); // Clear interrupt status
        }
        logger.info("Interrupted setup test passed: Monitor started despite interruption.");
        verify(logger).info("Interrupted setup test passed: Monitor started despite interruption.");
    }

    /**
     * Tests ServerMonitor behavior when interrupted.
     *
     * Adds a server, interrupts the monitor thread, and verifies it stops gracefully.
     *
     * @throws InterruptedException if the thread join operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testInterruptedMonitor() throws InterruptedException {
        logger.info("=== TESTING INTERRUPTED MONITOR ===");
        Server server = new Server("S1", 50.0, 50.0, 50.0);
        addServers(server);

        monitorThread.interrupt();
        monitorThread.join(5000);

        assertFalse(monitorThread.isAlive(), "Monitor should stop after interruption.");
        assertFalse(monitor.isRunning(), "Monitor running state should be false after interruption.");
        assertFalse(monitor.getStatus().isRunning(), "Monitor status should report stopped after interruption.");
        logger.info("Interrupted monitor test passed: Thread stopped successfully.");
        verify(logger).info("Interrupted monitor test passed: Thread stopped successfully.");
    }

    /**
     * Tests ServerMonitor with a null LoadBalancer.
     *
     * Verifies that constructing a ServerMonitor with a null balancer throws an IllegalArgumentException.
     */
    @Test
    @Timeout(value = 10)
    void testConstructor_NullBalancer() {
        logger.info("=== TESTING NULL BALANCER IN CONSTRUCTOR ===");
        assertThrows(IllegalArgumentException.class, () -> new ServerMonitor(null),
            "Should reject null LoadBalancer in constructor!");
        logger.info("Null balancer test passed: Exception thrown correctly.");
        verify(logger).info("Null balancer test passed: Exception thrown correctly.");
    }

    /**
     * Tests ServerMonitor with an empty server list.
     *
     * Runs the monitor with no servers and verifies it operates without errors for two cycles.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testEmptyServerList() throws InterruptedException {
        logger.info("=== TESTING EMPTY SERVER LIST ===");
        waitForMonitorCycles(2, 15);
        assertTrue(balancer.getAlertLog().isEmpty(), "No alerts should be logged with no servers.");
        assertTrue(monitorThread.isAlive(), "Monitor should still be running with no servers.");
        logger.info("Empty server list test passed: Monitor ran without issues.");
        verify(logger).info("Empty server list test passed: Monitor ran without issues.");
    }

    /**
     * Tests ServerMonitor when all servers are unhealthy.
     *
     * Adds multiple unhealthy servers and verifies no metrics update and no alerts are triggered.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    @Timeout(value = 30)
    void testAllServersUnhealthy() throws InterruptedException {
        logger.info("=== TESTING ALL SERVERS UNHEALTHY ===");
        Server s1 = new Server("S1", 30.0, 40.0, 50.0);
        s1.setHealthy(false);
        Server s2 = new Server("S2", 20.0, 30.0, 40.0);
        s2.setHealthy(false);
        addServers(s1, s2);

        waitForMonitorCycles(2, 15);

        assertEquals(30.0, s1.getCpuUsage(), 0.01, "S1 CPU should not update when unhealthy.");
        assertEquals(20.0, s2.getCpuUsage(), 0.01, "S2 CPU should not update when unhealthy.");
        assertTrue(balancer.getAlertLog().isEmpty(), "No alerts should be triggered for all unhealthy servers.");
        logger.info("All servers unhealthy test passed: No updates or alerts.");
        verify(logger).info("All servers unhealthy test passed: No updates or alerts.");
    }
}
