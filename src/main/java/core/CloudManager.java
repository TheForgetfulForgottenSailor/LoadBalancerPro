package core;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import gui.Command;
import gui.Command.Status;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages AWS cloud instances for LoadBalancerPro, integrating with LoadBalancer for scaling and monitoring.
 */
public class CloudManager {
    private static final Logger logger = LogManager.getLogger(CloudManager.class);

    private final AmazonEC2 ec2Client;
    private final AmazonCloudWatch cloudWatchClient;
    private final AmazonAutoScaling autoScalingClient;
    private final LoadBalancer balancer;
    private final CloudConfig config;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Cache<String, MetricCacheEntry> metricCache;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean backgroundJobsStarted = new AtomicBoolean(false);
    private FileChannel logChannel;
    private final Consumer<Command> commandRecorder;

    public CloudManager(LoadBalancer balancer, CloudConfig config, Consumer<Command> commandRecorder) {
        Objects.requireNonNull(balancer, "Balancer cannot be null");
        Objects.requireNonNull(config, "Config cannot be null");
        this.balancer = balancer;
        this.config = config;
        this.commandRecorder = commandRecorder != null ? commandRecorder : cmd -> {};
        if (config.isLiveMode()) {
            BasicAWSCredentials credentials = new BasicAWSCredentials(config.getAccessKey(), config.getSecretKey());
            AWSStaticCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
            this.ec2Client = AmazonEC2ClientBuilder.standard()
                    .withCredentials(provider)
                    .withRegion(config.getRegion())
                    .build();
            this.cloudWatchClient = AmazonCloudWatchClientBuilder.standard()
                    .withCredentials(provider)
                    .withRegion(config.getRegion())
                    .build();
            this.autoScalingClient = AmazonAutoScalingClientBuilder.standard()
                    .withCredentials(provider)
                    .withRegion(config.getRegion())
                    .build();
        } else {
            this.ec2Client = null;
            this.cloudWatchClient = null;
            this.autoScalingClient = null;
            logger.warn("CloudManager running in dry-run mode. AWS operations are disabled.");
        }
        this.executor = Executors.newFixedThreadPool(config.getThreadPoolSize());
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.metricCache = Caffeine.newBuilder()
                .expireAfterWrite(config.getMetricCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
        this.logChannel = initializeZeroCopyLogging();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public CloudManager(LoadBalancer balancer, String accessKey, String secretKey, String region) {
        this(balancer, new CloudConfig(accessKey, secretKey, region, "", ""), null);
    }

    /**
     * Legacy bridge for command objects that still need to coordinate UI undo state.
     * Prefer passing the LoadBalancer explicitly to new code.
     */
    @Deprecated
    public LoadBalancer getBalancer() {
        return balancer;
    }

    // For testing with injected clients
    CloudManager(LoadBalancer balancer, CloudConfig config, AmazonEC2 ec2Client, AmazonCloudWatch cloudWatchClient,
                 AmazonAutoScaling autoScalingClient, Consumer<Command> commandRecorder) {
        this.balancer = balancer;
        this.config = config;
        this.ec2Client = ec2Client;
        this.cloudWatchClient = cloudWatchClient;
        this.autoScalingClient = autoScalingClient;
        this.executor = Executors.newFixedThreadPool(config.getThreadPoolSize());
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.metricCache = Caffeine.newBuilder()
                .expireAfterWrite(config.getMetricCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
        this.logChannel = initializeZeroCopyLogging();
        this.commandRecorder = commandRecorder != null ? commandRecorder : cmd -> {};
    }

    private FileChannel initializeZeroCopyLogging() {
        try {
            return FileChannel.open(Paths.get(config.getLogFile()), 
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("Failed to initialize zero-copy logging: {}", e.getMessage(), e);
            logger.warn("Falling back to standard logging due to initialization failure.");
            return null;
        }
    }

    public void initializeCloudServers(int minServers, int maxServers) throws InterruptedException {
        if (minServers < 0 || maxServers < minServers) {
            throw new IllegalArgumentException("Invalid server counts: min=" + minServers + ", max=" + maxServers);
        }
        Command command = new InitializeCloudCommand(this, minServers, maxServers);
        command.execute();
        if (config.isLiveMode()) {
            startBackgroundJobs();
        }
        commandRecorder.accept(command);
    }

    public void startBackgroundJobs() {
        if (!config.isLiveMode()) {
            logger.info("Dry-run mode: background cloud jobs are disabled.");
            return;
        }
        if (backgroundJobsStarted.compareAndSet(false, true)) {
            startSelfHealing();
            preemptiveInstancePooling();
        }
    }

    private static class InitializeCloudCommand implements Command {
        private final CloudManager manager;
        private final int minServers;
        private final int maxServers;
        private final String id = "InitializeCloud-" + System.nanoTime();
        private Status status = Status.PENDING;

        InitializeCloudCommand(CloudManager manager, int minServers, int maxServers) {
            this.manager = manager;
            this.minServers = minServers;
            this.maxServers = maxServers;
        }

        @Override
        public void execute() {
            String timestamp = Instant.now().toString();
            manager.logZeroCopy("[{}] Initializing cloud servers: min={}, max={}", timestamp, minServers, maxServers);
            if (manager.config.isDryRun()) {
                manager.logZeroCopy("[{}] Dry-run mode: skipped Auto Scaling Group creation.", timestamp);
                status = Status.COMPLETED;
                return;
            }
            CreateAutoScalingGroupRequest asgRequest = new CreateAutoScalingGroupRequest()
                    .withAutoScalingGroupName(manager.config.getAutoScalingGroupName())
                    .withMinSize(minServers)
                    .withMaxSize(maxServers)
                    .withDesiredCapacity(minServers)
                    .withLaunchTemplate(new com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification().withLaunchTemplateId(manager.config.getLaunchTemplateId()))
                    .withVPCZoneIdentifier(manager.config.getSubnetId())
                    .withTags(new com.amazonaws.services.autoscaling.model.Tag().withKey("LoadBalancerPro").withValue(manager.config.getAutoScalingGroupName()));

            manager.executeWithRetry(() -> {
                manager.autoScalingClient.createAutoScalingGroup(asgRequest);
                manager.logZeroCopy("[{}] Created Auto Scaling Group: {}", timestamp, manager.config.getAutoScalingGroupName());
            }, "create Auto Scaling Group");

            try {
                manager.waitForInstancesReady(minServers);
                manager.registerRunningInstances(true);
                status = Status.COMPLETED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                manager.logZeroCopy("Initialization interrupted: {}", e.getMessage());
                status = Status.FAILED;
            }
        }

        @Override
        public void undo() {
            manager.logZeroCopy("Undo not supported for cloud initialization.");
        }

        @Override
        public boolean canUndo() { return false; }
        @Override
        public String getDescription() { return "Initialize " + minServers + " to " + maxServers + " cloud servers"; }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    private void waitForInstancesReady(int minServers) throws InterruptedException {
        if (config.isDryRun()) {
            logZeroCopy("Dry-run mode: skipped waiting for cloud instances.");
            return;
        }
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < config.getPollTimeoutSeconds() * 1000 && !isShuttingDown.get()) {
            DescribeAutoScalingGroupsResult result = executeWithRetry(() -> 
                autoScalingClient.describeAutoScalingGroups(
                    new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(config.getAutoScalingGroupName())),
                "describe ASG during initialization", null);
            if (result != null && !result.getAutoScalingGroups().isEmpty()) {
                int runningCount = (int) result.getAutoScalingGroups().get(0).getInstances().stream()
                    .filter(i -> i.getLifecycleState().equals("InService")).count();
                if (runningCount >= minServers) {
                    logZeroCopy("All {} instances are in service after {}ms", minServers, 
                                System.currentTimeMillis() - startTime);
                    return;
                }
            }
            Thread.sleep(config.getPollIntervalSeconds() * 1000);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Wait for instances interrupted");
            }
        }
        if (!isShuttingDown.get()) {
            logZeroCopy("Timeout waiting for {} instances to be ready after {}s", minServers, config.getPollTimeoutSeconds());
        }
    }

    private void registerRunningInstances(boolean tagInstances) {
        if (config.isDryRun()) {
            logZeroCopy("Dry-run mode: skipped cloud instance registration.");
            return;
        }
        DescribeInstancesResult result = executeWithRetry(() -> ec2Client.describeInstances(), 
                                                          "describe instances during initialization", null);
        if (result != null) {
            for (Reservation reservation : result.getReservations()) {
                for (com.amazonaws.services.ec2.model.Instance instance : reservation.getInstances()) {
                    if (instance.getState().getName().equals("running")) {
                        String instanceId = instance.getInstanceId();
                        Server server = new Server(instanceId, 10.0, 20.0, 30.0, ServerType.CLOUD, 
                                                  msg -> logZeroCopy("Health alert for {}: {}", instanceId, msg));
                        server.setCapacity(500.0);
                        balancer.addServer(server);
                        if (tagInstances) {
                            tagInstance(instanceId);
                        }
                        logZeroCopy("Registered and tagged cloud server: {}", instanceId);
                    }
                }
            }
        }
    }

    private void tagInstance(String instanceId) {
        if (config.isDryRun()) {
            logZeroCopy("Dry-run mode: skipped tagging instance {}", instanceId);
            return;
        }
        CreateTagsRequest tagRequest = new CreateTagsRequest()
            .withResources(instanceId)
            .withTags(new com.amazonaws.services.ec2.model.Tag().withKey("LoadBalancerPro").withValue(config.getAutoScalingGroupName()));
        executeWithRetry(() -> {
            ec2Client.createTags(tagRequest);
            logger.debug("Tagged instance {} with LoadBalancerPro", instanceId);
        }, "tag instance " + instanceId);
    }

    public void updateServerMetricsFromCloud() {
        if (config.isDryRun()) {
            logger.info("Dry-run mode: skipped CloudWatch metric updates.");
            return;
        }
        Flux.fromIterable(balancer.getServers())
            .filter(s -> s.getServerType() == ServerType.CLOUD)
            .parallel()
            .runOn(Schedulers.fromExecutor(executor))
            .doOnNext(server -> {
                String instanceId = server.getServerId();
                try {
                    MetricCacheEntry cpuEntry = metricCache.get(instanceId + "-CPUUtilization", 
                        k -> fetchMetric(instanceId, "CPUUtilization"));
                    double cpu = cpuEntry != null && cpuEntry.isValid() ? cpuEntry.value : fetchMetric(instanceId, "CPUUtilization").value;
                    double mem = estimateMemoryUsage(cpu);
                    double disk = estimateDiskUsage(cpu);
                    server.updateMetrics(cpu, mem, disk);
                    logger.debug("Updated metrics for server {}: CPU={}, Mem={}, Disk={}", instanceId, cpu, mem, disk);
                    checkLoadTrend(server);
                } catch (Exception e) {
                    logger.warn("Failed to update metrics for server {}: {}", instanceId, e.getMessage());
                }
            })
            .sequential()
            .blockLast(Duration.ofSeconds(30));
    }

    private MetricCacheEntry fetchMetric(String instanceId, String metricName) {
        if (config.isDryRun()) {
            return new MetricCacheEntry(0.0, System.currentTimeMillis());
        }
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
            .withNamespace("AWS/EC2")
            .withMetricName(metricName)
            .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
            .withStartTime(new Date(System.currentTimeMillis() - 300000))
            .withEndTime(new Date(System.currentTimeMillis()))
            .withPeriod(60)
            .withStatistics(Statistic.Average);
        GetMetricStatisticsResult result = executeWithRetry(() -> cloudWatchClient.getMetricStatistics(request), 
                                                            "fetch CloudWatch metric " + metricName + " for " + instanceId, null);
        double value = result != null && !result.getDatapoints().isEmpty() ? 
                       validateMetric(result.getDatapoints().get(0).getAverage()) : 0.0;
        return new MetricCacheEntry(value, System.currentTimeMillis());
    }

    private double validateMetric(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < config.getMinMetricValue() || value > config.getMaxMetricValue()) {
            logger.warn("Invalid CloudWatch metric value: {}; defaulting to 0", value);
            return 0.0;
        }
        return value;
    }

    private double estimateMemoryUsage(double cpu) {
        return Math.min(cpu * 1.2, config.getMaxMetricValue());
    }

    private double estimateDiskUsage(double cpu) {
        return Math.min(cpu * 0.8, config.getMaxMetricValue());
    }

    private void checkLoadTrend(Server server) {
        double currentLoad = server.getLoadScore();
        double[] loadHistory = server.getCpuHistorySnapshot();
        double avgLoad = Arrays.stream(loadHistory).average().orElse(0.0);
        if (currentLoad > avgLoad * (1 + config.getLoadTrendThreshold())) {
            int currentCapacity = getCurrentCapacity();
            int predictedCapacity = predictCapacityWithAI();
            int desiredCapacity = Math.max(Math.max(currentCapacity + 1, predictedCapacity), getMinServers());
            scaleServersAsync(desiredCapacity, success -> 
                logZeroCopy("Predictive scale-up to {} servers completed: {}", desiredCapacity, success));
        }
    }

    private int predictCapacityWithAI() {
        List<Server> cloudServers = balancer.getServers().stream()
            .filter(s -> s.getServerType() == ServerType.CLOUD)
            .collect(Collectors.toList());
        double totalLoad = cloudServers.stream().mapToDouble(Server::getLoadScore).sum();
        double avgLoadPerServer = totalLoad / Math.max(1, cloudServers.size());
        double predictedLoad = avgLoadPerServer * 1.5; // Simple AI-like prediction
        int currentCapacity = getCurrentCapacity();
        return (int) Math.ceil(totalLoad / predictedLoad) + config.getPreemptivePoolSize();
    }

    public int getCurrentCapacity() {
        if (config.isDryRun()) {
            return balancer.getServersByType(ServerType.CLOUD).size();
        }
        DescribeAutoScalingGroupsResult result = executeWithRetry(() -> 
            autoScalingClient.describeAutoScalingGroups(
                new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(config.getAutoScalingGroupName())),
            "get current capacity", null);
        return result != null && !result.getAutoScalingGroups().isEmpty() ? 
               result.getAutoScalingGroups().get(0).getDesiredCapacity() : 0;
    }

    public void scaleServersAsync(int desiredCapacity, Consumer<Boolean> callback) {
        if (desiredCapacity < 0) throw new IllegalArgumentException("Desired capacity cannot be negative: " + desiredCapacity);
        Command command = new ScaleServersCommand(this, desiredCapacity, callback);
        commandRecorder.accept(command);
        if (config.isDryRun()) {
            command.execute();
        } else {
            executor.submit(command::execute);
        }
    }

    public void scaleServers(int desiredCapacity) {
        scaleServersAsync(desiredCapacity, null);
    }

    private static class ScaleServersCommand implements Command {
        private final CloudManager manager;
        private final int desiredCapacity;
        private final Consumer<Boolean> callback;
        private final String id = "ScaleServers-" + System.nanoTime();
        private Status status = Status.PENDING;

        ScaleServersCommand(CloudManager manager, int desiredCapacity, Consumer<Boolean> callback) {
            this.manager = manager;
            this.desiredCapacity = desiredCapacity;
            this.callback = callback;
        }

        @Override
        public void execute() {
            if (manager.config.isDryRun()) {
                manager.logZeroCopy("Dry-run mode: skipped scaling Auto Scaling Group to {} servers", desiredCapacity);
                status = Status.COMPLETED;
                if (callback != null) callback.accept(true);
                return;
            }
            UpdateAutoScalingGroupRequest request = new UpdateAutoScalingGroupRequest()
                    .withAutoScalingGroupName(manager.config.getAutoScalingGroupName())
                    .withDesiredCapacity(desiredCapacity);
            boolean success = manager.executeWithRetry(() -> {
                manager.autoScalingClient.updateAutoScalingGroup(request);
                manager.logZeroCopy("Scaled Auto Scaling Group {} to {} servers", 
                                    manager.config.getAutoScalingGroupName(), desiredCapacity);
                return true;
            }, "scale Auto Scaling Group", false);
            status = success ? Status.COMPLETED : Status.FAILED;
            if (callback != null) callback.accept(success);
        }

        @Override
        public void undo() {
            manager.logZeroCopy("Undo not supported for scaling operation.");
        }

        @Override
        public boolean canUndo() { return false; }
        @Override
        public String getDescription() { return "Scale servers to " + desiredCapacity; }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    public void shutdown() {
        if (isShuttingDown.compareAndSet(false, true)) {
            if (canDeleteCloudResources()) {
                DeleteAutoScalingGroupRequest deleteRequest = new DeleteAutoScalingGroupRequest()
                        .withAutoScalingGroupName(config.getAutoScalingGroupName())
                        .withForceDelete(true);
                executeWithRetry(() -> {
                    autoScalingClient.deleteAutoScalingGroup(deleteRequest);
                    logZeroCopy("Deleted Auto Scaling Group: {}", config.getAutoScalingGroupName());
                }, "delete Auto Scaling Group");
            } else {
                logZeroCopy("Skipped cloud resource deletion. liveMode={}, ownershipConfirmed={}, deletionAllowed={}",
                        config.isLiveMode(), config.isResourceOwnershipConfirmed(), config.isResourceDeletionAllowed());
            }

            if (ec2Client != null) ec2Client.shutdown();
            if (cloudWatchClient != null) cloudWatchClient.shutdown();
            if (autoScalingClient != null) autoScalingClient.shutdown();
            scheduler.shutdown();
            executor.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    logZeroCopy("Scheduler shutdown timed out; forced termination.");
                }
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    logZeroCopy("Executor shutdown timed out; forced termination.");
                }
                if (logChannel != null && logChannel.isOpen()) {
                    logChannel.close();
                    logZeroCopy("Log channel closed successfully.");
                }
                logZeroCopy("CloudManager shutdown complete");
            } catch (InterruptedException | IOException e) {
                logger.error("Shutdown interrupted or failed to close log channel: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public int getMinServers() {
        if (config.isDryRun()) {
            return 0;
        }
        try {
            DescribeAutoScalingGroupsResult result = autoScalingClient.describeAutoScalingGroups(
                new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(config.getAutoScalingGroupName()));
            return result.getAutoScalingGroups().isEmpty() ? 0 : result.getAutoScalingGroups().get(0).getMinSize();
        } catch (Exception e) {
            logger.error("Failed to get min servers for ASG {}: {}", config.getAutoScalingGroupName(), e.getMessage(), e);
            return 0;
        }
    }

    public double getCloudMetric(String instanceId, String metricName) {
        if (config.isDryRun()) {
            return 0.0;
        }
        MetricCacheEntry cacheEntry = metricCache.get(instanceId + "-" + metricName, 
            k -> fetchMetric(instanceId, metricName));
        return cacheEntry != null && cacheEntry.isValid() ? cacheEntry.value : fetchMetric(instanceId, metricName).value;
    }

    private void startSelfHealing() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isShuttingDown.get()) {
                checkASGHealth();
            } else {
                logger.debug("Skipping self-healing due to shutdown");
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    private void checkASGHealth() {
        if (config.isDryRun()) {
            return;
        }
        DescribeAutoScalingGroupsResult result = executeWithRetry(() -> 
            autoScalingClient.describeAutoScalingGroups(
                new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(config.getAutoScalingGroupName())),
            "check ASG health", null);
        if (result != null && !result.getAutoScalingGroups().isEmpty()) {
            AutoScalingGroup asg = result.getAutoScalingGroups().get(0);
            int healthyCount = (int) asg.getInstances().stream()
                .filter(i -> i.getHealthStatus().equals("Healthy")).count();
            if (healthyCount < asg.getMinSize()) {
                logZeroCopy("ASG {} unhealthy: {} healthy instances < min size {}; repairing...", 
                            config.getAutoScalingGroupName(), healthyCount, asg.getMinSize());
                scaleServersAsync(asg.getMinSize(), success -> 
                    logZeroCopy("Self-healing scale to min size {} completed: {}", asg.getMinSize(), success));
            }
        }
    }

    private void preemptiveInstancePooling() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isShuttingDown.get()) {
                int currentCapacity = getCurrentCapacity();
                int desiredCapacity = currentCapacity + config.getPreemptivePoolSize();
                scaleServersAsync(desiredCapacity, success -> 
                    logZeroCopy("Preemptive pool increased to {} servers: {}", desiredCapacity, success));
            } else {
                logger.debug("Skipping preemptive pooling due to shutdown");
            }
        }, 0, 300, TimeUnit.SECONDS);
    }

    private <T> T executeWithRetry(CheckedSupplier<T> action, String operation) {
        return executeWithRetry(action, operation, null);
    }

    private void executeWithRetry(CheckedRunnable action, String operation) {
        executeWithRetry(() -> {
            action.run();
            return null;
        }, operation, null);
    }

    private <T> T executeWithRetry(CheckedSupplier<T> action, String operation, T defaultValue) {
        if (config.isDryRun()) {
            logZeroCopy("Dry-run mode: skipped {}", operation);
            return defaultValue;
        }
        for (int attempts = config.getRetryAttempts(); attempts > 0; attempts--) {
            try {
                return action.get();
            } catch (Exception e) {
                if (attempts == 1) {
                    logger.error("Failed to {} after {} retries: {}", operation, config.getRetryAttempts(), e.getMessage(), e);
                    return defaultValue;
                }
                long delay = config.getRetryBaseDelayMs() * (long) Math.pow(2, config.getRetryAttempts() - attempts) + 
                             ThreadLocalRandom.current().nextInt(1000);
                logZeroCopy("Attempt {}/{} failed for {}: {}; retrying in {}ms...", 
                            config.getRetryAttempts() - attempts + 1, config.getRetryAttempts(), operation, e.getMessage(), delay);
                try { 
                    Thread.sleep(delay); 
                } catch (InterruptedException ie) { 
                    Thread.currentThread().interrupt();
                    logZeroCopy("Retry interrupted for {}: {}", operation, ie.getMessage());
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private void logZeroCopy(String message, Object... args) {
        if (logChannel != null && logChannel.isOpen()) {
            try {
                if (logChannel.size() > config.getMaxLogFileSize()) {
                    rotateLogChannel();
                }
                String formatted = String.format("[%s] %s%n", Instant.now(), String.format(message, args));
                ByteBuffer buffer = ByteBuffer.wrap(formatted.getBytes(StandardCharsets.UTF_8));
                logChannel.write(buffer);
            } catch (IOException e) {
                logger.error("Zero-copy logging failed: {}", e.getMessage(), e);
                logger.info(message, args);
            }
        } else {
            logger.info(message, args);
        }
    }

    private synchronized void rotateLogChannel() throws IOException {
        if (logChannel != null && logChannel.isOpen()) {
            logChannel.close();
            Path logPath = Paths.get(config.getLogFile());
            Path rotatedPath = Paths.get(config.getLogFile() + "." + System.currentTimeMillis());
            Files.move(logPath, rotatedPath, StandardCopyOption.REPLACE_EXISTING);
            logChannel = FileChannel.open(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("Rotated log file to {}", rotatedPath);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private boolean canDeleteCloudResources() {
        return config.isLiveMode()
                && config.isResourceOwnershipConfirmed()
                && config.isResourceDeletionAllowed()
                && autoScalingClient != null;
    }

    private static class MetricCacheEntry {
        final double value;
        final long timestamp;

        MetricCacheEntry(double value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        boolean isValid() {
            return System.currentTimeMillis() - timestamp < CloudConfig.DEFAULT_METRIC_CACHE_TTL_SECONDS * 1000;
        }
    }
}
