package com.richmond423.loadbalancerpro.cli;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configuration class for the CLI interface of LoadBalancerPro, supporting both CLI and cloud settings.
 */
public class CliConfig {
    private static final Logger logger = LogManager.getLogger(CliConfig.class);

    // Class-level constants
    private static final int DEFAULT_MAX_SERVER_ID_LENGTH = 50;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_SERVERS_PER_PAGE = 20;
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_PROGRESS_ANIMATION_SPEED = 200;
    private static final double DEFAULT_ALERT_THRESHOLD = 90.0;
    private static final long DEFAULT_MONITOR_INTERVAL_MS = 5000L;
    private static final double DEFAULT_MAX_FLUCTUATION = 20.0;
    private static final boolean DEFAULT_CLOUD_ENABLED = false;
    private static final int DEFAULT_CLOUD_MIN_SERVERS = 1;
    private static final int DEFAULT_CLOUD_MAX_SERVERS = 10;
    private static final int MIN_MAX_SERVER_ID_LENGTH = 1;
    private static final int MIN_MAX_RETRIES = 1;
    private static final int MIN_SERVERS_PER_PAGE = 1;
    private static final int MIN_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 3600;
    private static final int MIN_PROGRESS_ANIMATION_SPEED = 50;
    private static final double MIN_ALERT_THRESHOLD = 0.0;
    private static final double MAX_ALERT_THRESHOLD = 100.0;
    private static final long MIN_MONITOR_INTERVAL_MS = 100L;
    private static final double MIN_MAX_FLUCTUATION = 0.0;
    private static final double MAX_MAX_FLUCTUATION = 50.0;
    private static final int MIN_CLOUD_SERVERS = 0;

    // Instance fields
    private final int maxServerIdLength;
    private final int maxRetries;
    private final int serversPerPage;
    private final int timeoutSeconds;
    private final int progressAnimationSpeed;
    private final String successColor;
    private final String errorColor;
    private final String resetColor;
    private final String[] progressAnimation;
    private final double alertThreshold;
    private final long monitorIntervalMs;
    private final double maxFluctuation;
    private final String configFile;
    private final String[] args;
    private final boolean cloudEnabled;
    private final int cloudMinServers;
    private final int cloudMaxServers;

    // ANSI Color Enum
    public enum AnsiColor {
        SUCCESS("\u001B[32m"),
        ERROR("\u001B[31m"),
        RESET("\u001B[0m"),
        NONE("");

        private final String code;

