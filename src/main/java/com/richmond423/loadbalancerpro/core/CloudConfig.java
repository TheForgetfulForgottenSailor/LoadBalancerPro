package com.richmond423.loadbalancerpro.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Configuration class for CloudManager, providing customizable settings for AWS integration and scaling.
 */
public class CloudConfig {
    private static final Logger logger = LogManager.getLogger(CloudConfig.class);
    public static final int DEFAULT_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_BASE_DELAY_MS = 1000;
    public static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_POLL_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_METRIC_CACHE_TTL_SECONDS = 60;
    public static final double DEFAULT_LOAD_TREND_THRESHOLD = 0.2;
    public static final int DEFAULT_PREEMPTIVE_POOL_SIZE = 2;
    public static final int DEFAULT_AI_LOOKBACK_MINUTES = 60;
    public static final String DEFAULT_LOG_FILE = "cloud_manager.log";
    public static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    public static final double DEFAULT_MIN_METRIC_VALUE = 0.0;
    public static final double DEFAULT_MAX_METRIC_VALUE = 100.0;
    public static final long DEFAULT_MAX_LOG_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final String LIVE_MODE_PROPERTY = "cloud.liveMode";
    public static final String ALLOW_RESOURCE_DELETION_PROPERTY = "cloud.allowResourceDeletion";
    public static final String CONFIRM_RESOURCE_OWNERSHIP_PROPERTY = "cloud.confirmResourceOwnership";
    public static final String MAX_DESIRED_CAPACITY_PROPERTY = "cloud.maxDesiredCapacity";
    public static final String MAX_SCALE_STEP_PROPERTY = "cloud.maxScaleStep";
    public static final String ALLOW_LIVE_MUTATION_PROPERTY = "cloud.allowLiveMutation";
    public static final String OPERATOR_INTENT_PROPERTY = "cloud.operatorIntent";
    public static final String ALLOW_AUTONOMOUS_SCALE_UP_PROPERTY = "cloud.allowAutonomousScaleUp";
    public static final String ENVIRONMENT_PROPERTY = "cloud.environment";
    public static final String RESOURCE_NAME_PREFIX_PROPERTY = "cloud.resourceNamePrefix";
    public static final String ALLOWED_AWS_ACCOUNT_IDS_PROPERTY = "cloud.allowedAwsAccountIds";
    public static final String CURRENT_AWS_ACCOUNT_ID_PROPERTY = "cloud.currentAwsAccountId";
    public static final String ALLOWED_REGIONS_PROPERTY = "cloud.allowedRegions";
    public static final int DEFAULT_MAX_DESIRED_CAPACITY = 0;
    public static final int DEFAULT_MAX_SCALE_STEP = 0;
    public static final boolean DEFAULT_ALLOW_LIVE_MUTATION = false;
    public static final String DEFAULT_OPERATOR_INTENT = "";
    public static final boolean DEFAULT_ALLOW_AUTONOMOUS_SCALE_UP = false;
    public static final String DEFAULT_ENVIRONMENT = "";
    public static final String DEFAULT_RESOURCE_NAME_PREFIX = "";
    public static final String DEFAULT_CURRENT_AWS_ACCOUNT_ID = "";

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String launchTemplateId;
    private final String autoScalingGroupName;
    private final String subnetId;
    private final int retryAttempts;
    private final long retryBaseDelayMs;
    private final int pollIntervalSeconds;
    private final int pollTimeoutSeconds;
    private final int metricCacheTtlSeconds;
    private final double loadTrendThreshold;
    private final int preemptivePoolSize;
    private final int aiLookbackMinutes;
    private final String logFile;
    private final int threadPoolSize;
    private final double minMetricValue;
    private final double maxMetricValue;
    private final long maxLogFileSize;
    private final boolean liveMode;
    private final boolean allowResourceDeletion;
    private final boolean resourceOwnershipConfirmed;
    private final int maxDesiredCapacity;
    private final int maxScaleStep;
    private final boolean allowLiveMutation;
    private final String operatorIntent;
    private final boolean allowAutonomousScaleUp;
    private final String environment;
    private final String resourceNamePrefix;
    private final List<String> allowedAwsAccountIds;
    private final String currentAwsAccountId;
    private final List<String> allowedRegions;

    public CloudConfig(String accessKey, String secretKey, String region, String launchTemplateId, String subnetId) {
        this(accessKey, secretKey, region, launchTemplateId, subnetId, new Properties());
    }

