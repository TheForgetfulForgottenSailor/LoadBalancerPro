package gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Utility class for loading and parsing configuration properties.
 */
public class ConfigLoader {
    private static final Logger logger = LogManager.getLogger(ConfigLoader.class);
    private static final ResourceBundle messages = ResourceBundle.getBundle("gui.messages");
    private static boolean loadErrors = false;

    public static Properties loadProperties(String configFile, Consumer<String> errorCallback) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            loadErrors = false;
        } catch (Exception e) {
            String msg = String.format(messages.getString("config.load.failed"), configFile, e.getMessage());
            logger.warn(msg);
            errorCallback.accept(msg);
            loadErrors = true;
        }
        return props;
    }

    public static boolean hasLoadErrors() {
        return loadErrors;
    }

    public static int parseInt(Properties props, String key, int defaultValue, int min, int max) {
        try {
            int val = Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                logger.warn(messages.getString("config.range"), key, val, min, max, defaultValue);
                return defaultValue;
            }
            return val;
        } catch (Exception e) {
            logger.info(messages.getString("config.default"), key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    public static double parseDouble(Properties props, String key, double defaultValue, double min, double max) {
        try {
            double val = Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                logger.warn(messages.getString("config.range"), key, val, min, max, defaultValue);
                return defaultValue;
            }
            return val;
        } catch (Exception e) {
            logger.info(messages.getString("config.default"), key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    public static long parseLong(Properties props, String key, long defaultValue, long min, long max) {
        try {
            long val = Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
            if (val < min || val > max) {
                logger.warn(messages.getString("config.range"), key, val, min, max, defaultValue);
                return defaultValue;
            }
            return val;
        } catch (Exception e) {
            logger.info(messages.getString("config.default"), key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    public static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));
    }
}
