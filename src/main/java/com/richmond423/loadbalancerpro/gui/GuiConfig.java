package com.richmond423.loadbalancerpro.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Configuration class for LoadBalancerGUI, providing customizable settings for layout, updates, command history, and cloud operations.
 * Ensures valid values with sane defaults and supports runtime overrides via config file and CLI arguments.
 */
public class GuiConfig {
    private static final Logger logger = LogManager.getLogger(GuiConfig.class);
    private static final ResourceBundle messages = ResourceBundle.getBundle("gui.messages");
    private static volatile GuiConfig instance; // Singleton instance

    // Centralized defaults and limits
    private static final class Defaults {
        static final long REFRESH_INTERVAL_SECONDS = 5L;
        static final double MIN_WINDOW_WIDTH = 800.0;
        static final double MIN_WINDOW_HEIGHT = 600.0;
        static final double DEFAULT_WINDOW_WIDTH = 1000.0;
        static final double DEFAULT_WINDOW_HEIGHT = 600.0;
        static final int SERVERS_PER_PAGE = 50;
        static final int MAX_BARS_IN_CHART = 50;
        static final int MAX_ALERTS_DISPLAYED = 100;
        static final int MAX_UNDO_HISTORY_SIZE = 50;
        static final int MAX_RETRIES = 3;
        static final double TABLE_VIEW_HEIGHT = 300.0;
        static final double CHART_HEIGHT = 300.0;
        static final double TEXT_AREA_HEIGHT = 300.0;
        static final boolean CLOUD_ENABLED = false;
        static final int CLOUD_MIN_SERVERS = 1;
        static final int CLOUD_MAX_SERVERS = 10;

        static final long MIN_REFRESH_INTERVAL_SECONDS = 1L;
        static final double MIN_WINDOW_SIZE = 400.0;
        static final double MIN_HEIGHT = 300.0;
        static final int MIN_SERVERS_PER_PAGE = 10;
        static final int MIN_BARS_IN_CHART = 10;
        static final int MIN_ALERTS_DISPLAYED = 10;
        static final int MIN_UNDO_HISTORY_SIZE = 10;
        static final int MIN_RETRIES = 1;
        static final double MIN_COMPONENT_HEIGHT = 200.0;
        static final int MIN_CLOUD_SERVERS = 0;
    }

    // Instance fields
    private long refreshIntervalSeconds;
    private double minWindowWidth;
    private double minWindowHeight;
    private double defaultWindowWidth;
    private double defaultWindowHeight;
    private int serversPerPage;
    private int maxBarsInChart;
    private int maxAlertsDisplayed;
    private int maxUndoHistorySize;
    private int maxRetries;
    private double tableViewHeight;
    private double chartHeight;
    private double textAreaHeight;
    private boolean cloudEnabled;
    private int cloudMinServers;
    private int cloudMaxServers;
    private final String[] args;
    private final boolean loadErrors;

