package test.core;

import core.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test class for the ServerMonitor, verifying its functionality in monitoring server metrics,
 * triggering alerts, and handling cloud integration scenarios.
 *
 * This class uses JUnit 5 to test the ServerMonitor's behavior under various conditions,
 * including metric updates, alert generation, and shutdown procedures. It also includes
 * cloud-related tests that assume a CloudManager is initialized.
 *
 */
class ServerMonitorTest {
    /** LoadBalancer instance for testing server management. */
    private LoadBalancer balancer;

    /** ServerMonitor instance to be tested. */
    private ServerMonitor monitor;

    /** Thread running the ServerMonitor for testing. */
    private Thread monitorThread;

    /**
     * Sets up the test environment before each test method.
     *
     * Initializes a new LoadBalancer, ServerMonitor, and starts the monitor thread.
     * Skips cloud initialization if using test credentials.
     */
    @BeforeEach
    void setup() {
        balancer = new LoadBalancer();
        monitor = new ServerMonitor(balancer);
        monitorThread = new Thread(monitor);
        monitorThread.start();

        String accessKey = "test_access_key";
        String secretKey = "test_secret_key";
        String region = "us-east-1";
        if (!accessKey.equals("test_access_key") && !secretKey.equals("test_secret_key")) {
            balancer.initializeCloud(accessKey, secretKey, region, 5, 10);
        }
    }

    /**
     * Tears down the test environment after each test method.
     *
     * Stops the monitor, interrupts and joins the monitor thread, and shuts down the balancer.
     * Asserts that the thread is no longer alive after shutdown.
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
     * Tests that server metrics are updated over time by the ServerMonitor.
     *
     * Adds a server with initial metrics and waits for updates, asserting that the CPU usage changes.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testMetricUpdatesOccur() throws InterruptedException {
        Server server = new Server("TestServer", 50.0, 50.0, 50.0);
        balancer.addServer(server);

        TimeUnit.SECONDS.sleep(6);

        assertNotEquals(50.0, server.getCpuUsage(), "CPU metric should update.");
    }

    /**
     * Tests that an alert is triggered when a server's CPU usage exceeds the threshold.
     *
     * Adds a server with high CPU usage, waits for the monitor to detect it, and verifies an alert is logged.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testAlertTriggeredOnHighCPU() throws InterruptedException {
        Server server = new Server("HotServer", 95.0, 50.0, 50.0);
        balancer.addServer(server);

        TimeUnit.SECONDS.sleep(11);

        List<String> alerts = balancer.getAlertLog();
        assertFalse(alerts.isEmpty(), "Alert should have triggered for high CPU.");
        assertTrue(alerts.stream().anyMatch(alert -> alert.contains("HotServer")), "Alert should mention HotServer.");
    }

    /**
     * Tests that no alert is triggered when a server's metrics are below the threshold.
     *
     * Adds a server with normal metrics, waits for the monitor, and verifies no alerts are logged.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testNoAlertBelowThreshold() throws InterruptedException {
        Server server = new Server("CoolServer", 30.0, 30.0, 30.0);
        balancer.addServer(server);

        TimeUnit.SECONDS.sleep(6);

        assertTrue(balancer.getAlertLog().isEmpty(), "No alerts expected for normal metrics.");
    }

    /**
     * Tests that the ServerMonitor handles multiple servers correctly.
     *
     * Adds a hot server (exceeding threshold) and a cool server (below threshold),
     * waits for monitoring, and verifies alerts for the hot server only.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testMultipleServerMonitoring() throws InterruptedException {
        Server hot = new Server("Hot", 95.0, 95.0, 95.0);
        Server cool = new Server("Cool", 10.0, 10.0, 10.0);
        balancer.addServer(hot);
        balancer.addServer(cool);

        TimeUnit.SECONDS.sleep(6);

        List<String> alerts = balancer.getAlertLog();
        long hotAlerts = alerts.stream().filter(alert -> alert.contains("Hot")).count();
        long coolAlerts = alerts.stream().filter(alert -> alert.contains("Cool")).count();

        assertTrue(hotAlerts >= 1, "At least one alert expected for hot server.");
        assertEquals(0, coolAlerts, "No alerts expected for cool server.");
    }

    /**
     * Tests that metrics are not updated for an unhealthy server.
     *
     * Adds an unhealthy server, waits for the monitor, and verifies its metrics remain unchanged.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testNoMetricUpdatesForUnhealthyServer() throws InterruptedException {
        Server unhealthy = new Server("Unhealthy", 30.0, 30.0, 30.0);
        unhealthy.setHealthy(false);
        balancer.addServer(unhealthy);

        TimeUnit.SECONDS.sleep(6);

        assertEquals(30.0, unhealthy.getCpuUsage(), "Unhealthy server metrics should not update.");
    }

    /**
     * Tests that the ServerMonitor shuts down gracefully.
     *
     * Verifies the monitor thread is alive initially, stops it, and checks it is no longer alive.
     *
     * @throws InterruptedException if the thread join operation is interrupted
     */
    @Test
    void testGracefulShutdown() throws InterruptedException {
        assertTrue(monitorThread.isAlive(), "Monitor thread should be alive initially.");

        monitor.stop();
        monitorThread.interrupt();
        monitorThread.join(5000);

        assertFalse(monitorThread.isAlive(), "Monitor thread did not shut down gracefully.");
    }

