package test.core;

import core.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.List;
import java.util.concurrent.TimeUnit;

class ServerMonitorTest {
    private LoadBalancer balancer;
    private ServerMonitor monitor;
    private Thread monitorThread;

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

    @Test
    void testMetricUpdatesOccur() throws InterruptedException {
        Server server = new Server("TestServer", 50.0, 50.0, 50.0);
        balancer.addServer(server);

        TimeUnit.SECONDS.sleep(6);

        assertNotEquals(50.0, server.getCpuUsage(), "CPU metric should update.");
    }

    @Test
    void testAlertTriggeredOnHighCPU() throws InterruptedException {
        Server server = new Server("HotServer", 95.0, 50.0, 50.0);
        balancer.addServer(server);

        TimeUnit.SECONDS.sleep(11);

        List<String> alerts = balancer.getAlertLog();
        assertFalse(alerts.isEmpty(), "Alert should have triggered for high CPU.");
        assertTrue(alerts.stream().anyMatch(alert -> alert.contains("HotServer")), "Alert should mention HotServer.");
    }

    @Test
    void testNoAlertBelowThreshold() throws InterruptedException {
        Server server = new Server("CoolServer", 30.0, 30.0, 30.0);
        balancer.addServer(server);

        TimeUnit.SECONDS.sleep(6);

        assertTrue(balancer.getAlertLog().isEmpty(), "No alerts expected for normal metrics.");
    }

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

    @Test
    void testNoMetricUpdatesForUnhealthyServer() throws InterruptedException {
        Server unhealthy = new Server("Unhealthy", 30.0, 30.0, 30.0);
        unhealthy.setHealthy(false);
        balancer.addServer(unhealthy);

        TimeUnit.SECONDS.sleep(6);

        assertEquals(30.0, unhealthy.getCpuUsage(), "Unhealthy server metrics should not update.");
    }

    @Test
    void testGracefulShutdown() throws InterruptedException {
        assertTrue(monitorThread.isAlive(), "Monitor thread should be alive initially.");

        monitor.stop();
        monitorThread.interrupt();
        monitorThread.join(5000);

        assertFalse(monitorThread.isAlive(), "Monitor thread did not shut down gracefully.");
    }

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

    @Test
    void testCloudWatchMetrics() throws InterruptedException {
        assumeTrue(balancer.getCloudManager() != null, "Cloud integration not initialized.");

        String instanceId = balancer.getServers().get(0).getServerId();
        double cpuUsage = balancer.getCloudManager().getCloudMetric(instanceId, "CPUUtilization");

        assertTrue(cpuUsage >= 0, "CPU metric should be retrieved.");
    }

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
