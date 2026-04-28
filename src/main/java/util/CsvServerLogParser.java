package util;

import core.Server;
import core.ServerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Pattern;

final class CsvServerLogParser {
    private static final Logger logger = LogManager.getLogger(CsvServerLogParser.class);
    private static final int CSV_MIN_FIELDS = 4;
    private static final int CSV_CAPACITY_INDEX = 4;
    private static final int CSV_CLOUD_INDEX = 5;
    private static final double DEFAULT_CAPACITY = 100.0;
    private static final double MIN_VALUE = 0.0;
    private static final double MAX_VALUE = 100.0;

    private CsvServerLogParser() {
    }

    static String normalizeDelimiter(String csvDelimiter) {
        String delimiter = csvDelimiter != null ? csvDelimiter : ",";
        if (delimiter.isEmpty()) {
            throw new IllegalArgumentException("CSV delimiter cannot be empty");
        }
        return delimiter;
    }

    static Server parseLine(String line, int lineNum, String delimiter) {
        String[] parts = line.split(Pattern.quote(delimiter), -1);
        if (parts.length < CSV_MIN_FIELDS) {
            throw new IllegalArgumentException("Insufficient fields (" + parts.length + " < " + CSV_MIN_FIELDS + ")");
        }
        String serverId = parts[0].trim();
        if (serverId.isEmpty()) {
            throw new IllegalArgumentException("Server ID cannot be empty");
        }
        double cpu = parseMetric(parts[1].trim(), "CPU", lineNum);
        double mem = parseMetric(parts[2].trim(), "memory", lineNum);
        double disk = parseMetric(parts[3].trim(), "disk", lineNum);
        double capacity = parts.length > CSV_CAPACITY_INDEX
                ? parseNonNegativeDouble(parts[CSV_CAPACITY_INDEX].trim(), "capacity")
                : DEFAULT_CAPACITY;
        ServerType serverType = determineServerType(parts, serverId, lineNum);
        Server server = new Server(serverId, cpu, mem, disk, serverType);
        server.setCapacity(capacity);
        return server;
    }

    private static double parseMetric(String value, String field, int lineNum) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new IllegalArgumentException(field + " must be finite: " + value);
            }
            if (parsed < MIN_VALUE || parsed > MAX_VALUE) {
                logger.warn("Line {}: {} value {} out of range [0, 100]; clamping.", lineNum, field, parsed);
                return clamp(parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be numeric: " + value);
        }
    }

    private static double parseNonNegativeDouble(String value, String field) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < MIN_VALUE) {
                throw new IllegalArgumentException(field + " must be non-negative: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be numeric: " + value);
        }
    }

    private static ServerType determineServerType(String[] parts, String serverId, int lineNum) {
        if (parts.length > CSV_CLOUD_INDEX) {
            String isCloudStr = parts[CSV_CLOUD_INDEX].trim().toLowerCase();
            return Boolean.parseBoolean(isCloudStr) || isCloudStr.equals("1")
                    ? ServerType.CLOUD
                    : ServerType.ONSITE;
        }
        ServerType inferred = serverId.startsWith("AWS-") ? ServerType.CLOUD : ServerType.ONSITE;
        logger.info("Line {}: Inferred serverType for {}: {}", lineNum, serverId, inferred);
        return inferred;
    }

    private static double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }
}
