package util;

import core.LoadBalancer;
import core.Server;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class providing helper methods for the LoadBalancerPro system.
 *
 * This class contains static methods for hashing keys, importing server logs, and exporting reports.
 * It supports both CSV and JSON formats for data import/export and includes logging for debugging and tracking.
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Generates hash values for keys using MD5 or fallback to default hashCode.</li>
 *   <li>Imports server logs in CSV or JSON format to populate a {@link LoadBalancer}.</li>
 *   <li>Exports server reports and alerts in CSV or JSON format.</li>
 *   <li>Uses Log4j for structured logging of operations.</li>
 * </ul>
 *
 * <p><b>UML Diagram:</b></p>
 * <p><img src="doc/utils.png" alt="Utils UML Diagram"></p>
 *
 * @author Richmond Dhaenens
 * @version 17.0
 */
public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);

    /**
     * Generates a hash value for a given key using the MD5 algorithm.
     * If MD5 is unavailable, falls back to the key's default {@code hashCode}.
     *
     * @param key the string key to hash
     * @return a long hash value for the key
     */
    public static long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes());
            return new BigInteger(1, bytes).longValue();
        } catch (Exception e) {
            return key.hashCode();
        }
    }

    /**
     * Imports server logs from a file into a {@link LoadBalancer}.
     *
     * Supports CSV and JSON formats. For CSV, each line represents a server with
     * fields for server ID, CPU usage, memory usage, disk usage, capacity (optional),
     * and cloud instance status (optional). For JSON, the file should contain an array
     * of server objects. Logs the import process using Log4j.
     *
     * @param filename the name of the file containing server logs
     * @param format the format of the file ("csv" or "json")
     * @param balancer the {@link LoadBalancer} to add servers to
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the format is unsupported
     */
    public static void importServerLogs(String filename, String format, LoadBalancer balancer) throws IOException {
        logger.info("Starting importServerLogs with filename: {}, format: {}", filename, format); // Debug log
        synchronized (balancer) {
            if (format.equalsIgnoreCase("csv")) {
                try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        logger.info("Raw CSV line: {}", line); // Debug log for raw line
                        String[] parts = line.split(",");
                        logger.info("Number of parts: {}", parts.length); // Debug log for parts length
                        logger.info("Parts for line: {}", String.join(",", parts)); // Debug log for parts
                        if (parts.length >= 4) {
                            Server server = new Server(parts[0].trim(),
                                    Double.parseDouble(parts[1].trim()),
                                    Double.parseDouble(parts[2].trim()),
                                    Double.parseDouble(parts[3].trim()));
                            if (parts.length > 4) {
                                server.setCapacity(Double.parseDouble(parts[4].trim()));
                            }
                            if (parts.length > 5) {
                                String isCloudStr = parts[5].trim().toLowerCase();
                                logger.info("isCloudStr for {}: {}", parts[0], isCloudStr); // Debug log for raw value
                                boolean isCloud = isCloudStr.equals("true") || isCloudStr.equals("1");
                                logger.info("Parsed isCloud for {}: {}", parts[0], isCloud); // Debug log for parsed value
                                server.setCloudInstance(isCloud);
                            }
                            balancer.addServer(server);
                        }
                    }
                }
            } else if (format.equalsIgnoreCase("json")) {
                try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                    StringBuilder jsonString = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) jsonString.append(line);
                    JSONArray jsonArray = new JSONArray(jsonString.toString());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject json = jsonArray.getJSONObject(i);
                        Server server = Server.fromJson(json);
                        if (json.has("capacity")) server.setCapacity(json.getDouble("capacity"));
                        balancer.addServer(server);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format + ". Use 'csv' or 'json'");
            }
        }
    }

    /**
     * Exports a report of server metrics and alerts to a file.
     *
     * Supports CSV and JSON formats. For CSV, the report includes a timestamp, server metrics
     * (ID, CPU usage, memory usage, disk usage, capacity, load score, health), and alerts.
     * For JSON, the report is structured as a JSON object with a timestamp, array of servers,
     * and array of alerts.
     *
     * @param filename the name of the file to write the report to
     * @param format the format of the report ("csv" or "json")
     * @param servers the list of {@link Server} objects to include in the report
     * @param alertLog the list of alert messages to include in the report
     * @throws IOException if an I/O error occurs while writing the file
     * @throws IllegalArgumentException if the format is unsupported
     */
    public static void exportReport(String filename, String format, List<Server> servers, List<String> alertLog) throws IOException {
        synchronized (servers) {
            if (format.equalsIgnoreCase("csv")) {
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    bw.write("Server Load Report - " + timestamp + "\n");
                    bw.write("Server ID,CPU Usage,Memory Usage,Disk Usage,Capacity,Load Score,Healthy\n");
                    for (Server server : servers) {
                        bw.write(String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%b\n",
                                server.getServerId(), server.getCpuUsage(), server.getMemoryUsage(),
                                server.getDiskUsage(), server.getCapacity(), server.getLoadScore(),
                                server.isHealthy()));
                    }
                    bw.write("\nAlerts:\n");
                    for (String alert : alertLog) {
                        bw.write(alert + "\n");
                    }
                }
            } else if (format.equalsIgnoreCase("json")) {
                JSONObject report = new JSONObject();
                report.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                JSONArray serverArray = new JSONArray();
                for (Server server : servers) {
                    serverArray.put(server.toJson());
                }
                report.put("servers", serverArray);
                report.put("alerts", new JSONArray(alertLog));
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
                    bw.write(report.toString(4));
                }
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format + ". Use 'csv' or 'json'");
            }
        }
    }
}
