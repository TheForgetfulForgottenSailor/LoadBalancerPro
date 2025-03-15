package gui;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference; // New import for AtomicReference
import core.LoadBalancer;
import core.Server;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Map;

/**
 * GUI Application for Load Balancer Management.
 * 
 * This class provides a graphical user interface to interact with the {@code LoadBalancer}.
 * It allows real-time visualization of server distribution, status updates, and logs.
 * 
 * <b>Features:</b>
 * - Displays load balancing status.
 * - Logs real-time updates for server activity.
 * - Provides a structured interface for interacting with {@code LoadBalancer}.
 * 
 * The GUI leverages JavaFX for rendering and Log4j for structured logging.
 * 
 * <p><b>UML Diagram:</b></p>
 * <p><img src="loadbalancergui.png" alt="UML Diagram for LoadBalancerGUI"></p>
 */

public class LoadBalancerGUI extends Application {
    /**
     * Logger instance for LoadBalancerGUI.
     * 
     * This logger tracks GUI events, errors, and important load balancing actions.
     * It logs user interactions, status updates, and system errors.
     */
    private static final Logger logger = LogManager.getLogger(LoadBalancerGUI.class);

    /**
     * Reference to the LoadBalancer instance.
     * 
     * This LoadBalancer instance manages server distribution and load balancing operations.
     * The GUI interacts with this instance to display real-time server status.
     */
    private LoadBalancer balancer;

    /**
     * Text area for displaying status updates.
     * 
     * This JavaFX component shows real-time logs, errors, and server load status.
     * It helps users monitor the LoadBalancer's activity.
     */
    private TextArea statusArea;

