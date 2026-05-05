package com.richmond423.loadbalancerpro.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ResourceBundle;

/**
 * Utility class for parsing CLI arguments specific to GuiConfig.
 */
public class CliArgsParser {
    private static final Logger logger = LogManager.getLogger(CliArgsParser.class);
    private static final ResourceBundle messages = ResourceBundle.getBundle("gui.messages");

    public static int getInt(String[] args, String name, int fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    int value = Integer.parseInt(args[i + 1]);
                    logger.info(messages.getString("cli.override"), name, value);
                    return value;
                } catch (NumberFormatException e) {
                    logger.warn(messages.getString("cli.invalid"), name, e.getMessage());
                }
            }
        }
        return fallback;
    }

    public static double getDouble(String[] args, String name, double fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    double value = Double.parseDouble(args[i + 1]);
                    logger.info(messages.getString("cli.override"), name, value);
                    return value;
                } catch (NumberFormatException e) {
                    logger.warn(messages.getString("cli.invalid"), name, e.getMessage());
                }
            }
        }
        return fallback;
    }

    public static long getLong(String[] args, String name, long fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    long value = Long.parseLong(args[i + 1]);
                    logger.info(messages.getString("cli.override"), name, value);
                    return value;
                } catch (NumberFormatException e) {
                    logger.warn(messages.getString("cli.invalid"), name, e.getMessage());
                }
            }
        }
        return fallback;
    }

    public static boolean getBoolean(String[] args, String name, boolean fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                boolean value = Boolean.parseBoolean(args[i + 1]);
                logger.info(messages.getString("cli.override"), name, value);
                return value;
            }
        }
        return fallback;
    }
}