    /**
     * Tests monitoring of a large scale of cloud servers.
     *
     * Handles cloud server monitoring if a CloudManager is initialized, otherwise simulates
     * 1000 cloud servers, verifies metric changes, and triggers an alert.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testCloudScaleServerMonitoring() throws InterruptedException {
        int cloudServerCount = 1000;
        if (balancer.getCloudManager() != null) {
            TimeUnit.SECONDS.sleep(30);
            assertEquals(balancer.getServers().size(), balancer.getCloudManager().getMinServers(),
                        "All cloud servers should match initialized count.");
        } else {
            for (int i = 1; i <= cloudServerCount; i++) {
                Server cloudServer = new Server("CloudServer-" + i, 10.0, 20.0, 30.0);
                cloudServer.setCapacity(500.0);
                balancer.addServer(cloudServer);
            }
            assertEquals(cloudServerCount, balancer.getServers().size(),
                        "All cloud servers should be registered.");
        }

        TimeUnit.SECONDS.sleep(12);

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

        TimeUnit.SECONDS.sleep(6);

        List<String> alerts = balancer.getAlertLog();
        assertFalse(alerts.isEmpty(), "Alerts should trigger for servers exceeding threshold.");
        assertTrue(alerts.stream().anyMatch(alert -> alert.contains("CloudServer-500")),
                "Alert should specifically mention CloudServer-500.");

        System.out.println("Cloud-scale monitoring successfully validated.");
    }

    /**
     * Tests automatic scaling of cloud servers.
     *
     * Assumes a CloudManager is initialized and verifies that the server count
     * increases after scaling to a higher capacity.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testCloudAutoScaling() throws InterruptedException {
        assumeTrue(balancer.getCloudManager() != null, "Cloud integration not initialized.");

        TimeUnit.SECONDS.sleep(30);
        int initialCount = balancer.getServers().size();
        assertTrue(initialCount >= 5, "Initial server count should be at least 5.");

        balancer.scaleCloudServers(10);
        TimeUnit.SECONDS.sleep(30);

        int scaledCount = balancer.getServers().size();
        assertTrue(scaledCount >= 10, "Scaled server count should be at least 10.");
        assertTrue(scaledCount >= initialCount, "Server count should increase after scaling.");

        System.out.println("Cloud auto-scaling successfully validated.");
    }

    /**
     * Tests removal of cloud servers by scaling down.
     *
     * Assumes a CloudManager is initialized and verifies that the server count
     * decreases after scaling to a lower capacity.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testCloudServerRemoval() throws InterruptedException {
        assumeTrue(balancer.getCloudManager() != null, "Cloud integration not initialized.");

        balancer.scaleCloudServers(10);
        TimeUnit.SECONDS.sleep(30);
    
        assertTrue(balancer.getServers().size() >= 10, "Scaled server count should be at least 10.");

        balancer.scaleCloudServers(5);
        TimeUnit.SECONDS.sleep(30);

        assertTrue(balancer.getServers().size() <= 5, "Scaled server count should be at most 5.");
    }

    /**
     * Tests failover handling for cloud servers.
     *
     * Assumes a CloudManager is initialized, marks the first server as failed,
     * and verifies its removal after failover.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testCloudFailover() throws InterruptedException {
        assumeTrue(balancer.getCloudManager() != null, "Cloud integration not initialized.");

        balancer.scaleCloudServers(5);
        TimeUnit.SECONDS.sleep(30);

        Server failingServer = balancer.getServers().get(0);
        failingServer.updateMetrics(100.0, 100.0, 100.0);

        TimeUnit.SECONDS.sleep(10);

        assertFalse(failingServer.isHealthy(), "Failing server should be marked unhealthy.");
        assertFalse(balancer.getServers().contains(failingServer), "Failing server should be removed.");
    }

    /**
     * Tests retrieval of cloud metrics via CloudWatch.
     *
     * Assumes a CloudManager is initialized and verifies that a valid CPU metric
     * is retrieved for the first server.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testCloudWatchMetrics() throws InterruptedException {
        assumeTrue(balancer.getCloudManager() != null, "Cloud integration not initialized.");

        String instanceId = balancer.getServers().get(0).getServerId();
        double cpuUsage = balancer.getCloudManager().getCloudMetric(instanceId, "CPUUtilization");

        assertTrue(cpuUsage >= 0, "CPU metric should be retrieved.");
    }

    /**
     * Tests health checking of cloud servers.
     *
     * Assumes a CloudManager is initialized, marks the first server as unhealthy,
     * and verifies its removal after a health check.
     *
     * @throws InterruptedException if the sleep operation is interrupted
     */
    @Test
    void testCloudHealthCheck() throws InterruptedException {
        assumeTrue(balancer.getCloudManager() != null, "Cloud integration not initialized.");

        balancer.scaleCloudServers(5);
        TimeUnit.SECONDS.sleep(30);

        Server unhealthyServer = balancer.getServers().get(0);
        unhealthyServer.updateMetrics(99.0, 99.0, 99.0);

        TimeUnit.SECONDS.sleep(10);

        assertFalse(unhealthyServer.isHealthy(), "Unhealthy cloud server should be removed.");
    }
}
