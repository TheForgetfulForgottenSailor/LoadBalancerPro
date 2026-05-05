package com.richmond423.loadbalancerpro.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;

public class ConsoleUtils {
    private static final Logger logger = LogManager.getLogger(ConsoleUtils.class);
    private final Scanner scanner;
    private final CliConfig config;

    public ConsoleUtils(Scanner scanner, CliConfig config) {
        this.scanner = scanner;
        this.config = config;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public boolean isScannerClosed() {
        try {
            scanner.hasNext();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public void clearBuffer() {
        while (scanner.hasNextLine()) scanner.nextLine();
    }

    public String promptForString(String prompt, int maxLength) {
        if (isScannerClosed()) return null;
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (input.isEmpty() || input.length() > maxLength) {
            logger.warn("Invalid input: length must be 1-{}", maxLength);
            printError("Input must be non-empty and <= " + maxLength + " characters.");
            return null;
        }
        return input;
    }

    public double promptForDouble(String prompt, double min, double max, String fieldName) {
        int attempts = 0;
        final int maxAttempts = 3;
        while (attempts < maxAttempts && !isScannerClosed()) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(scanner.nextLine().trim());
                if (value < min || value > max) {
                    logger.warn("Invalid {}: {} (must be between {} and {})", fieldName, value, min, max);
                    printError(fieldName + " must be between " + min + " and " + max + ". Try again? (y/n)");
                    if (scanner.nextLine().trim().toLowerCase().equals("y")) {
                        attempts++;
                        continue;
                    }
                    return -1;
                }
                return value;
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} input: {}", fieldName, e.getMessage());
                printError("Invalid " + fieldName + " input. Enter a number. Try again? (y/n)");
                if (scanner.nextLine().trim().toLowerCase().equals("y")) {
                    attempts++;
                    continue;
                }
                return -1;
            }
        }
        printError("Max attempts (" + maxAttempts + ") reached for " + fieldName + ".");
        return -1;
    }

    public int promptForInt(String prompt, int min, int max, String fieldName) {
        int attempts = 0;
        final int maxAttempts = 3;
        while (attempts < maxAttempts && !isScannerClosed()) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (value < min || value > max) {
                    logger.warn("Invalid {}: {} (must be between {} and {})", fieldName, value, min, max);
                    printError(fieldName + " must be between " + min + " and " + max + ". Try again? (y/n)");
                    if (scanner.nextLine().trim().toLowerCase().equals("y")) {
                        attempts++;
                        continue;
                    }
                    return -1;
                }
                return value;
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} input: {}", fieldName, e.getMessage());
                printError("Invalid " + fieldName + " input. Enter an integer. Try again? (y/n)");
                if (scanner.nextLine().trim().toLowerCase().equals("y")) {
                    attempts++;
                    continue;
                }
                return -1;
            }
        }
        printError("Max attempts (" + maxAttempts + ") reached for " + fieldName + ".");
        return -1;
    }

    private void printError(String message) {
        System.out.println(config.getErrorColor() + message + config.getResetColor());
        logger.error(message);
    }
}
