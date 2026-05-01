package gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import core.CloudManager;
import core.CloudConfig;
import core.LoadBalancer;
import core.Server;
import core.ServerType;
import util.Utils;
import javafx.animation.FillTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Main GUI class for LoadBalancerPro, a real-time server management interface.
 * Features live animations, strategy toggles, theme switching, and persistent settings with cloud integration.
 */
public class LoadBalancerGUI extends Application {
    private static final Logger logger = LogManager.getLogger(LoadBalancerGUI.class);
    private static final String ERROR_TITLE = "Error";
    private static final String SUCCESS_COLOR = "-fx-background-color: lightgreen;";
    private static final String ERROR_COLOR = "-fx-background-color: lightcoral;";
    private static final String NO_SERVERS_MESSAGE = "No servers available.";
    private static final String NO_ALERTS_MESSAGE = "No alerts available.";
    private static final String NO_DISTRIBUTION_MESSAGE = "No distribution data available.";
    private static final String COMMAND_HISTORY_FILE = "command_history.json";
    private static final String AWS_ACCESS_KEY_PROPERTY = "aws.accessKeyId";
    private static final String AWS_SECRET_KEY_PROPERTY = "aws.secretAccessKey";
    private static final String AWS_REGION_PROPERTY = "aws.region";
    private static final String LAUNCH_TEMPLATE_PROPERTY = "cloud.launchTemplateId";
    private static final String SUBNET_ID_PROPERTY = "cloud.subnetId";
    private static final String AWS_ACCESS_KEY_ENV = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_KEY_ENV = "AWS_SECRET_ACCESS_KEY";
    private static final String AWS_REGION_ENV = "AWS_REGION";
    private static final String AWS_DEFAULT_REGION_ENV = "AWS_DEFAULT_REGION";
    private static final String LAUNCH_TEMPLATE_ENV = "CLOUD_LAUNCH_TEMPLATE_ID";
    private static final String SUBNET_ID_ENV = "CLOUD_SUBNET_ID";
    private static final Preferences prefs = Preferences.userNodeForPackage(LoadBalancerGUI.class);

    private LoadBalancer balancer;
    private CloudManager cloudManager;
    private TableView<ServerTableRow> serverTable;
    private TextArea logArea;
    private TextArea alertArea;
    private ListView<String> commandHistoryView;
    private ObservableList<ServerTableRow> serverData;
    private ScheduledExecutorService executorService;
    private AtomicBoolean isUpdating = new AtomicBoolean(true);
    private ProgressIndicator progressIndicator;
    private ToggleButton updateToggleButton;
    private ToggleButton themeToggleButton;
    private Button undoButton;
    private Map<String, ServerTableRow> serverRowMap;
    private GuiConfig config;
    private Pagination pagination;
    private Pagination alertPagination;
    private ChoiceBox<String> typeFilter;
    private ChoiceBox<String> healthFilter;
    private ChoiceBox<String> strategyChoice;
    private TextField searchField;
    private List<ServerTableRow> allServerData;
    private BarChart<String, Number> loadChart;
    private Stack<Command> commandHistory;
    private BlockingQueue<Command> commandQueue;
    private List<String> allAlerts;
    private boolean isDarkMode;

    @Override
    public void start(Stage stage) {
        logger.info("Starting LoadBalancerGUI.");
        config = GuiConfig.getInstance(getParameters().getRaw().toArray(new String[0]), this::handleConfigError);
        balancer = new LoadBalancer();
        commandHistory = loadCommandHistory();
        if (config.isCloudEnabled()) {
            initializeCloudManager();
        } else {
            logger.info("Cloud integration disabled via GuiConfig.");
        }
        serverData = FXCollections.observableArrayList();
        allServerData = new ArrayList<>();
        serverRowMap = new HashMap<>();
        commandQueue = new LinkedBlockingQueue<>();
        allAlerts = new ArrayList<>();
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        commandHistoryView = new ListView<>(FXCollections.observableArrayList());
        isDarkMode = prefs.getBoolean("darkMode", false);

        VBox root = setupLayout();
        setupRealTimeUpdates();
        setupCommandQueueProcessor();
        displayStatus();

        Scene scene = new Scene(root, config.getDefaultWindowWidth(), config.getDefaultWindowHeight());
        scene.getStylesheets().add(getClass().getResource("/resources/" + (isDarkMode ? "dark.css" : "light.css")).toExternalForm());
        stage.setTitle("LoadBalancerPro - GUI");
        stage.setMinWidth(config.getMinWindowWidth());
        stage.setMinHeight(config.getMinWindowHeight());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        if (config.hasLoadErrors()) {
            alert(ERROR_TITLE, "GUI configuration loaded with errors. Check logs for details.");
        }
    }