	/**
     * Initializes and launches the LoadBalancer GUI.
     * 
     * This method sets up the JavaFX stage, layout, and user interface components,
     * providing interactive controls for managing load balancing operations.
     * 
     * <p><b>Components and Functionality:</b></p>
     * - Creates a new {@link LoadBalancer} instance to manage server distribution.
     * - Initializes a non-editable {@link TextArea} for real-time status updates.
     * - Adds interactive buttons for:
     *   - Adding servers (`addServerButton`)
     *   - Importing logs (`importButton`)
     *   - Balancing load (`balanceButton`)
     *   - Generating reports (`reportButton`)
     *   - Displaying system status (`statusButton`)
     *   - Checking server health (`healthButton`)
     *   - Simulating server failure (`failButton`)
     * - Defines button actions to trigger corresponding operations.
     * - Ensures clean shutdown by calling {@code balancer.shutdown()} when the GUI is closed.
     * 
     * <p><b>Logging:</b></p>
     * - Logs the initialization with `"=== FIRING UP THE GUI BEAST ==="`.
     * - Logs GUI shutdown with `"=== GUI SHUTTING DOWN—PEACE OUT ==="` upon exit.
     * 
     * @param stage The primary stage for this JavaFX application.
     */
    @Override
    public void start(Stage stage) {
        logger.info("=== FIRING UP THE GUI BEAST ===");
        balancer = new LoadBalancer();
        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefHeight(300);

        Button addServerButton = new Button("Add Server");
        Button importButton = new Button("Import Logs");
        Button balanceButton = new Button("Balance Load");
        Button reportButton = new Button("Generate Report");
        Button statusButton = new Button("Show Status");
        Button healthButton = new Button("Check Health");
        Button failButton = new Button("Fail Server");

        HBox controls = new HBox(10, addServerButton, importButton, balanceButton, reportButton,
                                 statusButton, healthButton, failButton);
        controls.setPadding(new javafx.geometry.Insets(10));
        VBox root = new VBox(10, controls, statusArea);

        addServerButton.setOnAction(e -> addServerDialog());
        importButton.setOnAction(e -> importLogs());
        balanceButton.setOnAction(e -> balanceLoadDialog());
        reportButton.setOnAction(e -> generateReport());
        statusButton.setOnAction(e -> displayStatus());
        healthButton.setOnAction(e -> balancer.checkServerHealth());
        failButton.setOnAction(e -> failServerDialog());

        stage.setOnCloseRequest(e -> {
            logger.info("=== GUI SHUTTING DOWN—PEACE OUT ===");
            balancer.shutdown();
        });

        Scene scene = new Scene(root, 800, 400);
        stage.setTitle("LoadBalancerPro - Buck Wild GUI");
        stage.setScene(scene);
        stage.show();
    }
	/**
     * Displays a dialog for adding a new server to the LoadBalancer.
     * 
     * This method creates a JavaFX {@link Dialog} that allows the user to input server details, 
     * including ID, CPU usage, memory usage, disk usage, and capacity. The dialog validates input 
     * and ensures that the server ID is unique before adding it to the {@link LoadBalancer}.
     * 
     * <p><b>Dialog Components:</b></p>
     * - {@code TextField idField}: Input for the server ID.
     * - {@code TextField cpuField}: Input for CPU usage percentage.
     * - {@code TextField memField}: Input for memory usage percentage.
     * - {@code TextField diskField}: Input for disk usage percentage.
     * - {@code TextField capField}: Input for server capacity (default: 100.0).
     * 
     * <p><b>Validation and Behavior:</b></p>
     * - If the server ID already exists in {@code balancer.serverMap}, an error alert is shown.
     * - If any input contains invalid numeric values, an error alert is displayed.
     * - If valid, a new {@link Server} instance is created and added to the LoadBalancer.
     * - Logs successful server addition using the {@code logger} and updates the status area.
     * 
     * <p><b>Logging:</b></p>
     * - Logs the server addition with `"Server {} added—ready to roll!"`.
     * - Displays the same message in the GUI log for user feedback.
     */
	private void addServerDialog() {
		Dialog<Server> dialog = new Dialog<>();
        dialog.setTitle("Add Server");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        TextField idField = new TextField();
        TextField cpuField = new TextField();
        TextField memField = new TextField();
        TextField diskField = new TextField();
        TextField capField = new TextField("100.0");
        grid.add(new Label("Server ID:"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("CPU Usage (%):"), 0, 1);
        grid.add(cpuField, 1, 1);
        grid.add(new Label("Memory Usage (%):"), 0, 2);
        grid.add(memField, 1, 2);
        grid.add(new Label("Disk Usage (%):"), 0, 3);
        grid.add(diskField, 1, 3);
        grid.add(new Label("Capacity:"), 0, 4);
        grid.add(capField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String id = idField.getText();
                    if (balancer.serverMap.containsKey(id)) {
                        alert("Error", "Server ID already exists, big dawg!");
                        return null;
                    }
                    double cpu = Double.parseDouble(cpuField.getText());
                    double mem = Double.parseDouble(memField.getText());
                    double disk = Double.parseDouble(diskField.getText());
                    double cap = Double.parseDouble(capField.getText());
                    Server server = new Server(id, cpu, mem, disk);
                    server.setCapacity(cap);
                    return server;
                } catch (NumberFormatException e) {
                    alert("Error", "Invalid number input, fam!");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(server -> {
            balancer.addServer(server);
            logger.info("Server {} added—ready to roll!", server.getServerId());
            log("Server " + server.getServerId() + " added—ready to roll!");
        });
    }

	/**
     * Opens a file chooser to import server logs into the LoadBalancer.
     * 
     * This method prompts the user to select a log file and specify its format 
     * (CSV or JSON) for processing. The chosen file is then passed to 
     * {@code balancer.importServerLogs()} to be parsed and loaded.
     * 
     * <p><b>Process:</b></p>
     * - A {@link FileChooser} dialog allows the user to pick a log file.
     * - A {@link TextInputDialog} prompts the user for the format (csv/json).
     * - If valid, the file is processed using {@code LoadBalancer.importServerLogs()}.
     * - If an error occurs, an alert is displayed, and the error is logged.
     * 
     * <p><b>Logging:</b></p>
     * - Logs a successful import with `"Logs imported from {}—servers loaded!"`.
     * - Logs failures with `"Import crashed: {}"` and displays an alert.
     * 
     * <p><b>GUI Integration:</b></p>
     * - Updates the log area in the GUI to reflect the import process.
     * - Ensures a clean workflow for importing server logs dynamically.
     */
    private void importLogs() {
        FileChooser fc = new FileChooser();
        fc.setInitialDirectory(new File("."));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            TextInputDialog formatDialog = new TextInputDialog("csv");
            formatDialog.setTitle("Import Format");
            formatDialog.setHeaderText("Enter format (csv/json):");
            formatDialog.showAndWait().ifPresent(format -> {
                try {
                    balancer.importServerLogs(file.getPath(), format);
                    logger.info("Logs imported from {}—servers loaded!", file.getPath());
                    log("Logs imported from " + file.getPath() + "—servers loaded!");
                } catch (Exception e) {
                    logger.error("Import crashed: {}", e.getMessage());
                    alert("Error", "Import crashed: " + e.getMessage());
                }
            });
        }
    }
    
    
	/**
     * Opens a dialog to distribute data load among servers using a selected strategy.
     * 
     * This method first prompts the user to enter the total data amount (in GB) to be 
     * distributed. The user then selects a load balancing strategy from available options:
     * <ul>
     *   <li>Round Robin</li>
     *   <li>Least Loaded</li>
     *   <li>Weighted Distribution</li>
     *   <li>Consistent Hashing</li>
     *   <li>Capacity-Aware</li>
     *   <li>Predictive Load Balancing</li>
     * </ul>
     * 
     * <p><b>Process:</b></p>
     * - If the selected strategy is <b>Consistent Hashing</b>, another dialog appears 
     *   asking the user for the number of data keys before executing the strategy.
     * - The load balancing result is logged and displayed in the GUI.
     * - If no valid servers are available, a warning is logged and displayed.
     * 
     * <p><b>Error Handling:</b></p>
     * - If invalid or negative data is entered, an alert is shown.
     * - If the number of keys for Consistent Hashing is invalid, an error is logged.
     * - Any failures during execution are caught and logged.
     * 
     * <p><b>Logging:</b></p>
     * - Successful operations are logged with `"Load balanced with {}: {}"`.
     * - If no distribution occurs, logs `"No distribution—servers down?"`.
     */
    private void balanceLoadDialog() {
        TextInputDialog dataDialog = new TextInputDialog();
        dataDialog.setTitle("Balance Load");
        dataDialog.setHeaderText("Enter total data (GB):");
        dataDialog.showAndWait().ifPresent(dataStr -> {
            try {
                double data = Double.parseDouble(dataStr);
                if (data < 0) throw new IllegalArgumentException("Negative data? Nah!");
                ChoiceDialog<String> strategyDialog = new ChoiceDialog<>("Round Robin",
                        "Round Robin", "Least Loaded", "Weighted", "Consistent Hashing",
                        "Capacity-Aware", "Predictive");
                strategyDialog.setTitle("Strategy");
                strategyDialog.setHeaderText("Pick your balancing vibe:");
                strategyDialog.showAndWait().ifPresent(strategy -> {
                    Map<String, Double> dist = null;
                    if (strategy.equals("Consistent Hashing")) {
                        TextInputDialog keysDialog = new TextInputDialog("10");
                        keysDialog.setHeaderText("Enter number of data keys:");
                        AtomicReference<Map<String, Double>> distRef = new AtomicReference<>(null);
                        keysDialog.showAndWait().ifPresent(keysStr -> {
                            try {
                                int keys = Integer.parseInt(keysStr);
                                distRef.set(balancer.consistentHashing(data, keys));
                            } catch (NumberFormatException e) {
                                alert("Error", "Keys gotta be a number, dawg!");
                            }
                        });
                        dist = distRef.get();
                    } else {
                        dist = switch (strategy) {
                            case "Round Robin" -> balancer.roundRobin(data);
                            case "Least Loaded" -> balancer.leastLoaded(data);
                            case "Weighted" -> balancer.weightedDistribution(data);
                            case "Capacity-Aware" -> balancer.capacityAware(data);
                            case "Predictive" -> balancer.predictiveLoadBalancing(data);
                            default -> null;
                        };
                    }
                    if (dist != null && !dist.isEmpty()) {
                        logger.info("Load balanced with {}: {}", strategy, dist);
                        log("Load balanced with " + strategy + ": " + dist);
                    } else {
                        logger.warn("No distribution—servers down?");
                        log("No distribution—check your servers, fam!");
                    }
                });
            } catch (Exception e) {
                logger.error("Balance load failed: {}", e.getMessage());
                alert("Error", "Data input flopped: " + e.getMessage());
            }
        });
    }
    /**
     * Generates and saves a report of the current server load and status.
     * 
     * This method allows the user to:
     * <ul>
     *   <li>Select a save location for the report.</li>
     *   <li>Choose a format: CSV or JSON.</li>
     * </ul>
     * 
     * <p><b>Process:</b></p>
     * - The user selects a file destination using a file chooser.
     * - A dialog prompts the user to select the report format.
     * - The report is then generated and saved in the specified format.
     * - Success and failure messages are logged and displayed in the GUI.
     * 
     * <p><b>Error Handling:</b></p>
     * - If no file is selected, the operation is canceled.
     * - If an error occurs while generating the report, an error message is displayed and logged.
     * 
     * <p><b>Logging:</b></p>
     * - Success: `"Report dropped at {}—stats ready!"`.
     * - Failure: `"Report generation failed: {}"`.
     */
    private void generateReport() {
        FileChooser fc = new FileChooser();
        fc.setInitialDirectory(new File("resources/logs"));
        File file = fc.showSaveDialog(null);
        if (file != null) {
            ChoiceDialog<String> formatDialog = new ChoiceDialog<>("csv", "csv", "json");
            formatDialog.setTitle("Report Format");
            formatDialog.setHeaderText("Pick your report style:");
            formatDialog.showAndWait().ifPresent(format -> {
                try {
                    balancer.exportReport(file.getPath(), format);
                    logger.info("Report dropped at {}—stats ready!", file.getPath());
                    log("Report dropped at " + file.getPath() + "—check the stats!");
                } catch (Exception e) {
                    logger.error("Report generation failed: {}", e.getMessage());
                    alert("Error", "Report failed: " + e.getMessage());
                }
            });
        }
    }
    
	/**
     * Displays the current status of all servers in the load balancer.
     * 
     * This method retrieves and formats server status details, including:
     * <ul>
     *   <li>Server ID</li>
     *   <li>CPU usage percentage</li>
     *   <li>Memory usage percentage</li>
     *   <li>Disk usage percentage</li>
     *   <li>Capacity</li>
     *   <li>Load score</li>
     *   <li>Health status</li>
     * </ul>
     * 
     * <p><b>Process:</b></p>
     * - If no servers exist, a message stating "No servers—crickets, big dawg!" is displayed.
     * - Otherwise, each server's metrics are formatted and logged.
     * - The status is logged in the GUI and in the system logs.
     * 
     * <p><b>Synchronization:</b></p>
     * - Access to the `servers` list is synchronized to prevent concurrency issues.
     * 
     * <p><b>Logging:</b></p>
     * - Success: `"Displaying server status:\n{}"`.
     */
    private void displayStatus() {
        StringBuilder sb = new StringBuilder("=== SERVER STATUS ===\n");
        synchronized (balancer) {
            if (balancer.servers.isEmpty()) {
                sb.append("No servers—crickets, big dawg!\n");
            } else {
                for (Server s : balancer.servers) {
                    sb.append(String.format("Server %s: CPU:%.2f%% Mem:%.2f%% Disk:%.2f%% Cap:%.2f Load:%.2f Healthy:%b%n",
                            s.getServerId(), s.getCpuUsage(), s.getMemoryUsage(), s.getDiskUsage(),
                            s.getCapacity(), s.getLoadScore(), s.isHealthy()));
                }
            }
        }
        logger.info("Displaying server status:\n{}", sb);
        log(sb.toString());
    }
    
	/**
     * Simulates a server failure by marking the specified server as unhealthy.
     * 
     * This method allows the user to input a server ID, and if the server exists:
     * <ul>
     *   <li>It is marked as unhealthy (fail state).</li>
     *   <li>A warning is logged indicating the server has failed.</li>
     *   <li>A log message is displayed in the GUI.</li>
     * </ul>
     * 
     * <p><b>Process:</b></p>
     * - If the entered server ID exists in `serverMap`, its health is set to `false`.
     * - If the server ID does not exist, an error message is logged and displayed.
     * 
     * <p><b>Synchronization:</b></p>
     * - Access to the `balancer` instance is synchronized to ensure thread safety.
     * 
     * <p><b>Logging:</b></p>
     * - Success: `"Server {} smoked—failover engaged!"`
     * - Failure: `"Server '{}' not found!"`
     */
    private void failServerDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Fail Server");
        dialog.setHeaderText("Enter Server ID to fail:");
        dialog.showAndWait().ifPresent(id -> {
            synchronized (balancer) {
                Server server = balancer.serverMap.get(id);
                if (server != null) {
                    server.setHealthy(false);
                    logger.warn("Server {} smoked—failover engaged!", id);
                    log("Server " + id + " smoked—failover engaged!");
                } else {
                    logger.error("Server '{}' not found!", id);
                    alert("Error", "Server '" + id + "' ain’t in the crew!");
                }
            }
        });
    }
    
