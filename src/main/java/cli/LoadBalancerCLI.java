package cli;

import core.CloudManager;
import core.CloudConfig;
import core.LoadDistributionResult;
import core.LoadBalancer;
import core.ScalingRecommendation;
import core.Server;
import core.ServerMonitor;
import core.ServerType;
import gui.Command;
import gui.FailServerCommand;
import gui.LoadBalancerGUI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Command-line interface for LoadBalancerPro, supporting server management and cloud operations.
 */
public class LoadBalancerCLI {
    private static final Logger logger = LogManager.getLogger(LoadBalancerCLI.class);
    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        CliRunner runner = new CliRunner(args);
        runner.run();
    }

    static class CliRunner {
        private final LoadBalancer balancer = new LoadBalancer();
        private final CliConfig config;
        private CloudManager cloudManager;
        private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();
        private final UndoManager undoManager;
        private final ConsoleUtils console;
        private final Map<Integer, CliAction> actions;
        private volatile boolean running = true;
        private final boolean testMode;

        CliRunner(String[] args) {
            this.config = new CliConfig(args);
            this.undoManager = new UndoManager("undo_history.ser");
            this.console = new ConsoleUtils(new Scanner(System.in), config);
            this.actions = initializeActions();
            this.testMode = Arrays.stream(args).anyMatch("--test-mode"::equals);

            handleCliFlags(args);
            initializeCloudManager();
            startMonitorIfEnabled();
            if (testMode) logger.info("Test mode enabled: CLI will exit after first operation.");
        }

        void run() {
            try (Scanner scanner = console.getScanner()) {
                long lastInputTime = System.currentTimeMillis();
                while (running) {
                    try {
                        checkMonitorStatus();
                        long currentTime = System.currentTimeMillis();
                        if (currentTime < lastInputTime) {
                            logger.warn("System clock jump detected, resetting timeout.");
                            lastInputTime = currentTime;
                        }
                        if (currentTime - lastInputTime > config.getTimeoutSeconds() * 1000) {
                            logger.info("No input for {} seconds. Shutting down.", config.getTimeoutSeconds());
                            printSuccess("No input for " + config.getTimeoutSeconds() + " seconds. Shutting down.");
                            shutdown();
                            return;
                        }

                        displayMenu();
                        int choice = console.promptForInt("Enter your choice: ", 1, 15, "Menu choice");
                        if (choice != -1) {
                            lastInputTime = System.currentTimeMillis();
                            CliAction action = actions.get(choice);
                            if (action != null) {
                                action.execute(console);
                                if (testMode) {
                                    logger.info("Test mode: Exiting after first operation.");
                                    shutdown();
                                    return;
                                }
                            } else {
                                printError("Invalid choice—1-15 only!");
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Operation failed: {}", getRootCauseMessage(e), e);
                        printError("Operation failed: " + getRootCauseMessage(e));
                        if (console.isScannerClosed()) {
                            logger.error("Scanner closed unexpectedly — shutting down.");
                            shutdown();
                            return;
                        }
                        console.clearBuffer();
                    }
                }
            } finally {
                shutdown();
            }
        }

        private void handleCliFlags(String[] args) {
            if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("help") || arg.equalsIgnoreCase("--help"))) {
                showHelp();
                shutdown();
                System.exit(0);
            }
            if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("--version"))) {
                System.out.println("LoadBalancerCLI version " + VERSION);
                shutdown();
                System.exit(0);
            }
            if (Arrays.stream(args).anyMatch("--clear-undo"::equals)) {
                undoManager.clearUndoFile();
            }
        }

        private void initializeCloudManager() {
            if (config.isCloudEnabled()) {
                cloudManager = new CloudManager(balancer, new CloudConfig("your-access-key", "your-secret-key", "us-west-2", 
                                                                         "your-launch-template-id", "your-subnet-id"), 
                                               undoManager::addCommand);
                logger.info("CloudManager initialized with min={} and max={} servers.", 
                            config.getCloudMinServers(), config.getCloudMaxServers());
            } else {
                logger.info("Cloud integration disabled.");
            }
        }

        private void startMonitorIfEnabled() {
            if (!Arrays.stream(config.getArgs()).anyMatch("--no-monitor"::equals)) {
                ServerMonitor monitor = new ServerMonitor(balancer, config.getAlertThreshold(),
                        config.getMonitorIntervalMs(), config.getMaxFluctuation(),
                        msg -> System.out.println(config.getErrorColor() + msg + config.getResetColor()));
                monitor.start();
                logger.info("Server monitor started.");
            } else {
                logger.info("Server monitor disabled via --no-monitor flag.");
            }
        }

        private void checkMonitorStatus() {
            if (!monitorExecutor.isShutdown() && monitorExecutor.isTerminated()) {
                logger.warn("Server monitor stopped unexpectedly — restarting...");
                int attempts = 0;
                final int maxAttempts = 5;
                while (attempts < maxAttempts && running) {
                    try {
                        Thread.sleep((long) Math.pow(2, attempts) * 1000); // Exponential backoff
                        startMonitorIfEnabled();
                        logger.info("Monitor restarted after attempt {}", attempts + 1);
                        return;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Monitor restart interrupted: {}", e.getMessage());
                        return;
                    } catch (Exception e) {
                        attempts++;
                        logger.error("Monitor restart failed (attempt {}/{}): {}", attempts, maxAttempts, e.getMessage());
                    }
                }
                logger.error("Max monitor restart attempts ({}) reached. Monitor disabled.", maxAttempts);
                printError("Monitor failed to restart after " + maxAttempts + " attempts.");
            }
        }

        private void shutdown() {
            running = false;
            monitorExecutor.shutdownNow();
            try {
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Monitor did not shut down gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Shutdown interrupted: {}", e.getMessage(), e);
            }
            try {
                balancer.shutdown();
                if (cloudManager != null) {
                    cloudManager.shutdown();
                }
                undoManager.saveUndoHistory();
            } catch (Exception e) {
                logger.error("Balancer or CloudManager shutdown interrupted: {}", e.getMessage(), e);
            }
        }

        private Map<Integer, CliAction> initializeActions() {
            Map<Integer, CliAction> actions = new HashMap<>();
            actions.put(1, this::addServer);
            actions.put(2, this::importLogs);
            actions.put(3, this::balanceLoad);
            actions.put(4, this::generateReport);
            actions.put(5, this::displayServerStatus);
            actions.put(6, this::checkServerHealth);
            actions.put(7, this::failServer);
            actions.put(8, this::undoLastAction);
            actions.put(9, this::editServer);
            actions.put(10, this::clearScreen);
            actions.put(11, this::pauseMonitor);
            actions.put(12, this::resumeMonitor);
            actions.put(13, this::initializeCloud);
            actions.put(14, this::scaleCloud);
            actions.put(15, this::launchGUI);
            return actions;
        }

        private void addServer(ConsoleUtils console) {
            String id = console.promptForString("Enter Server ID: ", config.getMaxServerIdLength());
            if (id == null) return;
            double cpu = console.promptForDouble("Enter CPU Usage (%): [0-100] ", 0, 100, "CPU usage");
            if (cpu < 0) return;
            double mem = console.promptForDouble("Enter Memory Usage (%): [0-100] ", 0, 100, "Memory usage");
            if (mem < 0) return;
            double disk = console.promptForDouble("Enter Disk Usage (%): [0-100] ", 0, 100, "Disk usage");
            if (disk < 0) return;
            double capacity = console.promptForDouble("Enter Capacity (units): [1-10000] ", 1, 10000, "Capacity");
            if (capacity < 0) return;
            System.out.println("Select Server Type:\n1. CLOUD\t2. ONSITE");
            int typeChoice = console.promptForInt("Enter choice: ", 1, 2, "Server type");
            if (typeChoice < 0) return;

            try {
                Server server = new Server(id, cpu, mem, disk, typeChoice == 1 ? ServerType.CLOUD : ServerType.ONSITE);
                server.setCapacity(capacity);
                balancer.addServer(server);
                logger.info("Server {} added successfully.", id);
                printSuccess("Server " + id + " added successfully.");
            } catch (Exception e) {
                logger.error("Failed to add server: {}", e.getMessage(), e);
                printError("Failed to add server: " + getRootCauseMessage(e));
            }
        }

        private void importLogs(ConsoleUtils console) {
            String logFile = console.promptForString("Enter log file path: ", Integer.MAX_VALUE);
            if (logFile == null) return;
            String format = console.promptForString("Enter format (csv/json): ", Integer.MAX_VALUE);
            if (format == null || (!format.equalsIgnoreCase("csv") && !format.equalsIgnoreCase("json"))) {
                logger.error("Unsupported format: {}", format);
                printError("Unsupported format: " + format + ". Use 'csv' or 'json'!");
                return;
            }
            ProgressAnimation animation = new ProgressAnimation(config, "Importing logs ");
            try {
                animation.start();
                balancer.importServerLogs(logFile, format.toLowerCase());
                animation.stop();
                logger.info("Servers imported from {} successfully.", logFile);
                printSuccess("Servers imported from " + logFile + " successfully.");
            } catch (Exception e) {
                animation.stop();
                logger.error("Import failed: {}", e.getMessage(), e);
                printError("Import failed: " + getRootCauseMessage(e));
            }
        }

        private void balanceLoad(ConsoleUtils console) {
            synchronized (balancer) {
                if (balancer.getServers().isEmpty()) {
                    printError("No servers available to balance load.");
                    return;
                }
            }
            double data = console.promptForDouble("Enter total data to distribute (GB): [0-infinity] ", 0, Double.MAX_VALUE, "Data");
            if (data < 0) return;
            System.out.println("Select strategy:\n1. Round Robin\n2. Least Loaded\n3. Weighted\n4. Consistent Hashing\n5. Capacity-Aware\n6. Predictive");
            int strategyChoice = console.promptForInt("Enter your choice: ", 1, 6, "Strategy");
            if (strategyChoice < 0) return;

            LoadDistributionResult result = null;
            try {
                switch (strategyChoice) {
                    case 1: result = new LoadDistributionResult(balancer.roundRobin(data), 0.0); break;
                    case 2: result = new LoadDistributionResult(balancer.leastLoaded(data), 0.0); break;
                    case 3: result = new LoadDistributionResult(balancer.weightedDistribution(data), 0.0); break;
                    case 4:
                        int keys = console.promptForInt("Enter number of data keys: [1-infinity] ", 1, Integer.MAX_VALUE, "Number of keys");
                        if (keys < 0) return;
                        result = new LoadDistributionResult(balancer.consistentHashing(data, keys), 0.0);
                        break;
                    case 5: result = balancer.capacityAwareWithResult(data); break;
                    case 6: result = balancer.predictiveLoadBalancingWithResult(data); break;
                }
                if (result == null || (result.allocations().isEmpty() && result.unallocatedLoad() <= 0.0)) {
                    logger.warn("No distribution generated—no servers available?");
                    printError("No distribution—no servers available?");
                    return;
                }
                logger.info("Distribution: {}, unallocatedLoad={}", result.allocations(), result.unallocatedLoad());
                displayLoadDistribution(result.allocations());
                displayDistributionSafetyInfo(result);
            } catch (Exception e) {
                logger.error("Distribution failed: {}", e.getMessage(), e);
                printError("Distribution failed: " + getRootCauseMessage(e));
            }
        }

        private void displayLoadDistribution(Map<String, Double> distribution) {
            printSuccess("\nLoad Distribution:");
            List<Map.Entry<String, Double>> entries = new ArrayList<>(distribution.entrySet());
            int pageCount = (int) Math.ceil((double) entries.size() / config.getServersPerPage());
            for (int page = 0; page < pageCount && running; page++) {
                int fromIndex = page * config.getServersPerPage();
                int toIndex = Math.min(fromIndex + config.getServersPerPage(), entries.size());
                List<Map.Entry<String, Double>> pageEntries = entries.subList(fromIndex, toIndex);

                double maxValue = pageEntries.stream().mapToDouble(Map.Entry::getValue).max().orElse(1.0);

                System.out.println("Page " + (page + 1) + " of " + pageCount);
                for (Map.Entry<String, Double> entry : pageEntries) {
                    String serverId = entry.getKey();
                    if (serverId.length() > config.getMaxServerIdLength()) serverId = serverId.substring(0, config.getMaxServerIdLength() - 3) + "...";
                    double value = entry.getValue();
                    int barLength = (int) (value / maxValue * 20);
                    String bar = "█".repeat(Math.max(0, barLength));
                    System.out.printf("%-20s | %-20s | %.2f GB%n", serverId, bar, value);
                }
                if (page < pageCount - 1 && running && !console.isScannerClosed()) {
                    System.out.println("Press Enter to see the next page...");
                    console.getScanner().nextLine();
                }
            }
        }

        private void displayDistributionSafetyInfo(LoadDistributionResult result) {
            if (result.unallocatedLoad() <= 0.0) {
                return;
            }
            double targetCapacity = averageHealthyServerCapacity();
            ScalingRecommendation recommendation = balancer.recommendScaling(result.unallocatedLoad(), targetCapacity);
            printSuccess(String.format("Unallocated Load: %.2f GB", result.unallocatedLoad()));
            printSuccess(String.format("Recommended Additional Servers: %d", recommendation.additionalServers()));
        }

        private double averageHealthyServerCapacity() {
            synchronized (balancer) {
                return balancer.getServers().stream()
                        .filter(Server::isHealthy)
                        .mapToDouble(Server::getCapacity)
                        .average()
                        .orElse(0.0);
            }
        }

        private void generateReport(ConsoleUtils console) {
            String reportFile = console.promptForString("Enter report file path: ", Integer.MAX_VALUE);
            if (reportFile == null) return;
            String format = console.promptForString("Enter format (csv/json): ", Integer.MAX_VALUE);
            if (format == null || (!format.equalsIgnoreCase("csv") && !format.equalsIgnoreCase("json"))) {
                logger.error("Unsupported format: {}", format);
                printError("Unsupported format: " + format + ". Use 'csv' or 'json'!");
                return;
            }
            ProgressAnimation animation = new ProgressAnimation(config, "Generating report ");
            try {
                animation.start();
                balancer.exportReport(reportFile, format.toLowerCase());
                animation.stop();
                logger.info("Report generated successfully at {}", reportFile);
                printSuccess("Report generated successfully at " + reportFile);
            } catch (Exception e) {
                animation.stop();
                logger.error("Report generation failed: {}", e.getMessage(), e);
                printError("Report generation failed: " + getRootCauseMessage(e));
            }
        }

        private void displayServerStatus(ConsoleUtils console) {
            try {
                synchronized (balancer) {
                    if (balancer.getServers().isEmpty()) {
                        logger.info("No servers to display");
                        printSuccess("No servers available.");
                        return;
                    }

                    List<Server> servers = new ArrayList<>(balancer.getServers());
                    int pageCount = (int) Math.ceil((double) servers.size() / config.getServersPerPage());
                    for (int page = 0; page < pageCount && running; page++) {
                        int fromIndex = page * config.getServersPerPage();
                        int toIndex = Math.min(fromIndex + config.getServersPerPage(), servers.size());
                        List<Server> pageServers = servers.subList(fromIndex, toIndex);

                        StringBuilder sb = new StringBuilder("\n=== Server Status (Page " + (page + 1) + " of " + pageCount + ") ===\n");
                        for (Server s : pageServers) {
                            String displayId = s.getServerId().length() > config.getMaxServerIdLength() ? s.getServerId().substring(0, config.getMaxServerIdLength() - 3) + "..." : s.getServerId();
                            sb.append(String.format("Server %s (%s): CPU:%.2f%% Mem:%.2f%% Disk:%.2f%% " +
                                                    "Cap:%.2f Load:%.2f Healthy:%b%n",
                                                    displayId, s.getServerType(), s.getCpuUsage(), s.getMemoryUsage(),
                                                    s.getDiskUsage(), s.getCapacity(), s.getLoadScore(), s.isHealthy()));
                        }
                        logger.info(sb.toString());
                        System.out.println(config.getSuccessColor() + sb + config.getResetColor());

                        if (page < pageCount - 1 && running && !console.isScannerClosed()) {
                            System.out.println("Press Enter to see the next page...");
                            console.getScanner().nextLine();
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to display status: {}", e.getMessage(), e);
                printError("Failed to display status: " + getRootCauseMessage(e));
            }
        }

        private void checkServerHealth(ConsoleUtils console) {
            try {
                balancer.checkServerHealth();
                logger.info("Health check completed.");
                printSuccess("Health check completed.");
            } catch (Exception e) {
                logger.error("Health check failed: {}", e.getMessage(), e);
                printError("Health check failed: " + getRootCauseMessage(e));
            }
        }

        private void failServer(ConsoleUtils console) {
            String id = console.promptForString("Enter Server ID: ", config.getMaxServerIdLength());
            if (id == null) return;
            try {
                Command failCommand = new FailServerCommand(balancer, id);
                failCommand.execute();
                undoManager.addCommand(failCommand);
                logger.info("Server {} failed successfully.", id);
                printSuccess("Server " + id + " failed successfully.");
            } catch (Exception e) {
                logger.error("Failed to fail server: {}", e.getMessage(), e);
                printError("Failed to fail server: " + getRootCauseMessage(e));
            }
        }

        private void undoLastAction(ConsoleUtils console) {
            try {
                if (undoManager.undo()) {
                    logger.info("Last action undone.");
                    printSuccess("Last action undone.");
                } else {
                    logger.info("No actions to undo.");
                    printError("No actions to undo.");
                }
            } catch (Exception e) {
                logger.error("Undo failed: {}", e.getMessage(), e);
                printError("Undo failed: " + getRootCauseMessage(e));
            }
        }

        private void editServer(ConsoleUtils console) {
            String id = console.promptForString("Enter Server ID: ", config.getMaxServerIdLength());
            if (id == null) return;
            try {
                Server server = balancer.getServer(id);
                if (server == null) {
                    logger.warn("Server {} not found.", id);
                    printError("Server " + id + " not found.");
                    return;
                }
                double cpu = console.promptForDouble("Enter new CPU Usage (%): [0-100] ", 0, 100, "CPU usage");
                if (cpu < 0) return;
                double mem = console.promptForDouble("Enter new Memory Usage (%): [0-100] ", 0, 100, "Memory usage");
                if (mem < 0) return;
                double disk = console.promptForDouble("Enter new Disk Usage (%): [0-100] ", 0, 100, "Disk usage");
                if (disk < 0) return;
                double capacity = console.promptForDouble("Enter new Capacity (units): [1-10000] ", 1, 10000, "Capacity");
                if (capacity < 0) return;

                server.setCpuUsage(cpu);
                server.setMemoryUsage(mem);
                server.setDiskUsage(disk);
                server.setCapacity(capacity);
                logger.info("Server {} updated successfully.", id);
                printSuccess("Server " + id + " updated successfully.");
            } catch (Exception e) {
                logger.error("Failed to edit server: {}", e.getMessage(), e);
                printError("Failed to edit server: " + getRootCauseMessage(e));
            }
        }

        private void clearScreen(ConsoleUtils console) {
            try {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                logger.info("Screen cleared.");
            } catch (Exception e) {
                logger.error("Failed to clear screen: {}", e.getMessage(), e);
                printError("Failed to clear screen: " + getRootCauseMessage(e));
            }
        }

        private void pauseMonitor(ConsoleUtils console) {
            // Placeholder - requires ServerMonitor to have pause/resume methods
            printError("Monitor pause not implemented yet.");
        }

        private void resumeMonitor(ConsoleUtils console) {
            // Placeholder - requires ServerMonitor to have pause/resume methods
            printError("Monitor resume not implemented yet.");
        }

        private void initializeCloud(ConsoleUtils console) {
            if (cloudManager == null) {
                printError("Cloud integration is not enabled. Use --cloud-enabled to activate.");
                return;
            }
            int count = console.promptForInt("Enter number of cloud servers to initialize: [" + 
                                             config.getCloudMinServers() + "-" + config.getCloudMaxServers() + "] ", 
                                             config.getCloudMinServers(), config.getCloudMaxServers(), "Cloud servers");
            if (count < 0) return;

            Command initCommand = new InitCloudCommand(this, cloudManager, count);
            try {
                initCommand.execute();
                undoManager.addCommand(initCommand);
                logger.info("Initialized {} cloud servers.", count);
                printSuccess("Initialized " + count + " cloud servers.");
            } catch (Exception e) {
                logger.error("Failed to initialize cloud servers: {}", e.getMessage(), e);
                printError("Failed to initialize cloud servers: " + getRootCauseMessage(e));
            }
        }

        private static class InitCloudCommand implements Command {
            private final CliRunner cli;
            private final CloudManager cloudManager;
            private final int count;
            private List<Server> addedServers = new ArrayList<>();
            private final String id = "InitCloud-" + System.nanoTime();
            private Status status = Status.PENDING;

            InitCloudCommand(CliRunner cli, CloudManager cloudManager, int count) {
                this.cli = cli;
                this.cloudManager = cloudManager;
                this.count = count;
            }

            @Override
            public void execute() {
                try {
                    cloudManager.initializeCloudServers(count, count + 2); // Min and max servers
                    synchronized (cloudManager.getBalancer()) {
                        addedServers = cloudManager.getBalancer().getServers().stream()
                            .filter(s -> s.getServerType() == ServerType.CLOUD)
                            .collect(Collectors.toList());
                        status = Status.COMPLETED;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cli.printError("Cloud initialization interrupted: " + e.getMessage());
                    status = Status.FAILED;
                }
            }

            @Override
            public void undo() {
                synchronized (cloudManager.getBalancer()) {
                    for (Server server : addedServers) {
                        cloudManager.getBalancer().removeServer(server.getServerId());
                    }
                    cli.printSuccess("Undo: Removed " + addedServers.size() + " cloud servers.");
                }
            }

            @Override
            public boolean canUndo() { return true; }
            @Override
            public String getDescription() { return "Initialized " + count + " cloud servers"; }
            @Override
            public String getId() { return id; }
            @Override
            public Status getStatus() { return status; }
        }

        private void scaleCloud(ConsoleUtils console) {
            if (cloudManager == null) {
                printError("Cloud integration is not enabled. Use --cloud-enabled to activate.");
                return;
            }
            int adjust = console.promptForInt("Enter number of cloud servers to adjust (±): [-" + 
                                              config.getCloudMaxServers() + " to +" + config.getCloudMaxServers() + "] ", 
                                              -config.getCloudMaxServers(), config.getCloudMaxServers(), "Cloud adjustment");
            if (adjust == Integer.MIN_VALUE) return;

            Command scaleCommand = new ScaleCloudCommand(this, cloudManager, adjust);
            try {
                scaleCommand.execute();
                undoManager.addCommand(scaleCommand);
                logger.info("Scaled cloud servers by {}.", adjust);
                printSuccess("Scaled cloud servers by " + adjust + ".");
            } catch (Exception e) {
                logger.error("Failed to scale cloud servers: {}", e.getMessage(), e);
                printError("Failed to scale cloud servers: " + getRootCauseMessage(e));
            }
        }

        private static class ScaleCloudCommand implements Command {
            private final CliRunner cli;
            private final CloudManager cloudManager;
            private final int adjust;
            private List<Server> addedServers = new ArrayList<>();
            private List<String> removedServerIds = new ArrayList<>();
            private final String id = "ScaleCloud-" + System.nanoTime();
            private Status status = Status.PENDING;

            ScaleCloudCommand(CliRunner cli, CloudManager cloudManager, int adjust) {
                this.cli = cli;
                this.cloudManager = cloudManager;
                this.adjust = adjust;
            }

            @Override
            public void execute() {
                int currentCapacity = cloudManager.getCurrentCapacity();
                int newCapacity = currentCapacity + adjust;
                cloudManager.scaleServersAsync(newCapacity, success -> {
                    synchronized (cloudManager.getBalancer()) {
                        if (adjust > 0) {
                            addedServers = cloudManager.getBalancer().getServers().stream()
                                .filter(s -> s.getServerType() == ServerType.CLOUD)
                                .skip(currentCapacity)
                                .limit(adjust)
                                .collect(Collectors.toList());
                        } else if (adjust < 0) {
                            int toRemove = Math.min(-adjust, (int) cloudManager.getBalancer().getServers().stream()
                                .filter(s -> s.getServerType() == ServerType.CLOUD).count());
                            removedServerIds = cloudManager.getBalancer().getServers().stream()
                                .filter(s -> s.getServerType() == ServerType.CLOUD)
                                .limit(toRemove)
                                .map(Server::getServerId)
                                .collect(Collectors.toList());
                        }
                        status = success ? Status.COMPLETED : Status.FAILED;
                    }
                });
                // Wait briefly to ensure async operation completes for CLI feedback
                try {
                    Thread.sleep(2000); // Adjust as needed
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void undo() {
                synchronized (cloudManager.getBalancer()) {
                    if (adjust > 0) {
                        for (Server server : addedServers) {
                            cloudManager.getBalancer().removeServer(server.getServerId());
                        }
                        cli.printSuccess("Undo: Removed " + addedServers.size() + " cloud servers.");
                    } else if (adjust < 0) {
                        for (String serverId : removedServerIds) {
                            cloudManager.getBalancer().addServer(new Server(serverId, ServerType.CLOUD, 0.0, 0.0, 0.0, 1000.0));
                        }
                        cli.printSuccess("Undo: Restored " + removedServerIds.size() + " cloud servers.");
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

        private void launchGUI(ConsoleUtils console) {
            try {
                logger.info("Launching GUI...");
                printSuccess("Launching GUI...");
                LoadBalancerGUI.launch(LoadBalancerGUI.class, config.getArgs());
                shutdown(); // Exit CLI after launching GUI
            } catch (Exception e) {
                logger.error("Failed to launch GUI: {}", e.getMessage(), e);
                printError("Failed to launch GUI: " + getRootCauseMessage(e));
            }
        }

        private void displayMenu() {
            System.out.println("\n=== Data Server Load Balancer CLI ===");
            System.out.println("1. Add Server\n2. Import Server Logs\n3. Balance Load\n4. Generate Report\n" +
                    "5. Display Server Status\n6. Check Server Health\n7. Simulate Server Failure\n" +
                    "8. Undo Last Action\n9. Edit Server\n10. Clear Screen\n11. Pause Server Monitor\n" +
                    "12. Resume Server Monitor\n13. Initialize Cloud\n14. Scale Cloud\n15. Launch GUI");
        }

        private void printError(String message) {
            System.out.println(config.getErrorColor() + message + config.getResetColor());
            logger.error(message);
        }

        private void printSuccess(String message) {
            System.out.println(config.getSuccessColor() + message + config.getResetColor());
        }

        private String getRootCauseMessage(Throwable e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            return cause.getMessage() != null ? cause.getMessage() : cause.toString();
        }

        private void showHelp() {
            System.out.println("=== Load Balancer CLI Help ===\nUsage: java LoadBalancerCLI [options]");
            System.out.println("Options:\n  help, --help         Display this help message\n" +
                    "  --version           Display CLI version\n  --no-monitor        Disable server monitor\n" +
                    "  --clear-undo        Clear saved undo history\n" +
                    "  --test-mode         Exit after first operation (for testing)\n" +
                    "  --cloud-enabled     Enable cloud integration\n" +
                    "  --cloud-min-servers Set minimum cloud servers\n" +
                    "  --cloud-max-servers Set maximum cloud servers");
            System.out.println("Run without arguments to start the interactive CLI.");
        }
    }
}