    private void initializeCloudManager() {
        try {
            Properties systemProperties = System.getProperties();
            Map<String, String> environment = System.getenv();
            Optional<String> accessKey = firstConfigured(systemProperties, environment,
                    AWS_ACCESS_KEY_PROPERTY, AWS_ACCESS_KEY_ENV);
            Optional<String> secretKey = firstConfigured(systemProperties, environment,
                    AWS_SECRET_KEY_PROPERTY, AWS_SECRET_KEY_ENV);
            Optional<String> region = firstConfigured(systemProperties, environment,
                    AWS_REGION_PROPERTY, AWS_REGION_ENV, AWS_DEFAULT_REGION_ENV);
            Optional<String> launchTemplateId = firstConfigured(systemProperties, environment,
                    LAUNCH_TEMPLATE_PROPERTY, LAUNCH_TEMPLATE_ENV);
            Optional<String> subnetId = firstConfigured(systemProperties, environment,
                    SUBNET_ID_PROPERTY, SUBNET_ID_ENV);
            boolean liveModeRequested = firstConfigured(systemProperties, environment,
                    CloudConfig.LIVE_MODE_PROPERTY, "CLOUD_LIVE_MODE")
                    .map(Boolean::parseBoolean)
                    .orElse(false);

            List<String> missing = new ArrayList<>();
            if (accessKey.isEmpty()) missing.add(AWS_ACCESS_KEY_PROPERTY + "/" + AWS_ACCESS_KEY_ENV);
            if (secretKey.isEmpty()) missing.add(AWS_SECRET_KEY_PROPERTY + "/" + AWS_SECRET_KEY_ENV);
            if (region.isEmpty()) missing.add(AWS_REGION_PROPERTY + "/" + AWS_REGION_ENV + " or " + AWS_DEFAULT_REGION_ENV);
            if (liveModeRequested && launchTemplateId.isEmpty()) missing.add(LAUNCH_TEMPLATE_PROPERTY + "/" + LAUNCH_TEMPLATE_ENV);
            if (liveModeRequested && subnetId.isEmpty()) missing.add(SUBNET_ID_PROPERTY + "/" + SUBNET_ID_ENV);
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("missing required configuration: " + String.join(", ", missing));
            }

            Properties cloudProperties = new Properties();
            copyCloudFlag(systemProperties, environment, cloudProperties, CloudConfig.LIVE_MODE_PROPERTY, "CLOUD_LIVE_MODE");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.ALLOW_RESOURCE_DELETION_PROPERTY, "CLOUD_ALLOW_RESOURCE_DELETION");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.CONFIRM_RESOURCE_OWNERSHIP_PROPERTY, "CLOUD_CONFIRM_RESOURCE_OWNERSHIP");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "CLOUD_MAX_DESIRED_CAPACITY");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.MAX_SCALE_STEP_PROPERTY, "CLOUD_MAX_SCALE_STEP");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "CLOUD_ALLOW_LIVE_MUTATION");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.OPERATOR_INTENT_PROPERTY, "CLOUD_OPERATOR_INTENT");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.ALLOW_AUTONOMOUS_SCALE_UP_PROPERTY, "CLOUD_ALLOW_AUTONOMOUS_SCALE_UP");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.ENVIRONMENT_PROPERTY, "CLOUD_ENVIRONMENT");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "CLOUD_RESOURCE_NAME_PREFIX");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, "CLOUD_ALLOWED_AWS_ACCOUNT_IDS");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.CURRENT_AWS_ACCOUNT_ID_PROPERTY, "CLOUD_CURRENT_AWS_ACCOUNT_ID");
            copyCloudFlag(systemProperties, environment, cloudProperties,
                    CloudConfig.ALLOWED_REGIONS_PROPERTY, "CLOUD_ALLOWED_REGIONS");

            CloudConfig cloudConfig = new CloudConfig(
                    accessKey.orElseThrow(),
                    secretKey.orElseThrow(),
                    region.orElseThrow(),
                    launchTemplateId.orElse(""),
                    subnetId.orElse(""),
                    cloudProperties);
            cloudManager = new CloudManager(balancer, cloudConfig, commandHistory::add);
            logger.info("CloudManager initialized in {} mode with min={} and max={} servers.",
                    cloudConfig.isLiveMode() ? "live" : "dry-run",
                    config.getCloudMinServers(),
                    config.getCloudMaxServers());
            if (cloudConfig.isLiveMode()) {
                logger.warn("Live cloud mode is enabled because {}=true was provided.", CloudConfig.LIVE_MODE_PROPERTY);
            }
        } catch (IllegalArgumentException e) {
            cloudManager = null;
            logger.warn("Cloud integration requested but disabled: {}. No AWS calls will be made.", e.getMessage());
        }
    }

    private static void copyCloudFlag(Properties systemProperties, Map<String, String> environment,
                                      Properties target, String propertyName, String environmentName) {
        firstConfigured(systemProperties, environment, propertyName, environmentName)
                .ifPresent(value -> target.setProperty(propertyName, value));
    }

    private static Optional<String> firstConfigured(Properties systemProperties, Map<String, String> environment,
                                                   String propertyName, String... environmentNames) {
        String propertyValue = systemProperties.getProperty(propertyName);
        if (isConfigured(propertyValue)) {
            return Optional.of(propertyValue.trim());
        }
        for (String environmentName : environmentNames) {
            String value = environment.get(environmentName);
            if (isConfigured(value)) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private VBox setupLayout() {
        serverTable = createTableView();
        HBox filterBox = createFilterBox();
        pagination = createPagination(serverTable, this::updateTableData);
        VBox statusPane = new VBox(5, filterBox, pagination);
        statusPane.setPrefHeight(config.getTableViewHeight());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(config.getTextAreaHeight());

        alertArea = new TextArea();
        alertArea.setEditable(false);
        alertArea.setPrefHeight(config.getTextAreaHeight());
        alertPagination = createPagination(alertArea, this::updateAlertData);

        VBox alertPane = new VBox(5, alertPagination);
        alertPane.setPrefHeight(config.getTextAreaHeight());

        commandHistoryView.setPrefHeight(config.getTextAreaHeight());
        commandHistoryView.setPlaceholder(new Label("No command history yet."));
        VBox historyPane = new VBox(5, new Label("Command History"), commandHistoryView);

        loadChart = createLoadChart();
        TabPane tabPane = createTabPane(statusPane, alertPane, historyPane);
        FlowPane controls = createControls();

        VBox root = new VBox(10, controls, progressIndicator, tabPane);
        root.setPadding(new Insets(10));
        return root;
    }

    private TableView<ServerTableRow> createTableView() {
        TableView<ServerTableRow> table = new TableView<>();
        table.setPrefHeight(config.getTableViewHeight());
        table.setSortPolicy(param -> true);
        table.setPlaceholder(new Label(NO_SERVERS_MESSAGE));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ServerTableRow selectedRow = table.getSelectionModel().getSelectedItem();
                if (selectedRow != null) {
                    showServerDetailsDialog(selectedRow);
                }
            }
        });

        table.getColumns().addAll(
            createColumn("Server ID", "serverId", "Unique server identifier"),
            createColumn("Type", "serverType", "Server type (CLOUD/ONSITE)"),
            createBarColumn("CPU (%)", "cpuUsage", "CPU usage percentage"),
            createBarColumn("Memory (%)", "memoryUsage", "Memory usage percentage"),
            createBarColumn("Disk (%)", "diskUsage", "Disk usage percentage"),
            createColumn("Capacity", "capacity", "Server capacity (GB)"),
            createColumn("Load Score", "loadScore", "Computed load score"),
            createHealthColumn("Healthy", "healthy", "Server health status")
        );
        return table;
    }

    private <T> TableColumn<ServerTableRow, T> createColumn(String title, String property, String tooltip) {
        TableColumn<ServerTableRow, T> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        return col;
    }

    private TableColumn<ServerTableRow, Double> createBarColumn(String title, String property, String tooltip) {
        TableColumn<ServerTableRow, Double> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setCellFactory(col1 -> new BarCell());
        return col;
    }

    private TableColumn<ServerTableRow, Boolean> createHealthColumn(String title, String property, String tooltip) {
        TableColumn<ServerTableRow, Boolean> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setCellFactory(column -> new TableCell<ServerTableRow, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item ? "Yes" : "No");
                    setStyle(item ? SUCCESS_COLOR : ERROR_COLOR);
                }
            }
        });
        return col;
    }

    private static class BarCell extends TableCell<ServerTableRow, Double> {
        private final Rectangle bar = new Rectangle(0, 10);

        BarCell() {
            bar.setHeight(10);
            setGraphic(bar);
            setText(null);
        }

        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                bar.setWidth(0);
            } else {
                double maxWidth = 100;
                double newWidth = (item / 100.0) * maxWidth;
                FillTransition ft = new FillTransition(Duration.millis(500), bar);
                ft.setFromValue(Color.BLUE);
                ft.setToValue(item > 80 ? Color.RED : Color.GREEN);
                bar.setWidth(newWidth);
                ft.play();
                setTooltip(new Tooltip(String.format("%.2f%%", item)));
            }
        }
    }

    private HBox createFilterBox() {
        typeFilter = new ChoiceBox<>(FXCollections.observableArrayList("All", "CLOUD", "ONSITE"));
        typeFilter.setValue("All");
        typeFilter.setOnAction(e -> updateTableData(pagination.getCurrentPageIndex()));

        healthFilter = new ChoiceBox<>(FXCollections.observableArrayList("All", "Healthy", "Unhealthy"));
        healthFilter.setValue("All");
        healthFilter.setOnAction(e -> updateTableData(pagination.getCurrentPageIndex()));

        searchField = new TextField();
        searchField.setPromptText("Search by Server ID");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateTableData(pagination.getCurrentPageIndex()));

        strategyChoice = new ChoiceBox<>(FXCollections.observableArrayList("Round Robin", "Least Loaded"));
        strategyChoice.setValue("Round Robin");
        strategyChoice.setOnAction(e -> {
            balancer.setStrategy(strategyChoice.getValue().equals("Round Robin") ?
                LoadBalancer.Strategy.ROUND_ROBIN : LoadBalancer.Strategy.LEAST_LOADED);
            log("Load balancing strategy set to " + strategyChoice.getValue());
        });

        return new HBox(10, new Label("Filter by Type:"), typeFilter,
                        new Label("Filter by Health:"), healthFilter,
                        new Label("Search:"), searchField,
                        new Label("Strategy:"), strategyChoice);
    }

    private Pagination createPagination(Control content, PaginationCallback callback) {
        Pagination pag = new Pagination();
        pag.setPageCount(1);
        pag.setPageFactory(pageIndex -> {
            callback.update(pageIndex);
            return content;
        });
        return pag;
    }

    @FunctionalInterface
    private interface PaginationCallback {
        void update(int pageIndex);
    }

    private TabPane createTabPane(VBox statusPane, VBox alertPane, VBox historyPane) {
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
            new Tab("Server Status", statusPane),
            new Tab("Logs", logArea),
            new Tab("Alerts", alertPane),
            new Tab("History", historyPane),
            new Tab("Load Distribution", loadChart)
        );
        tabPane.getTabs().forEach(tab -> tab.setClosable(false));
        return tabPane;
    }

    private BarChart<String, Number> createLoadChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Server ID");
        xAxis.setTickLabelRotation(45);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Load Score");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Load Distribution");
        chart.setPrefHeight(config.getChartHeight());
        chart.setLegendVisible(false);
        return chart;
    }

    private FlowPane createControls() {
        Button addServerButton = new Button("Add Server");
        Button importButton = new Button("Import Logs");
        Button balanceButton = new Button("Balance Load");
        Button reportButton = new Button("Generate Report");
        Button statusButton = new Button("Refresh Status");
        Button healthButton = new Button("Check Health");
        Button failButton = new Button("Fail Server");
        undoButton = new Button("Undo");
        Button initCloudButton = new Button("Initialize Cloud");
        Button scaleCloudButton = new Button("Scale Cloud");
        updateToggleButton = new ToggleButton("Pause Updates");
        updateToggleButton.setSelected(false);
        updateToggleButton.setOnAction(e -> {
            isUpdating.set(!updateToggleButton.isSelected());
            updateToggleButton.setText(isUpdating.get() ? "Pause Updates" : "Resume Updates");
            log(isUpdating.get() ? "Real-time updates resumed." : "Real-time updates paused.");
        });
        themeToggleButton = new ToggleButton(isDarkMode ? "Light Mode" : "Dark Mode");
        themeToggleButton.setOnAction(e -> toggleTheme());

        initCloudButton.setDisable(!config.isCloudEnabled() || cloudManager == null);
        scaleCloudButton.setDisable(!config.isCloudEnabled() || cloudManager == null);

        FlowPane controls = new FlowPane(10, 8, addServerButton, importButton, balanceButton, reportButton,
                                         statusButton, healthButton, failButton, undoButton, initCloudButton,
                                         scaleCloudButton, updateToggleButton, themeToggleButton);

        addServerButton.setOnAction(e -> queueCommand(new AddServerCommand(this, balancer)));
        importButton.setOnAction(e -> queueCommand(this::importLogs));
        balanceButton.setOnAction(e -> queueCommand(new BalanceLoadCommand(this, balancer)));
        reportButton.setOnAction(e -> queueCommand(this::generateReport));
        statusButton.setOnAction(e -> displayStatus());
        healthButton.setOnAction(e -> queueCommand(this::checkHealth));
        failButton.setOnAction(e -> queueCommand(new FailServerCommand(this, balancer)));
        undoButton.setOnAction(e -> queueCommand(this::undoLastAction));
        initCloudButton.setOnAction(e -> queueCommand(new InitCloudCommand(this, cloudManager)));
        scaleCloudButton.setOnAction(e -> queueCommand(new ScaleCloudCommand(this, cloudManager)));
        updateUndoButtonState();

        return controls;
    }

    // Command DTO for serialization
    private static class CommandDto {
        String commandType;
        Map<String, Object> properties;

        CommandDto(String commandType, Map<String, Object> properties) {
            this.commandType = commandType;
            this.properties = properties;
        }

        static CommandDto fromCommand(Command command) {
            Map<String, Object> props = new HashMap<>();
            props.put("id", command.getId());
            props.put("description", command.getDescription());
            props.put("status", command.getStatus().toString());
            if (command instanceof AddServerCommand && ((AddServerCommand) command).server != null) {
                props.put("serverId", ((AddServerCommand) command).server.getServerId());
            } else if (command instanceof FailServerCommand && ((FailServerCommand) command).serverId != null) {
                props.put("serverId", ((FailServerCommand) command).serverId);
            } else if (command instanceof InitCloudCommand) {
                props.put("addedServers", ((InitCloudCommand) command).addedServers.stream()
                    .map(Server::getServerId).collect(Collectors.toList()));
            } else if (command instanceof ScaleCloudCommand) {
                props.put("adjust", ((ScaleCloudCommand) command).adjust);
                props.put("addedServers", ((ScaleCloudCommand) command).addedServers.stream()
                    .map(Server::getServerId).collect(Collectors.toList()));
                props.put("removedServerIds", ((ScaleCloudCommand) command).removedServerIds);
            }
            return new CommandDto(command.getClass().getSimpleName(), props);
        }

        Command toCommand(LoadBalancerGUI gui, LoadBalancer balancer, CloudManager cloudManager) {
            switch (commandType) {
                case "AddServerCommand":
                    return new AddServerCommand(gui, balancer) {
                        { server = balancer.getServer((String) properties.get("serverId")); }
                    };
                case "BalanceLoadCommand":
                    return new BalanceLoadCommand(gui, balancer);
                case "FailServerCommand":
                    return new FailServerCommand(gui, balancer) {
                        { serverId = (String) properties.get("serverId"); }
                    };
                case "InitCloudCommand":
                    return new InitCloudCommand(gui, cloudManager) {
                        { addedServers = ((List<String>) properties.get("addedServers")).stream()
                            .map(id -> balancer.getServer(id)).filter(Objects::nonNull).collect(Collectors.toList()); }
                    };
                case "ScaleCloudCommand":
                    return new ScaleCloudCommand(gui, cloudManager) {
                        { 
                            adjust = ((Number) properties.get("adjust")).intValue();
                            addedServers = ((List<String>) properties.get("addedServers")).stream()
                                .map(id -> balancer.getServer(id)).filter(Objects::nonNull).collect(Collectors.toList());
                            removedServerIds = (List<String>) properties.get("removedServerIds");
                        }
                    };
                default:
                    logger.warn("Unknown command type: {}", commandType);
                    return null;
            }
        }
    }

    // Command implementations
    private static class AddServerCommand implements Command {
        private final LoadBalancerGUI gui;
        private final LoadBalancer balancer;
        protected Server server;
        private final String id = "AddServer-" + System.nanoTime();
        private Status status = Status.PENDING;

        AddServerCommand(LoadBalancerGUI gui, LoadBalancer balancer) {
            this.gui = gui;
            this.balancer = balancer;
        }

        @Override
        public void execute() {
            gui.addServerDialog(server -> {
                synchronized (balancer) {
                    this.server = server;
                    balancer.addServer(server);
                    gui.log("Server " + server.getServerId() + " added.");
                    status = Status.COMPLETED;
                }
            });
        }

        @Override
        public void undo() {
            if (server != null) {
                synchronized (balancer) {
                    balancer.removeServer(server.getServerId());
                    gui.log("Undo: Removed server " + server.getServerId());
                }
            }
        }

        @Override
        public boolean canUndo() { return true; }
        @Override
        public String getDescription() { return "Add Server " + (server != null ? server.getServerId() : ""); }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    private static class BalanceLoadCommand implements Command {
        private final LoadBalancerGUI gui;
        private final LoadBalancer balancer;
        private final String id = "BalanceLoad-" + System.nanoTime();
        private Status status = Status.PENDING;

        BalanceLoadCommand(LoadBalancerGUI gui, LoadBalancer balancer) {
            this.gui = gui;
            this.balancer = balancer;
        }

        @Override
        public void execute() {
            gui.balanceLoadDialog(() -> {
                synchronized (balancer) {
                    balancer.rebalanceExistingLoad();
                    gui.log("Load balanced across servers.");
                    status = Status.COMPLETED;
                }
            });
        }

        @Override
        public void undo() { logger.warn("Undo not supported for load balancing."); }
        @Override
        public boolean canUndo() { return false; }
        @Override
        public String getDescription() { return "Balanced server loads"; }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    private static class FailServerCommand implements Command {
        private final LoadBalancerGUI gui;
        private final LoadBalancer balancer;
        protected String serverId;
        private final String id = "FailServer-" + System.nanoTime();
        private Status status = Status.PENDING;

        FailServerCommand(LoadBalancerGUI gui, LoadBalancer balancer) {
            this.gui = gui;
            this.balancer = balancer;
        }

        @Override
        public void execute() {
            gui.failServerDialog(sId -> {
                synchronized (balancer) {
                    this.serverId = sId;
                    Server server = balancer.getServer(sId);
                    if (server != null) {
                        server.setHealthy(false);
                        gui.log("Server " + sId + " failed.");
                        status = Status.COMPLETED;
                    }
                }
            });
        }

        @Override
        public void undo() {
            if (serverId != null) {
                synchronized (balancer) {
                    Server server = balancer.getServer(serverId);
                    if (server != null) {
                        server.setHealthy(true);
                        gui.log("Undo: Restored server " + serverId);
                    }
                }
            }
        }

        @Override
        public boolean canUndo() { return true; }
        @Override
        public String getDescription() { return "Fail Server " + (serverId != null ? serverId : ""); }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    private static class InitCloudCommand implements Command {
        private final LoadBalancerGUI gui;
        private final CloudManager cloudManager;
        protected List<Server> addedServers = new ArrayList<>();
        private final String id = "InitCloud-" + System.nanoTime();
        private Status status = Status.PENDING;

        InitCloudCommand(LoadBalancerGUI gui, CloudManager cloudManager) {
            this.gui = gui;
            this.cloudManager = cloudManager;
        }

        @Override
        public void execute() {
            gui.initCloudDialog(count -> {
                try {
                    int minServers = Math.max(count, gui.config.getCloudMinServers());
                    int maxServers = Math.max(count + 2, gui.config.getCloudMaxServers());
                    cloudManager.initializeCloudServers(minServers, maxServers);
                    synchronized (cloudManager.getBalancer()) {
                        addedServers = cloudManager.getBalancer().getServers().stream()
                            .filter(s -> s.getServerType() == ServerType.CLOUD)
                            .collect(Collectors.toList());
                        gui.log("Initialized " + count + " cloud servers via CloudManager (min=" + minServers + ", max=" + maxServers + ").");
                        status = Status.COMPLETED;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    gui.log("Cloud initialization interrupted: " + e.getMessage());
                    status = Status.FAILED;
                }
            });
        }

        @Override
        public void undo() {
            synchronized (cloudManager.getBalancer()) {
                for (Server server : addedServers) {
                    cloudManager.getBalancer().removeServer(server.getServerId());
                }
                gui.log("Undo: Removed " + addedServers.size() + " cloud servers.");
            }
        }

        @Override
        public boolean canUndo() { return true; }
        @Override
        public String getDescription() { return "Initialized " + addedServers.size() + " cloud servers"; }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    private static class ScaleCloudCommand implements Command {
        private final LoadBalancerGUI gui;
        private final CloudManager cloudManager;
        protected int adjust;
        protected List<Server> addedServers = new ArrayList<>();
        protected List<String> removedServerIds = new ArrayList<>();
        private final String id = "ScaleCloud-" + System.nanoTime();
        private Status status = Status.PENDING;
        private final CompletableFuture<Boolean> scaleFuture = new CompletableFuture<>();

        ScaleCloudCommand(LoadBalancerGUI gui, CloudManager cloudManager) {
            this.gui = gui;
            this.cloudManager = cloudManager;
        }

        @Override
        public void execute() {
            gui.scaleCloudDialog(adj -> {
                this.adjust = adj;
                int currentCapacity = cloudManager.getCurrentCapacity();
                int newCapacity = Math.max(gui.config.getCloudMinServers(), 
                                         Math.min(gui.config.getCloudMaxServers(), currentCapacity + adjust));
                cloudManager.scaleServersAsync(newCapacity, success -> {
                    synchronized (cloudManager.getBalancer()) {
                        if (adjust > 0) {
                            addedServers = cloudManager.getBalancer().getServers().stream()
                                .filter(s -> s.getServerType() == ServerType.CLOUD)
                                .skip(currentCapacity)
                                .limit(adjust)
                                .collect(Collectors.toList());
                            gui.log("Scaled up " + adjust + " cloud servers: " + success);
                        } else if (adjust < 0) {
                            int toRemove = Math.min(-adjust, (int) cloudManager.getBalancer().getServers().stream()
                                .filter(s -> s.getServerType() == ServerType.CLOUD).count());
                            removedServerIds = cloudManager.getBalancer().getServers().stream()
                                .filter(s -> s.getServerType() == ServerType.CLOUD)
                                .limit(toRemove)
                                .map(Server::getServerId)
                                .collect(Collectors.toList());
                            gui.log("Scaled down " + toRemove + " cloud servers: " + success);
                        }
                        status = success ? Status.COMPLETED : Status.FAILED;
                        scaleFuture.complete(success);
                    }
                });
                try {
                    scaleFuture.get(10, TimeUnit.SECONDS); // Wait for async completion with timeout
                } catch (Exception e) {
                    gui.log("Scaling operation timed out or failed: " + e.getMessage());
                    status = Status.FAILED;
                }
            });
        }

        @Override
        public void undo() {
            synchronized (cloudManager.getBalancer()) {
                if (adjust > 0) {
                    for (Server server : addedServers) {
                        cloudManager.getBalancer().removeServer(server.getServerId());
                    }
                    gui.log("Undo: Removed " + addedServers.size() + " cloud servers.");
                } else if (adjust < 0) {
                    for (String serverId : removedServerIds) {
                        cloudManager.getBalancer().addServer(new Server(serverId, ServerType.CLOUD, 0.0, 0.0, 0.0, 1000.0));
                    }
                    gui.log("Undo: Restored " + removedServerIds.size() + " cloud servers.");
                }
            }
        }

        @Override
        public boolean canUndo() { return true; }
        @Override
        public String getDescription() { return "Scaled cloud servers by " + adjust; }
        @Override
        public String getId() { return id; }
        @Override
        public Status getStatus() { return status; }
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        Scene scene = serverTable.getScene();
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource("/resources/" + (isDarkMode ? "dark.css" : "light.css")).toExternalForm());
        themeToggleButton.setText(isDarkMode ? "Light Mode" : "Dark Mode");
        prefs.putBoolean("darkMode", isDarkMode);
        log("Switched to " + (isDarkMode ? "dark" : "light") + " mode.");
    }

    private void setupRealTimeUpdates() {
        executorService = Executors.newScheduledThreadPool(2);
        executorService.scheduleAtFixedRate(() -> {
            if (isUpdating.get()) {
                displayStatus();
                Platform.runLater(this::updateUndoButtonState);
            }
        }, 0, config.getRefreshIntervalSeconds(), TimeUnit.SECONDS);
    }

    private void setupCommandQueueProcessor() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Command command = commandQueue.take();
                    Platform.runLater(() -> progressIndicator.setVisible(true));
                    command.executeAsync().thenRun(() -> {
                        synchronized (commandHistory) {
                            commandHistory.push(command);
                            if (commandHistory.size() > config.getMaxUndoHistorySize()) {
                                commandHistory.remove(0);
                            }
                        }
                        Platform.runLater(() -> {
                            progressIndicator.setVisible(false);
                            updateUndoButtonState();
                            updateCommandHistoryView();
                        });
                    }).exceptionally(throwable -> {
                        Platform.runLater(() -> {
                            progressIndicator.setVisible(false);
                            alert(ERROR_TITLE, "Command failed: " + throwable.getMessage());
                        });
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void shutdown() {
        logger.info("=== Shutting down LoadBalancerGUI ===");
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Executor service did not terminate gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Shutdown interrupted: {}", e.getMessage(), e);
            }
        }
        synchronized (balancer) {
            serverRowMap.values().forEach(ServerTableRow::close);
        }
        try {
            balancer.shutdown();
            if (cloudManager != null) {
                cloudManager.shutdown();
            }
        } catch (Exception e) {
            logger.error("Balancer or CloudManager shutdown interrupted: {}", e.getMessage(), e);
        }
        saveCommandHistory();
    }

    private void queueCommand(Command command) {
        commandQueue.offer(command);
    }

    private void queueCommand(Runnable runnable) {
        queueCommand(new RunnableCommand(runnable));
    }

    private static class RunnableCommand implements Command {
        private final Runnable runnable;
        private final String id = "Runnable-" + System.nanoTime();
        private Status status = Status.PENDING;

        RunnableCommand(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void execute() {
            runnable.run();
            status = Status.COMPLETED;
        }

        @Override
        public void undo() {}

        @Override
        public boolean canUndo() { return false; }

        @Override
        public String getDescription() { return "Runnable command"; }

        @Override
        public String getId() { return id; }

        @Override
        public Status getStatus() { return status; }
    }

    private void addServerDialog(Consumer<Server> onSuccess) {
        Dialog<Server> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add Server");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = createAddServerGrid();
        dialog.getDialogPane().setContent(grid);

        TextField idField = (TextField) grid.getChildren().get(1);
        TextField cpuField = (TextField) grid.getChildren().get(3);
        TextField memField = (TextField) grid.getChildren().get(5);
        TextField diskField = (TextField) grid.getChildren().get(7);
        TextField capField = (TextField) grid.getChildren().get(9);
        ChoiceBox<String> typeField = (ChoiceBox<String>) grid.getChildren().get(11);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? validateAndCreateServer(idField, cpuField, memField, diskField, capField, typeField) : null);

        dialog.showAndWait().ifPresent(onSuccess::accept);
    }

    private void failServerDialog(Consumer<String> onSuccess) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Fail Server");
        dialog.setHeaderText("Enter Server ID to fail:");
        dialog.showAndWait().ifPresent(id -> {
            Server server = balancer.getServer(id);
            if (server != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.initModality(Modality.APPLICATION_MODAL);
                confirm.setTitle("Confirm Failure");
                confirm.setHeaderText("Are you sure you want to fail server " + id + "?");
                confirm.setContentText("This will mark the server as unhealthy.");
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        onSuccess.accept(id);
                    }
                });
            } else {
                logger.error("Server '{}' not found!", id);
                alert(ERROR_TITLE, "Server '" + id + "' not found.");
            }
        });
    }

    private void balanceLoadDialog(Runnable onConfirm) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initModality(Modality.APPLICATION_MODAL);
        confirm.setTitle("Balance Load");
        confirm.setHeaderText("Rebalance server loads?");
        confirm.setContentText("This will redistribute load across healthy servers.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                onConfirm.run();
            }
        });
    }

    private void initCloudDialog(Consumer<Integer> onSuccess) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Initialize Cloud Servers");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Number of Servers [" + config.getCloudMinServers() + "-" + config.getCloudMaxServers() + "]:"), 0, 0);
        TextField countField = new TextField(String.valueOf(config.getCloudMinServers()));
        grid.add(countField, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int count = Integer.parseInt(countField.getText());
                    if (count < config.getCloudMinServers() || count > config.getCloudMaxServers()) {
                        alert(ERROR_TITLE, "Number of servers must be between " + config.getCloudMinServers() + " and " + config.getCloudMaxServers());
                        return null;
                    }
                    return count;
                } catch (NumberFormatException e) {
                    alert(ERROR_TITLE, "Invalid number: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(onSuccess::accept);
    }

    private void scaleCloudDialog(Consumer<Integer> onSuccess) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Scale Cloud Servers");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Adjust Count (±) [-" + config.getCloudMaxServers() + " to +" + config.getCloudMaxServers() + "]:"), 0, 0);
        TextField adjustField = new TextField("0");
        grid.add(adjustField, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int adjust = Integer.parseInt(adjustField.getText());
                    int currentCapacity = cloudManager.getCurrentCapacity();
                    int newCapacity = currentCapacity + adjust;
                    if (newCapacity < config.getCloudMinServers() || newCapacity > config.getCloudMaxServers()) {
                        alert(ERROR_TITLE, "New capacity must be between " + config.getCloudMinServers() + " and " + config.getCloudMaxServers());
                        return null;
                    }
                    return adjust;
                } catch (NumberFormatException e) {
                    alert(ERROR_TITLE, "Invalid number: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(onSuccess::accept);
    }

    private void undoLastAction() {
        synchronized (commandHistory) {
            if (commandHistory.isEmpty()) {
                alert(ERROR_TITLE, "No actions to undo.");
                return;
            }
            Command lastCommand = commandHistory.pop();
            lastCommand.undoAsync().thenRun(() -> {
                Platform.runLater(() -> {
                    log("Last action undone: " + lastCommand.getDescription());
                    updateUndoButtonState();
                    updateCommandHistoryView();
                });
            }).exceptionally(throwable -> {
                Platform.runLater(() -> alert(ERROR_TITLE, "Undo failed: " + throwable.getMessage()));
                return null;
            });
        }
    }

    private void updateCommandHistoryView() {
        synchronized (commandHistory) {
            commandHistoryView.getItems().setAll(
                commandHistory.stream()
                    .map(cmd -> cmd.getDescription() + " [" + cmd.getStatus() + "]")
                    .collect(Collectors.toList())
            );
        }
    }

    private Stack<Command> loadCommandHistory() {
        File file = new File(COMMAND_HISTORY_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Gson gson = new Gson();
                List<CommandDto> dtos = gson.fromJson(reader, new TypeToken<List<CommandDto>>(){}.getType());
                Stack<Command> history = new Stack<>();
                if (dtos != null) {
                    for (CommandDto dto : dtos) {
                        Command command = dto.toCommand(this, balancer, cloudManager);
                        if (command != null) {
                            history.push(command);
                        }
                    }
                }
                logger.info("Loaded command history from {}", COMMAND_HISTORY_FILE);
                return history;
            } catch (Exception e) {
                logger.error("Failed to load command history: {}", e.getMessage(), e);
            }
        }
        return new Stack<>();
    }

    private void saveCommandHistory() {
        try (FileWriter writer = new FileWriter(COMMAND_HISTORY_FILE)) {
            Gson gson = new Gson();
            synchronized (commandHistory) {
                List<CommandDto> dtos = commandHistory.stream()
                    .map(CommandDto::fromCommand)
                    .collect(Collectors.toList());
                gson.toJson(dtos, writer);
            }
            logger.info("Saved command history to {}", COMMAND_HISTORY_FILE);
        } catch (Exception e) {
            logger.error("Failed to save command history: {}", e.getMessage(), e);
            alert(ERROR_TITLE, "Failed to save command history: " + e.getMessage());
        }
    }

    private void handleConfigError(String msg) {
        log("Config Error: " + msg);
        alert(ERROR_TITLE, msg);
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
        logger.info(msg);
    }

    private void alert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    private void displayStatus() {
        if (!isUpdating.get()) return;

        synchronized (balancer) {
            serverRowMap.values().forEach(ServerTableRow::close);
            allServerData.clear();
            serverRowMap.clear();
            List<String> newAlerts = new ArrayList<>();
            for (Server server : balancer.getServers()) {
                ServerTableRow row = serverRowMap.computeIfAbsent(server.getServerId(), k -> new ServerTableRow(server));
                row.updateSafelyFromServer();
                allServerData.add(row);
                if (!row.isHealthy()) {
                    newAlerts.add("Server " + row.getServerId() + " is unhealthy!");
                }
            }
            allAlerts.addAll(newAlerts);
            if (!newAlerts.isEmpty()) {
                log("New alerts detected: " + newAlerts.size());
            }
            Platform.runLater(() -> {
                updateTableData(pagination.getCurrentPageIndex());
                updateAlertData(alertPagination.getCurrentPageIndex());
                updateLoadChart();
            });
        }
        log("Status refreshed at " + new Date());
    }

    private void updateTableData(int pageIndex) {
        List<ServerTableRow> filteredData = allServerData.stream()
            .filter(row -> "All".equals(typeFilter.getValue()) || row.getServerType().equals(typeFilter.getValue()))
            .filter(row -> "All".equals(healthFilter.getValue()) ||
                          ("Healthy".equals(healthFilter.getValue()) && row.isHealthy()) ||
                          ("Unhealthy".equals(healthFilter.getValue()) && !row.isHealthy()))
            .filter(row -> searchField.getText().isEmpty() || row.getServerId().toLowerCase().contains(searchField.getText().toLowerCase()))
            .collect(Collectors.toList());

        int pageSize = config.getServersPerPage();
        int pageCount = (int) Math.ceil((double) filteredData.size() / pageSize);
        pagination.setPageCount(Math.max(1, pageCount));

        int fromIndex = pageIndex * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filteredData.size());
        serverData.setAll(filteredData.subList(fromIndex, toIndex));
        serverTable.setItems(serverData);
    }

    private void updateAlertData(int pageIndex) {
        int pageSize = config.getMaxAlertsDisplayed();
        int pageCount = (int) Math.ceil((double) allAlerts.size() / pageSize);
        alertPagination.setPageCount(Math.max(1, pageCount));

        int fromIndex = pageIndex * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allAlerts.size());
        StringBuilder alertText = new StringBuilder();
        if (allAlerts.isEmpty()) {
            alertText.append(NO_ALERTS_MESSAGE);
        } else {
            allAlerts.subList(fromIndex, toIndex).forEach(alert -> alertText.append(alert).append("\n"));
        }
        alertArea.setText(alertText.toString());
    }

    private void updateLoadChart() {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Load Distribution");
        List<ServerTableRow> topServers = allServerData.stream()
            .sorted(Comparator.comparingDouble(ServerTableRow::getLoadScore).reversed())
            .limit(config.getMaxBarsInChart())
            .collect(Collectors.toList());
        for (ServerTableRow row : topServers) {
            XYChart.Data<String, Number> data = new XYChart.Data<>(row.getServerId(), row.getLoadScore());
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.getStyleClass().add(row.isHealthy() ? "healthy-bar" : "unhealthy-bar");
                }
            });
            series.getData().add(data);
        }
        Platform.runLater(() -> {
            loadChart.getData().clear();
            loadChart.getData().add(series);
            loadChart.setTitle("Load Distribution (Top " + topServers.size() + ")");
        });
    }

    private void updateUndoButtonState() {
        undoButton.setDisable(commandHistory.isEmpty());
    }

    private GridPane createAddServerGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Server ID:"), 0, 0);
        grid.add(new TextField(), 1, 0);
        grid.add(new Label("CPU Usage:"), 0, 1);
        grid.add(new TextField(), 1, 1);
        grid.add(new Label("Memory Usage:"), 0, 2);
        grid.add(new TextField(), 1, 2);
        grid.add(new Label("Disk Usage:"), 0, 3);
        grid.add(new TextField(), 1, 3);
        grid.add(new Label("Capacity:"), 0, 4);
        grid.add(new TextField(), 1, 4);
        grid.add(new Label("Type:"), 0, 5);
        ChoiceBox<String> typeBox = new ChoiceBox<>(FXCollections.observableArrayList("CLOUD", "ONSITE"));
        typeBox.setValue("CLOUD");
        grid.add(typeBox, 1, 5);
        return grid;
    }

    private Server validateAndCreateServer(TextField idField, TextField cpuField, TextField memField,
                                           TextField diskField, TextField capField, ChoiceBox<String> typeField) {
        try {
            String id = idField.getText();
            double cpu = Double.parseDouble(cpuField.getText());
            double mem = Double.parseDouble(memField.getText());
            double disk = Double.parseDouble(diskField.getText());
            double cap = Double.parseDouble(capField.getText());
            ServerType type = ServerType.valueOf(typeField.getValue());
            return new Server(id, cpu, mem, disk, type, cap);
        } catch (Exception e) {
            alert(ERROR_TITLE, "Invalid input: " + e.getMessage());
            return null;
        }
    }

    private void showServerDetailsDialog(ServerTableRow row) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Server Details");
        alert.setHeaderText("Details for " + row.getServerId());
        alert.setContentText(String.format("Type: %s\nCPU: %.2f%%\nMemory: %.2f%%\nDisk: %.2f%%\nCapacity: %.2f\nLoad Score: %.2f\nHealthy: %s",
            row.getServerType(), row.getCpuUsage(), row.getMemoryUsage(), row.getDiskUsage(), row.getCapacity(), row.getLoadScore(), row.isHealthy() ? "Yes" : "No"));
        alert.showAndWait();
    }

  private void importLogs() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Import Logs");
    File file = fileChooser.showOpenDialog(null);
    if (file != null) {
        try {
            Utils.importServerLogs(file.getPath(), file.getName().endsWith(".json") || file.getName().endsWith(".json.gz") ? "json" : "csv", 
                                  balancer, progress -> log("Import progress: " + progress + "%"), ",", config.isCloudEnabled());
            updateAlertData(alertPagination.getCurrentPageIndex());
            log("Imported logs from " + file.getName());
        } catch (Exception e) {
            alert(ERROR_TITLE, "Failed to import logs: " + e.getMessage());
        }
    }
}

