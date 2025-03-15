package core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerMonitor implements Runnable {
    private static final Logger logger = LogManager.getLogger(ServerMonitor.class);
    private final LoadBalancer balancer;
    private volatile boolean running = true;
    private final Random random = new Random();
    private final double alertThreshold = 90.0;

    public ServerMonitor(LoadBalancer balancer) {
        this.balancer = balancer;
    }

    public void stop() {
        running = false;
    }

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

                        if (cpu >= alertThreshold || mem >= alertThreshold || disk >= alertThreshold) {
                            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            String alertMsg = String.format("%s - ALERT: Server %s is HOT! CPU:%.2f%% Mem:%.2f%% Disk:%.2f%%",
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