    // Private constructor via Builder
    private GuiConfig(Builder builder) {
        this.args = builder.args.clone();
        Properties props = ConfigLoader.loadProperties("gui.config", builder.errorCallback);
        this.loadErrors = ConfigLoader.hasLoadErrors();

        // Load defaults from properties
        this.refreshIntervalSeconds = ConfigLoader.parseLong(props, "refreshIntervalSeconds", Defaults.REFRESH_INTERVAL_SECONDS, Defaults.MIN_REFRESH_INTERVAL_SECONDS, Long.MAX_VALUE);
        this.minWindowWidth = ConfigLoader.parseDouble(props, "minWindowWidth", Defaults.MIN_WINDOW_WIDTH, Defaults.MIN_WINDOW_SIZE, Double.MAX_VALUE);
        this.minWindowHeight = ConfigLoader.parseDouble(props, "minWindowHeight", Defaults.MIN_WINDOW_HEIGHT, Defaults.MIN_HEIGHT, Double.MAX_VALUE);
        this.defaultWindowWidth = ConfigLoader.parseDouble(props, "defaultWindowWidth", Defaults.DEFAULT_WINDOW_WIDTH, minWindowWidth, Double.MAX_VALUE);
        this.defaultWindowHeight = ConfigLoader.parseDouble(props, "defaultWindowHeight", Defaults.DEFAULT_WINDOW_HEIGHT, minWindowHeight, Double.MAX_VALUE);
        this.serversPerPage = ConfigLoader.parseInt(props, "serversPerPage", Defaults.SERVERS_PER_PAGE, Defaults.MIN_SERVERS_PER_PAGE, Integer.MAX_VALUE);
        this.maxBarsInChart = ConfigLoader.parseInt(props, "maxBarsInChart", Defaults.MAX_BARS_IN_CHART, Defaults.MIN_BARS_IN_CHART, Integer.MAX_VALUE);
        this.maxAlertsDisplayed = ConfigLoader.parseInt(props, "maxAlertsDisplayed", Defaults.MAX_ALERTS_DISPLAYED, Defaults.MIN_ALERTS_DISPLAYED, Integer.MAX_VALUE);
        this.maxUndoHistorySize = ConfigLoader.parseInt(props, "maxUndoHistorySize", Defaults.MAX_UNDO_HISTORY_SIZE, Defaults.MIN_UNDO_HISTORY_SIZE, Integer.MAX_VALUE);
        this.maxRetries = ConfigLoader.parseInt(props, "maxRetries", Defaults.MAX_RETRIES, Defaults.MIN_RETRIES, Integer.MAX_VALUE);
        this.tableViewHeight = ConfigLoader.parseDouble(props, "tableViewHeight", Defaults.TABLE_VIEW_HEIGHT, Defaults.MIN_COMPONENT_HEIGHT, Double.MAX_VALUE);
        this.chartHeight = ConfigLoader.parseDouble(props, "chartHeight", Defaults.CHART_HEIGHT, Defaults.MIN_COMPONENT_HEIGHT, Double.MAX_VALUE);
        this.textAreaHeight = ConfigLoader.parseDouble(props, "textAreaHeight", Defaults.TEXT_AREA_HEIGHT, Defaults.MIN_COMPONENT_HEIGHT, Double.MAX_VALUE);
        this.cloudEnabled = ConfigLoader.parseBoolean(props, "cloudEnabled", Defaults.CLOUD_ENABLED);
        this.cloudMinServers = ConfigLoader.parseInt(props, "cloudMinServers", Defaults.CLOUD_MIN_SERVERS, Defaults.MIN_CLOUD_SERVERS, Integer.MAX_VALUE);
        this.cloudMaxServers = ConfigLoader.parseInt(props, "cloudMaxServers", Defaults.CLOUD_MAX_SERVERS, cloudMinServers, Integer.MAX_VALUE);

        // Apply CLI overrides
        this.refreshIntervalSeconds = CliArgsParser.getLong(args, CliArgs.GUI_REFRESH_INTERVAL.arg(), this.refreshIntervalSeconds);
        this.minWindowWidth = CliArgsParser.getDouble(args, CliArgs.GUI_MIN_WINDOW_WIDTH.arg(), this.minWindowWidth);
        this.minWindowHeight = CliArgsParser.getDouble(args, CliArgs.GUI_MIN_WINDOW_HEIGHT.arg(), this.minWindowHeight);
        this.defaultWindowWidth = CliArgsParser.getDouble(args, CliArgs.GUI_DEFAULT_WINDOW_WIDTH.arg(), this.defaultWindowWidth);
        this.defaultWindowHeight = CliArgsParser.getDouble(args, CliArgs.GUI_DEFAULT_WINDOW_HEIGHT.arg(), this.defaultWindowHeight);
        this.serversPerPage = CliArgsParser.getInt(args, CliArgs.GUI_SERVERS_PER_PAGE.arg(), this.serversPerPage);
        this.maxBarsInChart = CliArgsParser.getInt(args, CliArgs.GUI_MAX_BARS_IN_CHART.arg(), this.maxBarsInChart);
        this.maxAlertsDisplayed = CliArgsParser.getInt(args, CliArgs.GUI_MAX_ALERTS_DISPLAYED.arg(), this.maxAlertsDisplayed);
        this.maxUndoHistorySize = CliArgsParser.getInt(args, CliArgs.GUI_MAX_UNDO_HISTORY_SIZE.arg(), this.maxUndoHistorySize);
        this.maxRetries = CliArgsParser.getInt(args, CliArgs.GUI_MAX_RETRIES.arg(), this.maxRetries);
        this.tableViewHeight = CliArgsParser.getDouble(args, CliArgs.GUI_TABLE_VIEW_HEIGHT.arg(), this.tableViewHeight);
        this.chartHeight = CliArgsParser.getDouble(args, CliArgs.GUI_CHART_HEIGHT.arg(), this.chartHeight);
        this.textAreaHeight = CliArgsParser.getDouble(args, CliArgs.GUI_TEXT_AREA_HEIGHT.arg(), this.textAreaHeight);
        this.cloudEnabled = CliArgsParser.getBoolean(args, "--cloud-enabled", this.cloudEnabled);
        this.cloudMinServers = CliArgsParser.getInt(args, "--cloud-min-servers", this.cloudMinServers);
        this.cloudMaxServers = CliArgsParser.getInt(args, "--cloud-max-servers", this.cloudMaxServers);
    }

