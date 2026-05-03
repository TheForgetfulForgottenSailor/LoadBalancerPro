package core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import gui.Command;
import gui.Command.Status;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.CreateAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.LaunchTemplateSpecification;
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Reservation;

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
    private static final String REQUIRED_LIVE_MUTATION_INTENT = "LOADBALANCERPRO_LIVE_MUTATION";
    private static final String REQUIRED_SANDBOX_RESOURCE_NAME_PREFIX = "lbp-sandbox-";

    private final CloudAwsClients awsClients;
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
        this.awsClients = CloudAwsClients.fromConfig(config);
        if (config.isDryRun()) {
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
    CloudManager(LoadBalancer balancer, CloudConfig config, Ec2Client ec2Client, CloudWatchClient cloudWatchClient,
                 AutoScalingClient autoScalingClient, Consumer<Command> commandRecorder) {
        this(balancer, config, CloudAwsClients.of(ec2Client, cloudWatchClient, autoScalingClient), commandRecorder);
    }

    CloudManager(LoadBalancer balancer, CloudConfig config, CloudAwsClients awsClients, Consumer<Command> commandRecorder) {
        Objects.requireNonNull(balancer, "Balancer cannot be null");
        Objects.requireNonNull(config, "Config cannot be null");
        this.balancer = balancer;
        this.config = config;
        this.awsClients = Objects.requireNonNull(awsClients, "AWS clients cannot be null");
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
        if (config.isLiveMode() && command.getStatus() == Status.COMPLETED) {
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
            if (!manager.canCreateAutoScalingGroup(minServers, maxServers)) {
                manager.logZeroCopy("[{}] Denied Auto Scaling Group creation because mutation guardrails did not pass.",
                        timestamp);
                status = Status.FAILED;
                return;
            }
            CreateAutoScalingGroupRequest asgRequest = CreateAutoScalingGroupRequest.builder()
                    .autoScalingGroupName(manager.config.getAutoScalingGroupName())
                    .minSize(minServers)
                    .maxSize(maxServers)
                    .desiredCapacity(minServers)
                    .launchTemplate(LaunchTemplateSpecification.builder()
                            .launchTemplateId(manager.config.getLaunchTemplateId())
                            .build())
                    .vpcZoneIdentifier(manager.config.getSubnetId())
                    .tags(software.amazon.awssdk.services.autoscaling.model.Tag.builder()
                            .key("LoadBalancerPro")
                            .value(manager.config.getAutoScalingGroupName())
                            .build())
                    .build();

            boolean created = manager.executeWithRetry(() -> {
                manager.awsClients.autoScaling().createAutoScalingGroup(asgRequest);
                manager.logZeroCopy("[{}] Created Auto Scaling Group: {}", timestamp, manager.config.getAutoScalingGroupName());
                return true;
            }, "create Auto Scaling Group", false);
            if (!created) {
                manager.logZeroCopy("[{}] Failed to create Auto Scaling Group {}; initialization stopped.",
                        timestamp, manager.config.getAutoScalingGroupName());
                status = Status.FAILED;
                return;
            }

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
            DescribeAutoScalingGroupsResponse result = executeWithRetry(() ->
                awsClients.autoScaling().describeAutoScalingGroups(
                    DescribeAutoScalingGroupsRequest.builder()
                            .autoScalingGroupNames(config.getAutoScalingGroupName())
                            .build()),
                "describe ASG during initialization", null);
            if (result != null && !result.autoScalingGroups().isEmpty()) {
                int runningCount = (int) result.autoScalingGroups().get(0).instances().stream()
                    .filter(i -> "InService".equals(i.lifecycleStateAsString())).count();
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
        Set<String> ownedInstanceIds = getAutoScalingGroupInstanceIdsForRegistration();
        if (ownedInstanceIds.isEmpty()) {
            logZeroCopy("No ASG-owned instances found for registration; skipped cloud instance registration.");
            return;
        }
        DescribeInstancesResponse result = executeWithRetry(() -> awsClients.ec2().describeInstances(),
                                                          "describe instances during initialization", null);
        if (result != null && result.reservations() != null) {
            for (Reservation reservation : result.reservations()) {
                for (software.amazon.awssdk.services.ec2.model.Instance instance : reservation.instances()) {
                    String instanceId = instance.instanceId();
                    InstanceStateName stateName = instance.state() != null ? instance.state().name() : null;
                    if (!InstanceStateName.RUNNING.equals(stateName)) {
                        continue;
                    }
                    if (!ownedInstanceIds.contains(instanceId)) {
                        logger.debug("Skipping running instance {} because it is not owned by ASG {}",
                                instanceId, config.getAutoScalingGroupName());
                        continue;
                    }
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

    private Set<String> getAutoScalingGroupInstanceIdsForRegistration() {
        DescribeAutoScalingGroupsResponse result = executeWithRetry(() ->
                awsClients.autoScaling().describeAutoScalingGroups(
                        DescribeAutoScalingGroupsRequest.builder()
                                .autoScalingGroupNames(config.getAutoScalingGroupName())
                                .build()),
                "describe ASG instances for registration", null);
        Optional<AutoScalingGroup> asg = findConfiguredAutoScalingGroup(result);
        if (asg.isEmpty() || asg.get().instances() == null) {
            return Set.of();
        }
        return asg.get().instances().stream()
                .map(software.amazon.awssdk.services.autoscaling.model.Instance::instanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void tagInstance(String instanceId) {
        if (config.isDryRun()) {
            logZeroCopy("Dry-run mode: skipped tagging instance {}", instanceId);
            return;
        }
        software.amazon.awssdk.services.ec2.model.CreateTagsRequest tagRequest =
                software.amazon.awssdk.services.ec2.model.CreateTagsRequest.builder()
            .resources(instanceId)
            .tags(software.amazon.awssdk.services.ec2.model.Tag.builder()
                    .key("LoadBalancerPro")
                    .value(config.getAutoScalingGroupName())
                    .build())
            .build();
        executeWithRetry(() -> {
            awsClients.ec2().createTags(tagRequest);
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
        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
            .namespace("AWS/EC2")
            .metricName(metricName)
            .dimensions(Dimension.builder().name("InstanceId").value(instanceId).build())
            .startTime(Instant.ofEpochMilli(System.currentTimeMillis() - 300000))
            .endTime(Instant.ofEpochMilli(System.currentTimeMillis()))
            .period(60)
            .statistics(Statistic.AVERAGE)
            .build();
        GetMetricStatisticsResponse result = executeWithRetry(() -> awsClients.cloudWatch().getMetricStatistics(request),
                                                            "fetch CloudWatch metric " + metricName + " for " + instanceId, null);
        double value = result != null && !result.datapoints().isEmpty() ?
                       validateMetric(result.datapoints().get(0).average()) : 0.0;
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
            scaleServersAsync(desiredCapacity, CloudMutationSource.PREDICTIVE, success ->
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
        return describeCurrentCapacity().orElse(0);
    }

    private OptionalInt describeCurrentCapacity() {
        if (config.isDryRun()) {
            return OptionalInt.of(balancer.getServersByType(ServerType.CLOUD).size());
        }
        DescribeAutoScalingGroupsResponse result = executeWithRetry(() ->
            awsClients.autoScaling().describeAutoScalingGroups(
                DescribeAutoScalingGroupsRequest.builder()
                        .autoScalingGroupNames(config.getAutoScalingGroupName())
                        .build()),
            "get current capacity", null);
        Optional<AutoScalingGroup> asg = findConfiguredAutoScalingGroup(result);
        if (asg.isEmpty() || asg.get().desiredCapacity() == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(asg.get().desiredCapacity());
    }

    private Optional<AutoScalingGroup> findConfiguredAutoScalingGroup(DescribeAutoScalingGroupsResponse result) {
        if (result == null || result.autoScalingGroups() == null) {
            return Optional.empty();
        }
        return result.autoScalingGroups().stream()
                .filter(asg -> config.getAutoScalingGroupName().equals(asg.autoScalingGroupName()))
                .findFirst();
    }

    public void scaleServersAsync(int desiredCapacity, Consumer<Boolean> callback) {
        scaleServersAsync(desiredCapacity, CloudMutationSource.OPERATOR, callback);
    }

    void scaleServersAsync(int desiredCapacity, CloudMutationSource source, Consumer<Boolean> callback) {
        if (desiredCapacity < 0) throw new IllegalArgumentException("Desired capacity cannot be negative: " + desiredCapacity);
        Command command = new ScaleServersCommand(this, desiredCapacity, source, callback);
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
        private final CloudMutationSource source;
        private final Consumer<Boolean> callback;
        private final String id = "ScaleServers-" + System.nanoTime();
        private Status status = Status.PENDING;

        ScaleServersCommand(CloudManager manager, int desiredCapacity, CloudMutationSource source,
                            Consumer<Boolean> callback) {
            this.manager = manager;
            this.desiredCapacity = desiredCapacity;
            this.source = source != null ? source : CloudMutationSource.UNKNOWN;
            this.callback = callback;
        }

        @Override
        public void execute() {
            if (manager.config.isDryRun()) {
                DomainMetrics.recordCloudScaleDecision("DRY_RUN", source, "DRY_RUN");
                manager.logZeroCopy("Dry-run mode: skipped scaling Auto Scaling Group to {} servers", desiredCapacity);
                status = Status.COMPLETED;
                if (callback != null) callback.accept(true);
                return;
            }
            if (!manager.canUpdateAutoScalingGroupCapacity(desiredCapacity, source)) {
                status = Status.FAILED;
                if (callback != null) callback.accept(false);
                return;
            }
            UpdateAutoScalingGroupRequest request = UpdateAutoScalingGroupRequest.builder()
                    .autoScalingGroupName(manager.config.getAutoScalingGroupName())
                    .desiredCapacity(desiredCapacity)
                    .build();
            boolean success = manager.executeWithRetry(() -> {
                manager.awsClients.autoScaling().updateAutoScalingGroup(request);
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
                DeleteAutoScalingGroupRequest deleteRequest = DeleteAutoScalingGroupRequest.builder()
                        .autoScalingGroupName(config.getAutoScalingGroupName())
                        .forceDelete(true)
                        .build();
                executeWithRetry(() -> {
                    awsClients.autoScaling().deleteAutoScalingGroup(deleteRequest);
                    logZeroCopy("Deleted Auto Scaling Group: {}", config.getAutoScalingGroupName());
                }, "delete Auto Scaling Group");
            } else {
                logZeroCopy("Skipped cloud resource deletion. liveMode={}, ownershipConfirmed={}, deletionAllowed={}",
                        config.isLiveMode(), config.isResourceOwnershipConfirmed(), config.isResourceDeletionAllowed());
            }

            awsClients.shutdown();
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
            DescribeAutoScalingGroupsResponse result = awsClients.autoScaling().describeAutoScalingGroups(
                DescribeAutoScalingGroupsRequest.builder()
                        .autoScalingGroupNames(config.getAutoScalingGroupName())
                        .build());
            return result.autoScalingGroups().isEmpty() ? 0 : result.autoScalingGroups().get(0).minSize();
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
        DescribeAutoScalingGroupsResponse result = executeWithRetry(() ->
            awsClients.autoScaling().describeAutoScalingGroups(
                DescribeAutoScalingGroupsRequest.builder()
                        .autoScalingGroupNames(config.getAutoScalingGroupName())
                        .build()),
            "check ASG health", null);
        if (result != null && !result.autoScalingGroups().isEmpty()) {
            AutoScalingGroup asg = result.autoScalingGroups().get(0);
            int healthyCount = (int) asg.instances().stream()
                .filter(i -> "Healthy".equals(i.healthStatus())).count();
            if (healthyCount < asg.minSize()) {
                logZeroCopy("ASG {} unhealthy: {} healthy instances < min size {}; repairing...", 
                            config.getAutoScalingGroupName(), healthyCount, asg.minSize());
                scaleServersAsync(asg.minSize(), CloudMutationSource.SELF_HEALING, success ->
                    logZeroCopy("Self-healing scale to min size {} completed: {}", asg.minSize(), success));
            }
        }
    }

    private void preemptiveInstancePooling() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isShuttingDown.get()) {
                int currentCapacity = getCurrentCapacity();
                int desiredCapacity = currentCapacity + config.getPreemptivePoolSize();
                scaleServersAsync(desiredCapacity, CloudMutationSource.PREEMPTIVE, success ->
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
        String formattedMessage = formatLogMessage(message, args);
        if (logChannel != null && logChannel.isOpen()) {
            try {
                if (logChannel.size() > config.getMaxLogFileSize()) {
                    rotateLogChannel();
                }
                String formatted = String.format("[%s] %s%n", Instant.now(), formattedMessage);
                ByteBuffer buffer = ByteBuffer.wrap(formatted.getBytes(StandardCharsets.UTF_8));
                logChannel.write(buffer);
            } catch (IOException e) {
                logger.error("Zero-copy logging failed: {}", e.getMessage(), e);
                logger.info(formattedMessage);
            }
        } else {
            logger.info(formattedMessage);
        }
    }

    private static String formatLogMessage(String message, Object... args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        if (message.contains("{}")) {
            StringBuilder builder = new StringBuilder(message.length() + args.length * 8);
            int cursor = 0;
            int argIndex = 0;
            int placeholder;
            while ((placeholder = message.indexOf("{}", cursor)) >= 0 && argIndex < args.length) {
                builder.append(message, cursor, placeholder);
                builder.append(String.valueOf(args[argIndex++]));
                cursor = placeholder + 2;
            }
            builder.append(message.substring(cursor));
            if (argIndex < args.length) {
                builder.append(' ')
                        .append(Arrays.toString(Arrays.copyOfRange(args, argIndex, args.length)));
            }
            return builder.toString();
        }
        try {
            return String.format(message, args);
        } catch (IllegalFormatException e) {
            return message + " " + Arrays.toString(args);
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

    private boolean canUpdateAutoScalingGroupCapacity(int desiredCapacity, CloudMutationSource source) {
        if (!config.isLiveMutationAllowed()) {
            auditScaleDecision("DENY", desiredCapacity, -1, -1, source, "ALLOW_LIVE_MUTATION_DISABLED");
            return false;
        }
        if (!REQUIRED_LIVE_MUTATION_INTENT.equals(config.getOperatorIntent())) {
            auditScaleDecision("DENY", desiredCapacity, -1, -1, source, "OPERATOR_INTENT_INVALID");
            return false;
        }
        if (desiredCapacity > config.getMaxDesiredCapacity()) {
            auditScaleDecision("DENY", desiredCapacity, -1, -1, source, "MAX_DESIRED_CAPACITY_EXCEEDED");
            return false;
        }

        OptionalInt currentCapacityResult = describeCurrentCapacity();
        if (currentCapacityResult.isEmpty()) {
            auditScaleDecision("DENY", desiredCapacity, -1, -1, source, "CURRENT_CAPACITY_UNAVAILABLE");
            return false;
        }

        int currentCapacity = currentCapacityResult.getAsInt();
        int scaleStep = Math.abs(desiredCapacity - currentCapacity);
        if (desiredCapacity > currentCapacity && requiresAutonomousScaleUpApproval(source)
                && !config.isAutonomousScaleUpAllowed()) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source,
                    "AUTONOMOUS_SCALE_UP_DISABLED");
            return false;
        }
        if (scaleStep > config.getMaxScaleStep()) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source, "MAX_SCALE_STEP_EXCEEDED");
            return false;
        }
        if (!canScaleInConfiguredEnvironment(desiredCapacity, currentCapacity, scaleStep, source)) {
            return false;
        }
        auditScaleDecision("ALLOW", desiredCapacity, currentCapacity, scaleStep, source, "GUARDRAILS_PASSED");
        return true;
    }

    private boolean canCreateAutoScalingGroup(int minServers, int maxServers) {
        CloudMutationSource source = CloudMutationSource.OPERATOR;
        int currentCapacity = 0;
        int scaleStep = minServers;
        if (!config.isLiveMutationAllowed()) {
            auditScaleDecision("DENY", minServers, currentCapacity, scaleStep, source,
                    "ALLOW_LIVE_MUTATION_DISABLED");
            return false;
        }
        if (!REQUIRED_LIVE_MUTATION_INTENT.equals(config.getOperatorIntent())) {
            auditScaleDecision("DENY", minServers, currentCapacity, scaleStep, source,
                    "OPERATOR_INTENT_INVALID");
            return false;
        }
        if (minServers > config.getMaxDesiredCapacity() || maxServers > config.getMaxDesiredCapacity()) {
            auditScaleDecision("DENY", minServers, currentCapacity, scaleStep, source,
                    "MAX_DESIRED_CAPACITY_EXCEEDED");
            return false;
        }
        if (scaleStep > config.getMaxScaleStep()) {
            auditScaleDecision("DENY", minServers, currentCapacity, scaleStep, source,
                    "MAX_SCALE_STEP_EXCEEDED");
            return false;
        }
        if (!canScaleInConfiguredEnvironment(minServers, currentCapacity, scaleStep, source)) {
            return false;
        }
        auditScaleDecision("ALLOW", minServers, currentCapacity, scaleStep, source, "GUARDRAILS_PASSED");
        return true;
    }

    private boolean canScaleInConfiguredEnvironment(int desiredCapacity, int currentCapacity, int scaleStep,
                                                    CloudMutationSource source) {
        if (config.getEnvironment().isBlank()) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source, "ENVIRONMENT_MISSING");
            return false;
        }
        if (config.getAllowedAwsAccountIds().isEmpty()) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source,
                    "ALLOWED_ACCOUNT_LIST_MISSING");
            return false;
        }
        if (config.getCurrentAwsAccountId().isBlank()
                || !config.getAllowedAwsAccountIds().contains(config.getCurrentAwsAccountId())) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source, "ACCOUNT_NOT_ALLOWED");
            return false;
        }
        if (!config.getAllowedRegions().isEmpty() && !config.getAllowedRegions().contains(config.getRegion())) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source, "REGION_NOT_ALLOWED");
            return false;
        }
        if (isSandboxEnvironment() && config.getResourceNamePrefix().isBlank()) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source,
                    "SANDBOX_RESOURCE_PREFIX_MISSING");
            return false;
        }
        if (isSandboxEnvironment()
                && !config.getResourceNamePrefix().startsWith(REQUIRED_SANDBOX_RESOURCE_NAME_PREFIX)) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source,
                    "SANDBOX_RESOURCE_PREFIX_INVALID");
            return false;
        }
        if (isSandboxEnvironment()
                && !config.getAutoScalingGroupName().startsWith(config.getResourceNamePrefix())) {
            auditScaleDecision("DENY", desiredCapacity, currentCapacity, scaleStep, source,
                    "SANDBOX_RESOURCE_PREFIX_MISMATCH");
            return false;
        }
        return true;
    }

    private boolean isSandboxEnvironment() {
        return "sandbox".equalsIgnoreCase(config.getEnvironment());
    }

    private boolean requiresAutonomousScaleUpApproval(CloudMutationSource source) {
        return source == CloudMutationSource.UNKNOWN
                || source == CloudMutationSource.PREDICTIVE
                || source == CloudMutationSource.PREEMPTIVE;
    }

    private void auditScaleDecision(String decision, int desiredCapacity, int currentCapacity, int scaleStep,
                                    CloudMutationSource source, String reason) {
        DomainMetrics.recordCloudScaleDecision(decision, source, reason);
        logZeroCopy("AUDIT cloud.scale.decision decision=%s source=%s desiredCapacity=%s currentCapacity=%s scaleStep=%s "
                        + "maxDesiredCapacity=%s maxScaleStep=%s environment=%s accountId=%s region=%s asg=%s reason=%s",
                decision,
                source,
                desiredCapacity,
                currentCapacity,
                scaleStep,
                config.getMaxDesiredCapacity(),
                config.getMaxScaleStep(),
                config.getEnvironment(),
                config.getCurrentAwsAccountId(),
                config.getRegion(),
                config.getAutoScalingGroupName(),
                reason);
    }

    private boolean canDeleteCloudResources() {
        if (!config.isLiveMode()
                || !config.isResourceOwnershipConfirmed()
                || !config.isResourceDeletionAllowed()
                || awsClients.autoScaling() == null) {
            return false;
        }

        DescribeAutoScalingGroupsResponse result = executeWithRetry(() ->
                awsClients.autoScaling().describeAutoScalingGroups(
                        DescribeAutoScalingGroupsRequest.builder()
                                .autoScalingGroupNames(config.getAutoScalingGroupName())
                                .build()),
                "validate ASG ownership before deletion", null);
        if (result == null || result.autoScalingGroups().isEmpty()) {
            logZeroCopy("Denied cloud resource deletion. No ASG found for {}", config.getAutoScalingGroupName());
            return false;
        }

        return result.autoScalingGroups().stream()
                .filter(asg -> config.getAutoScalingGroupName().equals(asg.autoScalingGroupName()))
                .anyMatch(this::hasDeletionOwnershipTag);
    }

    private boolean hasDeletionOwnershipTag(AutoScalingGroup asg) {
        return asg.tags() != null && asg.tags().stream()
                .anyMatch(tag -> "LoadBalancerPro".equals(tag.key())
                        && config.getAutoScalingGroupName().equals(tag.value()));
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
