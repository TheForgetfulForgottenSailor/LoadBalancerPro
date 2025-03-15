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

public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);

    public static long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes());
            return new BigInteger(1, bytes).longValue();
        } catch (Exception e) {
            return key.hashCode();
        }
    }

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