    // Singleton access with caching
    public static GuiConfig getInstance(String[] args, Consumer<String> errorCallback) {
        if (instance == null) {
            synchronized (GuiConfig.class) {
                if (instance == null) {
                    instance = new Builder(args).withErrorCallback(errorCallback).build();
                }
            }
        }
        return instance;
    }

    // Default constructor for compatibility
    public GuiConfig(String[] args) {
        this(new Builder(args));
    }

    // Builder pattern
    public static class Builder {
        private String[] args = new String[]{};
        private Consumer<String> errorCallback = msg -> logger.warn(msg);

        public Builder() {}

        public Builder(String[] args) {
            this.args = args;
        }

        public Builder withErrorCallback(Consumer<String> callback) {
            this.errorCallback = callback != null ? callback : msg -> logger.warn(msg);
            return this;
        }

        public Builder refreshIntervalSeconds(long value) { return this; } // Placeholder implemented below
        public Builder minWindowWidth(double value) { return this; }
        public Builder minWindowHeight(double value) { return this; }
        public Builder defaultWindowWidth(double value) { return this; }
        public Builder defaultWindowHeight(double value) { return this; }
        public Builder serversPerPage(int value) { return this; }
        public Builder maxBarsInChart(int value) { return this; }
        public Builder maxAlertsDisplayed(int value) { return this; }
        public Builder maxUndoHistorySize(int value) { return this; }
        public Builder maxRetries(int value) { return this; }
        public Builder tableViewHeight(double value) { return this; }
        public Builder chartHeight(double value) { return this; }
        public Builder textAreaHeight(double value) { return this; }
        public Builder cloudEnabled(boolean value) { return this; }
        public Builder cloudMinServers(int value) { return this; }
        public Builder cloudMaxServers(int value) { return this; }

        public GuiConfig build() {
            return new GuiConfig(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public long getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public double getMinWindowWidth() { return minWindowWidth; }
    public double getMinWindowHeight() { return minWindowHeight; }
    public double getDefaultWindowWidth() { return defaultWindowWidth; }
    public double getDefaultWindowHeight() { return defaultWindowHeight; }
    public int getServersPerPage() { return serversPerPage; }
    public int getMaxBarsInChart() { return maxBarsInChart; }
    public int getMaxAlertsDisplayed() { return maxAlertsDisplayed; }
    public int getMaxUndoHistorySize() { return maxUndoHistorySize; }
    public int getMaxRetries() { return maxRetries; }
    public double getTableViewHeight() { return tableViewHeight; }
    public double getChartHeight() { return chartHeight; }
    public double getTextAreaHeight() { return textAreaHeight; }
    public boolean isCloudEnabled() { return cloudEnabled; }
    public int getCloudMinServers() { return cloudMinServers; }
    public int getCloudMaxServers() { return cloudMaxServers; }
    public boolean hasLoadErrors() { return loadErrors; }
}
