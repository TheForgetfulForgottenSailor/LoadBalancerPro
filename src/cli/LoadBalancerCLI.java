package cli;

import java.util.Map;
import core.LoadBalancer;
import core.Server;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadBalancerCLI {
    private static final Logger logger = LogManager.getLogger(LoadBalancerCLI.class);
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";
    private static final int MAX_SERVER_ID_LENGTH = 50; 
    private static LoadBalancer balancer = new LoadBalancer();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            showHelp();
            return;
        }

        try {
            while (true) {
                try {
                    System.out.println("\n=== Data Server Load Balancer CLI ===");
                    System.out.println("1. Add Server");
                    System.out.println("2. Import Server Logs");
                    System.out.println("3. Balance Load");
                    System.out.println("4. Generate Report");
                    System.out.println("5. Display Server Status");
                    System.out.println("6. Check Server Health");
                    System.out.println("7. Simulate Server Failure");
                    System.out.println("8. Exit");
                    int choice = getChoiceInput(scanner);
                    if (choice == -1) continue;

                    switch (choice) {
                        case 1:
                            String id = getServerIdInput(scanner);
                            if (id == null) continue;

                            double cpu = getValidatedDouble(scanner, "Enter CPU Usage (%): [0-100] ", 0, 100, "CPU usage");
                            if (cpu < 0) continue;

                            double mem = getValidatedDouble(scanner, "Enter Memory Usage (%): [0-100] ", 0, 100, "Memory usage");
                            if (mem < 0) continue;

                            double disk = getValidatedDouble(scanner, "Enter Disk Usage (%): [0-100] ", 0, 100, "Disk usage");
                            if (disk < 0) continue;

                            double capacity = getValidatedDouble(scanner, "Enter Capacity (units): [1-10000] ", 1, 10000, "Capacity");
                            if (capacity < 0) continue;

                            try {
                                Server server = new Server(id, cpu, mem, disk);
                                server.setCapacity(capacity);
                                balancer.addServer(server);
                                logger.info("Server {} locked and loaded!", id);
                                System.out.println(GREEN + "Server " + id + " locked and loaded!" + RESET);
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Failed to add server: {}", errorMsg);
                                System.out.println(RED + "Failed to add server: " + errorMsg + RESET);
                                continue;
                            }
                            break;

                        case 2:
                            String logFile = getNonEmptyStringInput(scanner, "Enter log file path: ");
                            if (logFile == null) continue;

                            String importFormat = getNonEmptyStringInput(scanner, "Enter format (csv/json): ");
                            if (importFormat == null) continue;
                            importFormat = importFormat.toLowerCase();
                            if (!importFormat.equals("csv") && !importFormat.equals("json")) {
                                logger.error("Unsupported format: {}. Use 'csv' or 'json'", importFormat);
                                System.out.println(RED + "Unsupported format: " + importFormat + ". Use 'csv' or 'json'!" + RESET);
                                continue;
                            }
                            try {
                                balancer.importServerLogs(logFile, importFormat);
                                logger.info("Servers imported from {}—ready to roll!", logFile);
                                System.out.println(GREEN + "Servers imported from " + logFile + "—ready to roll!" + RESET);
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Import crashed: {}", errorMsg);
                                System.out.println(RED + "Import crashed: " + errorMsg + RESET);
                            }
                            break;

                        case 3:
                            double data = getValidatedDouble(scanner, "Enter total data to distribute (GB): [0-infinity] ", 0, Double.MAX_VALUE, "Data");
                            if (data < 0) continue;

                            System.out.println("Select strategy:");
                            System.out.println("1. Round Robin");
                            System.out.println("2. Least Loaded");
                            System.out.println("3. Weighted");
                            System.out.println("4. Consistent Hashing");
                            System.out.println("5. Capacity-Aware");
                            System.out.println("6. Predictive");
                            int strategy = getChoiceInput(scanner);
                            if (strategy == -1) continue;

                            Map<String, Double> dist;
                            try {
                                switch (strategy) {
                                    case 1: dist = balancer.roundRobin(data); break;
                                    case 2: dist = balancer.leastLoaded(data); break;
                                    case 3: dist = balancer.weightedDistribution(data); break;
                                    case 4:
                                        int keys = getValidatedInt(scanner, "Enter number of data keys: [1-infinity] ", 1, Integer.MAX_VALUE, "Number of keys");
                                        if (keys < 0) continue;
                                        dist = balancer.consistentHashing(data, keys);
                                        break;
                                    case 5: dist = balancer.capacityAware(data); break;
                                    case 6: dist = balancer.predictiveLoadBalancing(data); break;
                                    default:
                                        logger.warn("Invalid strategy selected: {}", strategy);
                                        System.out.println(RED + "Invalid strategy—1-6 only!" + RESET);
                                        continue;
                                }
                                if (dist.isEmpty()) {
                                    logger.warn("No distribution generated—servers down?");
                                    System.out.println(RED + "No distribution—servers down?" + RESET);
                                } else {
                                    logger.info("Distribution: {}", dist);
                                    System.out.println(GREEN + "Distribution: " + dist + RESET);
                                }
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Distribution failed: {}", errorMsg);
                                System.out.println(RED + "Distribution failed: " + errorMsg + RESET);
                                continue;
                            }
                            break;

                        case 4:
                            String reportFile = getNonEmptyStringInput(scanner, "Enter report file path: ");
                            if (reportFile == null) continue;

                            String exportFormat = getNonEmptyStringInput(scanner, "Enter format (csv/json): ");
                            if (exportFormat == null) continue;
                            exportFormat = exportFormat.toLowerCase();
                            if (!exportFormat.equals("csv") && !exportFormat.equals("json")) {
                                logger.error("Unsupported format: {}. Use 'csv' or 'json'", exportFormat);
                                System.out.println(RED + "Unsupported format: " + exportFormat + ". Use 'csv' or 'json'!" + RESET);
                                continue;
                            }
                            try {
                                balancer.exportReport(reportFile, exportFormat);
                                logger.info("Report blasted to {}", reportFile);
                                System.out.println(GREEN + "Report blasted to " + reportFile + RESET);
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Report flopped: {}", errorMsg);
                                System.out.println(RED + "Report flopped: " + errorMsg + RESET);
                            }
                            break;

                        case 5:
                            try {
                                synchronized (balancer) {
                                    if (balancer.servers.isEmpty()) {
                                        logger.info("No servers to display");
                                        System.out.println(GREEN + "No servers—quiet as hell!" + RESET);
                                    } else {
                                        StringBuilder sb = new StringBuilder("\n=== Server Status ===\n");
                                        for (Server s : balancer.servers) {
                                            // Truncate long IDs for display
                                            String displayId = s.getServerId().length() > 20 ? s.getServerId().substring(0, 20) + "..." : s.getServerId();
                                            sb.append(String.format("Server %s: CPU:%.2f%% Mem:%.2f%% Disk:%.2f%% " +
                                                                    "Cap:%.2f Load:%.2f Healthy:%b%n",
                                                                    displayId, s.getCpuUsage(), s.getMemoryUsage(),
                                                                    s.getDiskUsage(), s.getCapacity(), s.getLoadScore(), s.isHealthy()));
                                        }
                                        logger.info(sb.toString());
                                        System.out.println(GREEN + sb + RESET);
                                    }
                                }
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Failed to display status: {}", errorMsg);
                                System.out.println(RED + "Failed to display status: " + errorMsg + RESET);
                            }
                            break;

                        case 6:
                            try {
                                balancer.checkServerHealth();
                                logger.info("Health check completed");
                                System.out.println(GREEN + "Health check done—servers scanned!" + RESET);
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Health check failed: {}", errorMsg);
                                System.out.println(RED + "Health check failed: " + errorMsg + RESET);
                            }
                            break;

                        case 7:
                            String failId = getNonEmptyStringInput(scanner, "Enter Server ID to fail: ");
                            if (failId == null) continue;

                            try {
                                synchronized (balancer) {
                                    Server failServer = balancer.serverMap.get(failId);
                                    if (failServer != null) {
                                        failServer.setHealthy(false);
                                        logger.warn("Server {} smoked—failover on!", failId);
                                        System.out.println(RED + "Server " + failId + " smoked—failover on!" + RESET);
                                    } else {
                                        logger.error("Server '{}' ain’t here!", failId);
                                        System.out.println(RED + "Server '" + failId + "' ain’t here!" + RESET);
                                    }
                                }
                            } catch (Exception e) {
                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                logger.error("Server failure simulation failed: {}", errorMsg);
                                System.out.println(RED + "Server failure simulation failed: " + errorMsg + RESET);
                            }
                            break;

                        case 8:
                            logger.info("Shutting down—peace out!");
                            System.out.println(GREEN + "Shutting down—peace out!" + RESET);
                            balancer.shutdown();
                            scanner.close();
                            return;

                        default:
                            logger.warn("Invalid choice: {}", choice);
                            System.out.println(RED + "Invalid choice—1-8, big dawg!" + RESET);
                    }
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                    logger.error("Operation failed: {}", errorMsg);
                    System.out.println(RED + "Operation failed: An error occurred. Returning to menu. Details: " + errorMsg + RESET);
                    while (scanner.hasNextLine()) {
                        scanner.nextLine();
                    }
                }
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            logger.error("CLI crashed hard: {}", errorMsg);
            System.out.println(RED + "CLI crashed: " + errorMsg + RESET);
            balancer.shutdown();
            scanner.close();
        }
    }

    private static int getChoiceInput(Scanner scanner) {
        while (true) {
            System.out.print("Enter choice: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                logger.warn("Choice cannot be empty!");
                System.out.println(RED + "Choice cannot be empty—enter 1-8, big dawg!" + RESET);
                continue;
            }
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= 8) {
                    return choice;
                } else {
                    logger.warn("Invalid choice: {}. Must be 1-8.", input);
                    System.out.println(RED + "Invalid choice—1-8 only, big dawg!" + RESET);
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid input: Expected a number between 1 and 8");
                System.out.println(RED + "Invalid input—numbers 1-8 only, big dawg!" + RESET);
            }
        }
    }

    private static String getNonEmptyStringInput(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                logger.warn("Input cannot be empty!");
                System.out.println(RED + "Input cannot be empty!" + RESET);
                continue;
            }
            return input;
        }
    }

    private static String getServerIdInput(Scanner scanner) {
        while (true) {
            System.out.print("Enter Server ID: ");
            String id = scanner.nextLine().trim();
            if (id.isEmpty()) {
                logger.warn("Server ID cannot be empty!");
                System.out.println(RED + "Server ID cannot be empty!" + RESET);
                continue;
            }
            if (id.length() > MAX_SERVER_ID_LENGTH) {
                logger.warn("Server ID too long: {}. Max length is {}.", id.length(), MAX_SERVER_ID_LENGTH);
                System.out.println(RED + "Server ID too long! Max length is " + MAX_SERVER_ID_LENGTH + " characters." + RESET);
                continue;
            }
            if (!id.matches("^[a-zA-Z0-9-]+$")) {
                logger.warn("Invalid Server ID: {}", id);
                System.out.println(RED + "Server ID must contain only letters, numbers, or hyphens!" + RESET);
                continue;
            }
            if (balancer.serverMap.containsKey(id)) {
                logger.warn("Server ID {} already exists!", id);
                System.out.println(RED + "Server ID already in the crew!" + RESET);
                continue;
            }
            return id;
        }
    }

    private static int getValidatedInt(Scanner scanner, String prompt, int min, int max, String fieldName) {
        while (true) {
            System.out.print(prompt.isEmpty() ? "Enter " + fieldName + ": " : prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                logger.warn("Input cannot be empty!");
                System.out.println(RED + "Input cannot be empty!" + RESET);
                continue;
            }
            try {
                int value = Integer.parseInt(input);
                if (value < min || value > max) {
                    logger.warn("Invalid {}: {}. Must be between {} and {}.", fieldName, value, min, max);
                    System.out.println(RED + fieldName + " must be between " + min + " and " + max + "!" + RESET);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                logger.error("Invalid {} input: Expected a number", fieldName);
                System.out.println(RED + "Invalid " + fieldName + " input—numbers only!" + RESET);
            }
        }
    }

    private static double getValidatedDouble(Scanner scanner, String prompt, double min, double max, String fieldName) {
        while (true) {
            System.out.print(prompt.isEmpty() ? "Enter " + fieldName + ": " : prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                logger.warn("Input cannot be empty!");
                System.out.println(RED + "Input cannot be empty!" + RESET);
                continue;
            }
            try {
                double value = Double.parseDouble(input);
                if (value < min || value > max) {
                    logger.warn("Invalid {}: {}. Must be between {} and {}.", fieldName, value, min, max);
                    System.out.println(RED + fieldName + " must be between " + min + " and " + max + "!" + RESET);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                logger.error("Invalid {} input: Expected a number", fieldName);
                System.out.println(RED + "Invalid " + fieldName + " input—numbers only!" + RESET);
                while (scanner.hasNextLine()) {
                    scanner.nextLine();
                }
            }
        }
    }

    private static void showHelp() {
        String help = """
            === LoadBalancerPro CLI Help ===
            Available Commands:
            add       - Add a new server (ID, CPU, Mem, Disk, Capacity)
            import    - Import server logs from file (path, format: csv/json)
            balance   - Balance data across servers (data amount, strategy)
            report    - Generate a report (path, format: csv/json)
            status    - Display current server status
            health    - Check server health
            fail      - Simulate a server failure (ID)
            exit      - Shut down the balancer
            Run: java -cp "bin/classes;lib/*" cli.LoadBalancerCLI
            """;
        logger.info("Help requested:\n{}", help);
        System.out.println(GREEN + help + RESET);
    }
}
