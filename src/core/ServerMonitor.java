package core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Monitors the health and performance of servers in the {@link LoadBalancer}.
 * 
 * This class runs as a background thread, continuously updating server metrics 
 * and checking for threshold violations. If a server's CPU, memory, or disk 
 * usage exceeds {@code 90%}, an alert is triggered and logged.
 * 
 * <p><b>Features:</b></p>
 * - Runs independently in a loop, monitoring servers every 5 seconds.
 * - Fetches cloud-based metrics if available (e.g., AWS CloudWatch).
 * - Applies random metric fluctuations for on-premise servers.
 * - Triggers alerts if server usage surpasses the defined threshold.
 * - Logs alerts and updates server health status.
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * LoadBalancer balancer = new LoadBalancer();
 * ServerMonitor monitor = new ServerMonitor(balancer);
 * Thread monitorThread = new Thread(monitor);
 * monitorThread.start();
 * }</pre>
 * 
 * <p><b>Logging:</b></p>
 * - Server monitor startup: `"=== SERVER MONITOR ONLINE—WATCHIN’ THE CREW ==="`
 * - Alert if a server exceeds threshold: `"ALERT: Server X is HOT!"`
 * - Server monitor shutdown: `"=== SERVER MONITOR OUT—PEACE ==="`
 *
 * <p><b>Threading:</b></p>
 * - This class implements {@link Runnable} to run as a separate monitoring thread.
 * - Uses {@code synchronized (balancer)} to ensure thread safety when modifying server states.
 * - Sleeps for 5 seconds between monitoring cycles.
 *
 * <p><b>UML Diagram:</b></p>
 * <p><img src="../servermonitor.png" alt="ServerMonitor UML Diagram"></p>
 *
 * @author Richmond Dhaenens
 * @version 18.0
 */
public class ServerMonitor implements Runnable {
    /**
     * Logger instance for tracking monitoring events.
     * Logs startup, shutdown, alerts, and system errors.
     */
    private static final Logger logger = LogManager.getLogger(ServerMonitor.class);

    /**
     * Reference to the LoadBalancer instance.
     * This allows the monitor to access and update server metrics.
     */
    private final LoadBalancer balancer;

    /**
     * Flag to control the monitoring loop execution.
     * Set to {@code false} to stop the monitoring thread.
     */
    private volatile boolean running = true;

    /**
     * Random instance for generating metric fluctuations.
     * Used for simulating real-world server metric changes.
     */
    private final Random random = new Random();

    /**
     * Alert threshold for triggering warnings.
     * If CPU, memory, or disk usage exceeds this value, an alert is logged.
     */
    private static final double ALERT_THRESHOLD = 90.0;

    /**
     * Constructs a {@code ServerMonitor} instance tied to a {@link LoadBalancer}.
     * 
     * @param balancer The load balancer whose servers will be monitored.
     */
    public ServerMonitor(LoadBalancer balancer) {
        this.balancer = balancer;
    }

    /**
     * Stops the monitoring loop, allowing the thread to terminate gracefully.
     * 
     * <p>This method sets the {@code running} flag to {@code false}, signaling 
     * the monitoring loop to exit on the next iteration.</p>
     */
    public void stop() {
        running = false;
    }

    /**
     * Executes the server monitoring loop.
     * 
     * <p><b>Process:</b></p>
     * - Iterates through all servers managed by {@link LoadBalancer}.
     * - If cloud integration is available, updates metrics from AWS CloudWatch.
     * - Otherwise, applies random metric fluctuations (±10%).
     * - Triggers alerts if CPU, memory, or disk usage exceeds {@code 90%}.
     * - Calls {@code balancer.checkServerHealth()} to update server health statuses.
     * - Sleeps for 5 seconds between iterations.
     * 
     * <p><b>Error Handling:</b></p>
     * - If interrupted, the thread exits gracefully.
     * - Uses {@code synchronized (balancer)} to ensure safe multi-threaded access.
     */
    @Override
    public void run() {
        logger.info("=== SERVER MONITOR ONLINE—WATCHIN’ THE CREW ===");
        while (running) {
            synchronized (balancer) {
                for (Server server : balancer.getServers()) {
                    if (server.isHealthy()) {
                        // Use cloud metrics if available, fallback to random updates
                        if (balancer.getCloudManager() != null) {
                            balancer.updateMetricsFromCloud(); // Fetch from CloudWatch
                        } else {
                            double cpu = Math.min(100, Math.max(0, server.getCpuUsage() +
                                    (random.nextDouble() - 0.5) * 20));
                            double mem = Math.min(100, Math.max(0, server.getMemoryUsage() +
                                    (random.nextDouble() - 0.5) * 20));
                            double disk = Math.min(100, Math.max(0, server.getDiskUsage() +
                                    (random.nextDouble() - 0.5) * 20));
                            server.updateMetrics(cpu, mem, disk);
                        }

                        double cpu = server.getCpuUsage();
                        double mem = server.getMemoryUsage();
                        double disk = server.getDiskUsage();

                        if (cpu >= ALERT_THRESHOLD || mem >= ALERT_THRESHOLD || disk >= ALERT_THRESHOLD) {
                            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            String alertMsg = String.format(
                                    "%s - ALERT: Server %s is HOT! CPU:%.2f%% Mem:%.2f%% Disk:%.2f%%",
                                    timestamp, server.getServerId(), cpu, mem, disk);
                            logger.warn(alertMsg);
                            balancer.logAlert(alertMsg);
                        }
                    }
                }
                balancer.checkServerHealth();
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
        logger.info("=== SERVER MONITOR OUT—PEACE ===");
    }
}