	/**
     * Appends a log message to the GUI status area.
     * 
     * This method ensures that log messages are displayed in the `TextArea` 
     * within the JavaFX application thread using `Platform.runLater()`.
     * 
     * <p><b>Thread-Safety:</b></p>
     * - JavaFX UI updates must be performed on the JavaFX Application Thread.
     * - `Platform.runLater()` schedules the UI update safely from background threads.
     * 
     * <p><b>Behavior:</b></p>
     * - The message is appended to `statusArea` with a newline for readability.
     * 
     * @param msg The message to log in the GUI status area.
     */
    private void log(String msg) {
        Platform.runLater(() -> statusArea.appendText(msg + "\n"));
    }
    
	/**
     * Displays an alert dialog with the specified title and message.
     * 
     * This method ensures that the alert dialog is created and shown on the 
     * JavaFX Application Thread using `Platform.runLater()`.
     * 
     * <p><b>Thread-Safety:</b></p>
     * - JavaFX UI updates must be performed on the JavaFX Application Thread.
     * - `Platform.runLater()` ensures safe execution of UI-related tasks.
     * 
     * <p><b>Behavior:</b></p>
     * - Creates an error alert (`Alert.AlertType.ERROR`).
     * - Displays the provided `title` and `msg` as the content.
     * - The dialog blocks execution until the user acknowledges it (`showAndWait()`).
     * 
     * @param title The title of the alert window.
     * @param msg The content message displayed in the alert dialog.
     */
	private void alert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }
    
	/**
     * The main entry point for launching the JavaFX application.
     * 
     * This method invokes `launch(args)`, which initializes and starts 
     * the JavaFX runtime, eventually calling the `start(Stage stage)` method.
     * 
     * <p><b>Behavior:</b></p>
     * - Initializes the JavaFX application lifecycle.
     * - Creates the primary stage (`Stage`) and sets up the UI.
     * - Ensures the GUI is displayed properly.
     * 
     * <p><b>Notes:</b></p>
     * - The `launch()` method must be called from a `main` method 
     *   when running a standalone JavaFX application.
     * - If this class is run without `launch()`, JavaFX will not initialize properly.
     * 
     * @param args Command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