    public CloudConfig(String accessKey, String secretKey, String region, String launchTemplateId, String subnetId, Properties props) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.launchTemplateId = launchTemplateId;
        this.subnetId = subnetId;
        validateCredentials(accessKey, secretKey);
        this.resourceNamePrefix = parseValidatedString(props, RESOURCE_NAME_PREFIX_PROPERTY,
                DEFAULT_RESOURCE_NAME_PREFIX, this::isValidResourceNamePrefix);
        this.autoScalingGroupName = resourceNamePrefix + "LoadBalancerPro-ASG-"
                + UUID.randomUUID().toString().substring(0, 8);
        this.retryAttempts = parseInt(props, "retryAttempts", DEFAULT_RETRY_ATTEMPTS, 1, Integer.MAX_VALUE);
        this.retryBaseDelayMs = parseLong(props, "retryBaseDelayMs", DEFAULT_RETRY_BASE_DELAY_MS, 100, Long.MAX_VALUE);
        this.pollIntervalSeconds = parseInt(props, "pollIntervalSeconds", DEFAULT_POLL_INTERVAL_SECONDS, 1, 60);
        this.pollTimeoutSeconds = parseInt(props, "pollTimeoutSeconds", DEFAULT_POLL_TIMEOUT_SECONDS, 60, Integer.MAX_VALUE);
        this.metricCacheTtlSeconds = parseInt(props, "metricCacheTtlSeconds", DEFAULT_METRIC_CACHE_TTL_SECONDS, 10, Integer.MAX_VALUE);
        this.loadTrendThreshold = parseDouble(props, "loadTrendThreshold", DEFAULT_LOAD_TREND_THRESHOLD, 0.1, 1.0);
        this.preemptivePoolSize = parseInt(props, "preemptivePoolSize", DEFAULT_PREEMPTIVE_POOL_SIZE, 0, 10);
        this.aiLookbackMinutes = parseInt(props, "aiLookbackMinutes", DEFAULT_AI_LOOKBACK_MINUTES, 10, Integer.MAX_VALUE);
        this.logFile = props.getProperty("logFile", DEFAULT_LOG_FILE);
        this.threadPoolSize = parseInt(props, "threadPoolSize", DEFAULT_THREAD_POOL_SIZE, 1, Integer.MAX_VALUE);
        this.minMetricValue = parseDouble(props, "minMetricValue", DEFAULT_MIN_METRIC_VALUE, 0.0, Double.MAX_VALUE);
        this.maxMetricValue = parseDouble(props, "maxMetricValue", DEFAULT_MAX_METRIC_VALUE, 0.0, Double.MAX_VALUE);
        this.maxLogFileSize = parseLong(props, "maxLogFileSize", DEFAULT_MAX_LOG_FILE_SIZE, 1024 * 1024, Long.MAX_VALUE);
        this.liveMode = parseBoolean(props, LIVE_MODE_PROPERTY, false);
        this.allowResourceDeletion = parseBoolean(props, ALLOW_RESOURCE_DELETION_PROPERTY, false);
        this.resourceOwnershipConfirmed = parseBoolean(props, CONFIRM_RESOURCE_OWNERSHIP_PROPERTY, false);
        this.maxDesiredCapacity = parseInt(props, MAX_DESIRED_CAPACITY_PROPERTY, DEFAULT_MAX_DESIRED_CAPACITY, 0, Integer.MAX_VALUE);
        this.maxScaleStep = parseInt(props, MAX_SCALE_STEP_PROPERTY, DEFAULT_MAX_SCALE_STEP, 0, Integer.MAX_VALUE);
        this.allowLiveMutation = parseBoolean(props, ALLOW_LIVE_MUTATION_PROPERTY, DEFAULT_ALLOW_LIVE_MUTATION);
        this.operatorIntent = parseString(props, OPERATOR_INTENT_PROPERTY, DEFAULT_OPERATOR_INTENT);
        this.allowAutonomousScaleUp = parseBoolean(props, ALLOW_AUTONOMOUS_SCALE_UP_PROPERTY, DEFAULT_ALLOW_AUTONOMOUS_SCALE_UP);
        this.environment = parseString(props, ENVIRONMENT_PROPERTY, DEFAULT_ENVIRONMENT);
        this.allowedAwsAccountIds = parseCsvList(props, ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, this::isValidAwsAccountId);
        this.currentAwsAccountId = parseValidatedString(props, CURRENT_AWS_ACCOUNT_ID_PROPERTY,
                DEFAULT_CURRENT_AWS_ACCOUNT_ID, this::isValidAwsAccountId);
        this.allowedRegions = parseCsvList(props, ALLOWED_REGIONS_PROPERTY, this::isValidRegion);
    }

    private void validateCredentials(String accessKey, String secretKey) {
        if (isPlaceholder(accessKey) || isPlaceholder(secretKey)) {
            throw new IllegalArgumentException("Placeholder AWS credentials are not allowed.");
        }
    }

    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("placeholder")
            || normalized.startsWith("your-")
            || normalized.startsWith("mock_")
            || normalized.startsWith("test_")
            || normalized.equals("access-key")
            || normalized.equals("secret-key");
    }

    private int parseInt(Properties props, String key, int defaultValue, int min, int max) {
        try {
            int val = Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                logger.warn("{}={} out of range [{}-{}]; using default {}", key, val, min, max, defaultValue);
                return defaultValue;
            }
            return val;
        } catch (Exception e) {
            logger.info("Using default value for {}: {}. Reason: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    private long parseLong(Properties props, String key, long defaultValue, long min, long max) {
        try {
            long val = Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                logger.warn("{}={} out of range [{}-{}]; using default {}", key, val, min, max, defaultValue);
                return defaultValue;
            }
            return val;
        } catch (Exception e) {
            logger.info("Using default value for {}: {}. Reason: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    private double parseDouble(Properties props, String key, double defaultValue, double min, double max) {
        try {
            double val = Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                logger.warn("{}={} out of range [{}-{}]; using default {}", key, val, min, max, defaultValue);
                return defaultValue;
            }
            return val;
        } catch (Exception e) {
            logger.info("Using default value for {}: {}. Reason: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    private boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String raw = props.getProperty(key, System.getProperty(key, String.valueOf(defaultValue)));
        return Boolean.parseBoolean(raw);
    }

    private String parseString(Properties props, String key, String defaultValue) {
        String raw = props.getProperty(key, System.getProperty(key, defaultValue));
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return raw.trim();
    }

    private String parseValidatedString(Properties props, String key, String defaultValue, Predicate<String> validator) {
        String value = parseString(props, key, defaultValue);
        if (value.equals(defaultValue) || validator.test(value)) {
            return value;
        }
        logger.warn("{}={} is invalid; using default {}", key, value, defaultValue);
        return defaultValue;
    }

    private List<String> parseCsvList(Properties props, String key, Predicate<String> validator) {
        String raw = props.getProperty(key, System.getProperty(key, ""));
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> {
                    boolean valid = validator.test(value);
                    if (!valid) {
                        logger.warn("Ignoring invalid {} entry: {}", key, value);
                    }
                    return valid;
                })
                .distinct()
                .toList();
    }

    private boolean isValidAwsAccountId(String value) {
        return value != null && value.matches("\\d{12}");
    }

    private boolean isValidRegion(String value) {
        return value != null && value.matches("[a-z]{2}(-gov)?-[a-z]+-\\d");
    }

    private boolean isValidResourceNamePrefix(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9-]{0,63}");
    }

    // Getters
    public String getAccessKey() { return accessKey; }
    public String getSecretKey() { return secretKey; }
    public String getRegion() { return region; }
    public String getLaunchTemplateId() { return launchTemplateId; }
    public String getAutoScalingGroupName() { return autoScalingGroupName; }
    public String getSubnetId() { return subnetId; }
    public int getRetryAttempts() { return retryAttempts; }
    public long getRetryBaseDelayMs() { return retryBaseDelayMs; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public int getPollTimeoutSeconds() { return pollTimeoutSeconds; }
    public int getMetricCacheTtlSeconds() { return metricCacheTtlSeconds; }
    public double getLoadTrendThreshold() { return loadTrendThreshold; }
    public int getPreemptivePoolSize() { return preemptivePoolSize; }
    public int getAiLookbackMinutes() { return aiLookbackMinutes; }
    public String getLogFile() { return logFile; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public double getMinMetricValue() { return minMetricValue; }
    public double getMaxMetricValue() { return maxMetricValue; }
    public long getMaxLogFileSize() { return maxLogFileSize; }
    public boolean isLiveMode() { return liveMode; }
    public boolean isDryRun() { return !liveMode; }
    public boolean isResourceDeletionAllowed() { return allowResourceDeletion; }
    public boolean isResourceOwnershipConfirmed() { return resourceOwnershipConfirmed; }
    public int getMaxDesiredCapacity() { return maxDesiredCapacity; }
    public int getMaxScaleStep() { return maxScaleStep; }
    public boolean isLiveMutationAllowed() { return allowLiveMutation; }
    public String getOperatorIntent() { return operatorIntent; }
    public boolean isAutonomousScaleUpAllowed() { return allowAutonomousScaleUp; }
    public String getEnvironment() { return environment; }
    public String getResourceNamePrefix() { return resourceNamePrefix; }
    public List<String> getAllowedAwsAccountIds() { return allowedAwsAccountIds; }
    public String getCurrentAwsAccountId() { return currentAwsAccountId; }
    public List<String> getAllowedRegions() { return allowedRegions; }
}