        AnsiColor(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static AnsiColor fromString(String value, AnsiColor defaultColor) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return defaultColor;
            }
        }
    }

    // Main constructor
    public CliConfig(String[] args) {
        this(args, loadProperties(determineConfigFile(args)));
    }

    // Constructor for testing or custom properties
    public CliConfig(String[] args, Properties props) {
        this.args = args.clone(); // Store args defensively
        this.configFile = determineConfigFile(args);
        boolean isAnsiSupported = System.console() != null && !System.getProperty("os.name").contains("Windows");

        // Parse properties with range validation
        int maxIdLen = parseIntInRange(props, "maxServerIdLength", DEFAULT_MAX_SERVER_ID_LENGTH, MIN_MAX_SERVER_ID_LENGTH, Integer.MAX_VALUE);
        int retries = parseIntInRange(props, "maxRetries", DEFAULT_MAX_RETRIES, MIN_MAX_RETRIES, Integer.MAX_VALUE);
        int perPage = parseIntInRange(props, "serversPerPage", DEFAULT_SERVERS_PER_PAGE, MIN_SERVERS_PER_PAGE, Integer.MAX_VALUE);
        int timeout = parseIntInRange(props, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
        int animSpeed = parseIntInRange(props, "progressAnimationSpeed", DEFAULT_PROGRESS_ANIMATION_SPEED, MIN_PROGRESS_ANIMATION_SPEED, Integer.MAX_VALUE);
        double alertThresh = parseDoubleInRange(props, "alertThreshold", DEFAULT_ALERT_THRESHOLD, MIN_ALERT_THRESHOLD, MAX_ALERT_THRESHOLD);
        long monitorInterval = parseLongInRange(props, "monitorIntervalMs", DEFAULT_MONITOR_INTERVAL_MS, MIN_MONITOR_INTERVAL_MS, Long.MAX_VALUE);
        double maxFluct = parseDoubleInRange(props, "maxFluctuation", DEFAULT_MAX_FLUCTUATION, MIN_MAX_FLUCTUATION, MAX_MAX_FLUCTUATION);
        boolean cloudEnabledProp = Boolean.parseBoolean(props.getProperty("cloudEnabled", String.valueOf(DEFAULT_CLOUD_ENABLED)));
        int cloudMin = parseIntInRange(props, "cloudMinServers", DEFAULT_CLOUD_MIN_SERVERS, MIN_CLOUD_SERVERS, Integer.MAX_VALUE);
        int cloudMax = parseIntInRange(props, "cloudMaxServers", DEFAULT_CLOUD_MAX_SERVERS, cloudMin, Integer.MAX_VALUE);

        // Override with CLI arguments
        this.maxServerIdLength = getIntArg(args, "--max-server-id-length", maxIdLen);
        this.maxRetries = getIntArg(args, "--max-retries", retries);
        this.serversPerPage = getIntArg(args, "--servers-per-page", perPage);
        this.timeoutSeconds = getIntArg(args, "--timeout-seconds", timeout);
        this.progressAnimationSpeed = getIntArg(args, "--progress-animation-speed", animSpeed);
        this.alertThreshold = getDoubleArg(args, "--alert-threshold", alertThresh);
        this.monitorIntervalMs = getLongArg(args, "--monitor-interval-ms", monitorInterval);
        this.maxFluctuation = getDoubleArg(args, "--max-fluctuation", maxFluct);
        this.cloudEnabled = getBooleanArg(args, "--cloud-enabled", cloudEnabledProp);
        this.cloudMinServers = getIntArg(args, "--cloud-min-servers", cloudMin);
        this.cloudMaxServers = getIntArg(args, "--cloud-max-servers", cloudMax);

        // ANSI colors with enum
        this.successColor = isAnsiSupported
                ? AnsiColor.fromString(props.getProperty("successColor", "SUCCESS"), AnsiColor.SUCCESS).getCode()
                : AnsiColor.NONE.getCode();
        this.errorColor = isAnsiSupported
                ? AnsiColor.fromString(props.getProperty("errorColor", "ERROR"), AnsiColor.ERROR).getCode()
                : AnsiColor.NONE.getCode();
        this.resetColor = isAnsiSupported
                ? AnsiColor.fromString(props.getProperty("resetColor", "RESET"), AnsiColor.RESET).getCode()
                : AnsiColor.NONE.getCode();

        this.progressAnimation = new String[]{"-", "\\", "|", "/"};
    }

    // Helper methods for argument parsing
    private static int getIntArg(String[] args, String name, int fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid CLI arg value for {}, skipping: {}", name, e.getMessage());
                }
            }
        }
        return fallback;
    }

    private static double getDoubleArg(String[] args, String name, double fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    return Double.parseDouble(args[i + 1]);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid CLI arg value for {}, skipping: {}", name, e.getMessage());
                }
            }
        }
        return fallback;
    }

    private static long getLongArg(String[] args, String name, long fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    return Long.parseLong(args[i + 1]);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid CLI arg value for {}, skipping: {}", name, e.getMessage());
                }
            }
        }
        return fallback;
    }

    private static boolean getBooleanArg(String[] args, String name, boolean fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return Boolean.parseBoolean(args[i + 1]);
            }
        }
        return fallback;
    }

    // Property parsing with range validation
    private int parseIntInRange(Properties props, String key, int defaultValue, int min, int max) {
        try {
            int val = Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                throw new IllegalArgumentException(String.format("%s=%d out of range [%d-%d]", key, val, min, max));
            }
            return val;
        } catch (Exception e) {
            logger.warn("{} invalid or missing; using default {}. Reason: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    private double parseDoubleInRange(Properties props, String key, double defaultValue, double min, double max) {
        try {
            double val = Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                throw new IllegalArgumentException(String.format("%s=%.1f out of range [%.1f-%.1f]", key, val, min, max));
            }
            return val;
        } catch (Exception e) {
            logger.warn("{} invalid or missing; using default {}. Reason: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    private long parseLongInRange(Properties props, String key, long defaultValue, long min, long max) {
        try {
            long val = Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                throw new IllegalArgumentException(String.format("%s=%d out of range [%d-%d]", key, val, min, max));
            }
            return val;
        } catch (Exception e) {
            logger.warn("{} invalid or missing; using default {}. Reason: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    // Config file handling
    private static String determineConfigFile(String[] args) {
        String defaultConfig = "cli.config";
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultConfig;
    }

    private static Properties loadProperties(String configFile) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        } catch (Exception e) {
            logger.warn("Failed to load {}, using defaults: {}", configFile, e.getMessage());
        }
        return props;
    }

    // Getters
    public int getMaxServerIdLength() { return maxServerIdLength; }
    public int getMaxRetries() { return maxRetries; }
    public int getServersPerPage() { return serversPerPage; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getProgressAnimationSpeed() { return progressAnimationSpeed; }
    public String getSuccessColor() { return successColor; }
    public String getErrorColor() { return errorColor; }
    public String getResetColor() { return resetColor; }
    public String[] getProgressAnimation() { return progressAnimation.clone(); }
    public double getAlertThreshold() { return alertThreshold; }
    public long getMonitorIntervalMs() { return monitorIntervalMs; }
    public double getMaxFluctuation() { return maxFluctuation; }
    public String[] getArgs() { return args.clone(); }
    public boolean isCloudEnabled() { return cloudEnabled; }
    public int getCloudMinServers() { return cloudMinServers; }
    public int getCloudMaxServers() { return cloudMaxServers; }

    @Override
    public String toString() {
        return String.format("CliConfig{maxServerIdLength=%d, maxRetries=%d, serversPerPage=%d, " +
                "timeoutSeconds=%d, progressAnimationSpeed=%d, successColor='%s', errorColor='%s', " +
                "resetColor='%s', progressAnimation=%s, alertThreshold=%.1f, monitorIntervalMs=%d, " +
                "maxFluctuation=%.1f, cloudEnabled=%b, cloudMinServers=%d, cloudMaxServers=%d}",
                maxServerIdLength, maxRetries, serversPerPage, timeoutSeconds, progressAnimationSpeed,
                successColor, errorColor, resetColor, Arrays.toString(progressAnimation),
                alertThreshold, monitorIntervalMs, maxFluctuation, cloudEnabled, cloudMinServers, cloudMaxServers);
    }
}