private void generateReport() {
    StringBuilder report = new StringBuilder("Server Report - " + new Date() + "\n");
    allServerData.forEach(row -> report.append(String.format("%s: Load=%.2f, Healthy=%s\n",
        row.getServerId(), row.getLoadScore(), row.isHealthy() ? "Yes" : "No")));

    Alert reportDialog = new Alert(Alert.AlertType.INFORMATION);
    reportDialog.initModality(Modality.APPLICATION_MODAL);
    reportDialog.setTitle("Server Report");
    reportDialog.setHeaderText("Generated Report");
    TextArea reportArea = new TextArea(report.toString());
    reportArea.setEditable(false);
    reportDialog.getDialogPane().setContent(reportArea);
    reportDialog.getButtonTypes().add(ButtonType.OK);
    Button saveButton = new Button("Save to File");
    saveButton.setOnAction(e -> {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Report");
        fileChooser.setInitialFileName("report_" + System.currentTimeMillis() + ".txt");
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                List<Server> servers = allServerData.stream()
                                  .map(ServerTableRow::getSourceServer)
                                  .collect(Collectors.toList());
                Utils.exportReport(file.getPath(), "csv", servers, allAlerts, 
                                  progress -> log("Export progress: " + progress + "%"), config.isCloudEnabled());
                log("Report saved to " + file.getName());
            } catch (Exception ex) {
                alert(ERROR_TITLE, "Failed to save report: " + ex.getMessage());
            }
        }
    });
    reportDialog.getDialogPane().setExpandableContent(new VBox(10, saveButton));
    reportDialog.showAndWait();
}

    private void checkHealth() {
        log("Checking server health...");
        allServerData.forEach(row -> {
            if (!row.isHealthy()) {
                allAlerts.add("Health check: Server " + row.getServerId() + " is unhealthy!");
            }
        });
        updateAlertData(alertPagination.getCurrentPageIndex());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
