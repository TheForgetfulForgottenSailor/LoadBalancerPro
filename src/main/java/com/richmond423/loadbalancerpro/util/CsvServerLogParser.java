package com.richmond423.loadbalancerpro.util;

import com.richmond423.loadbalancerpro.core.Server;
import com.richmond423.loadbalancerpro.core.ServerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CsvServerLogParser {
    private static final Logger logger = LogManager.getLogger(CsvServerLogParser.class);
    private static final int CSV_MIN_FIELDS = 4;
    private static final int CSV_MAX_FIELDS = 6;
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
        if (delimiter.contains("\"") || delimiter.contains("\n") || delimiter.contains("\r")) {
            throw new IllegalArgumentException("CSV delimiter cannot contain quotes or line breaks");
        }
        return delimiter;
    }

    static Server parseLine(String line, int lineNum, String delimiter) {
        List<String> parts = parseFields(line, lineNum, delimiter);
        if (parts.size() < CSV_MIN_FIELDS) {
            throw validationError(lineNum, "schema",
                    "expected 4 to 6 columns but found " + parts.size());
        }
        if (parts.size() > CSV_MAX_FIELDS) {
            throw validationError(lineNum, "schema",
                    "expected 4 to 6 columns but found " + parts.size());
        }
        String serverId = parts.get(0).trim();
        if (serverId.isEmpty()) {
            throw validationError(lineNum, "serverId", "must not be blank");
        }
        if (isFormulaLike(serverId)) {
            throw validationError(lineNum, "serverId", "must not start with formula syntax");
        }
        double cpu = parseMetric(parts.get(1).trim(), "CPU", lineNum);
        double mem = parseMetric(parts.get(2).trim(), "memory", lineNum);
        double disk = parseMetric(parts.get(3).trim(), "disk", lineNum);
        double capacity = parts.size() > CSV_CAPACITY_INDEX
                ? parseNonNegativeDouble(parts.get(CSV_CAPACITY_INDEX).trim(), "capacity", lineNum)
                : DEFAULT_CAPACITY;
        ServerType serverType = determineServerType(parts, serverId, lineNum);
        Server server = new Server(serverId, cpu, mem, disk, serverType);
        server.setCapacity(capacity);
        return server;
    }

    static boolean isRecordComplete(String record) {
        boolean inQuotes = false;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            }
        }
        return !inQuotes;
    }

    static boolean isFormulaLike(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.stripLeading();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@';
    }

    static String escapeForCsv(String value) {
        String safeValue = value != null ? value : "";
        if (isFormulaLike(safeValue)) {
            safeValue = "'" + safeValue;
        }
        boolean mustQuote = safeValue.contains(",")
                || safeValue.contains("\"")
                || safeValue.contains("\n")
                || safeValue.contains("\r");
        if (!mustQuote) {
            return safeValue;
        }
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private static List<String> parseFields(String record, int lineNum, String delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        boolean quoteClosed = false;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < record.length() && record.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (matchesDelimiter(record, i, delimiter)) {
                fields.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                quoteClosed = false;
                i += delimiter.length() - 1;
                continue;
            }

            if (c == '"') {
                if (!fieldStarted || field.toString().trim().isEmpty()) {
                    field.setLength(0);
                    fieldStarted = true;
                    inQuotes = true;
                    continue;
                }
                throw validationError(lineNum, "schema", "unexpected quote in unquoted field");
            }

            if (quoteClosed) {
                if (Character.isWhitespace(c)) {
                    continue;
                }
                throw validationError(lineNum, "schema", "unexpected character after closing quote");
            }

            field.append(c);
            fieldStarted = true;
        }

        if (inQuotes) {
            throw validationError(lineNum, "schema", "unterminated quoted field");
        }
        fields.add(field.toString());
        return fields;
    }

    private static boolean matchesDelimiter(String record, int index, String delimiter) {
        return index + delimiter.length() <= record.length()
                && record.startsWith(delimiter, index);
    }

    private static double parseMetric(String value, String field, int lineNum) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw validationError(lineNum, field, "must be finite");
            }
            if (parsed < MIN_VALUE || parsed > MAX_VALUE) {
                logger.warn("Line {}: {} value {} out of range [0, 100]; clamping.", lineNum, field, parsed);
                return clamp(parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw validationError(lineNum, field, "must be numeric");
        }
    }

    private static double parseNonNegativeDouble(String value, String field, int lineNum) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < MIN_VALUE) {
                throw validationError(lineNum, field, "must be non-negative and finite");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw validationError(lineNum, field, "must be numeric");
        }
    }

    private static ServerType determineServerType(List<String> parts, String serverId, int lineNum) {
        if (parts.size() > CSV_CLOUD_INDEX) {
            String isCloudStr = parts.get(CSV_CLOUD_INDEX).trim().toLowerCase(Locale.ROOT);
            if (isCloudStr.isEmpty() || isCloudStr.equals("false") || isCloudStr.equals("0")) {
                return ServerType.ONSITE;
            }
            if (isCloudStr.equals("true") || isCloudStr.equals("1")) {
                return ServerType.CLOUD;
            }
            throw validationError(lineNum, "cloud", "must be true, false, 1, 0, or blank");
        }
        ServerType inferred = serverId.startsWith("AWS-") ? ServerType.CLOUD : ServerType.ONSITE;
        logger.info("Line {}: Inferred serverType for {}: {}", lineNum, serverId, inferred);
        return inferred;
    }

    private static CsvParseException validationError(int lineNum, String field, String reason) {
        return new CsvParseException(lineNum, field, reason);
    }

    private static double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    static final class CsvParseException extends IllegalArgumentException {
        private final int lineNumber;
        private final String field;
        private final String reason;

        CsvParseException(int lineNumber, String field, String reason) {
            super("line=" + lineNumber + ", field=" + field + ", reason=" + reason);
            this.lineNumber = lineNumber;
            this.field = field;
            this.reason = reason;
        }

        int getLineNumber() {
            return lineNumber;
        }

        String getField() {
            return field;
        }

        String getReason() {
            return reason;
        }
    }
}
