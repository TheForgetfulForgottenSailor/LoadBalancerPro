package core;

import org.json.JSONObject;

public class Server {
    private String serverId;
    private volatile double cpuUsage;
    private volatile double memoryUsage;
    private volatile double diskUsage;
    private double weight;
    private volatile boolean isHealthy;
    private double capacity;
    private boolean cloudInstance; // New field to track cloud status

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage) {
        this.serverId = serverId;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.diskUsage = diskUsage;
        this.weight = 1.0;
        this.isHealthy = true;
        this.capacity = 100.0;
        this.cloudInstance = false; // Default to false
    }

    public double getLoadScore() {
        return (cpuUsage * 0.4) + (memoryUsage * 0.3) + (diskUsage * 0.3);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("serverId", serverId);
        json.put("cpuUsage", cpuUsage);
        json.put("memoryUsage", memoryUsage);
        json.put("diskUsage", diskUsage);
        json.put("weight", weight);
        json.put("isHealthy", isHealthy);
        json.put("capacity", capacity);
        json.put("cloudInstance", cloudInstance); // Include cloud instance in JSON
        return json;
    }

    public static Server fromJson(JSONObject json) {
        Server server = new Server(
            json.getString("serverId"),
            json.getDouble("cpuUsage"),
            json.getDouble("memoryUsage"),
            json.getDouble("diskUsage")
        );
        if (json.has("weight")) server.setWeight(json.getDouble("weight"));
        if (json.has("isHealthy")) server.setHealthy(json.getBoolean("isHealthy"));
        if (json.has("capacity")) server.setCapacity(json.getDouble("capacity"));
        if (json.has("cloudInstance")) server.setCloudInstance(json.getBoolean("cloudInstance")); // Restore cloud status
        return server;
    }

    public String getServerId() { return serverId; }
    public double getCpuUsage() { return cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public double getDiskUsage() { return diskUsage; }
    public double getWeight() { return weight; }
    public boolean isHealthy() { return isHealthy; }
    public double getCapacity() { return capacity; }
    public boolean isCloudInstance() { return cloudInstance; } // Getter for cloud instance
    public void setWeight(double weight) { this.weight = weight; }
    public void setHealthy(boolean healthy) { this.isHealthy = healthy; }
    public void setCapacity(double capacity) { this.capacity = capacity; }
    public void setCloudInstance(boolean cloudInstance) { this.cloudInstance = cloudInstance; } // Setter for cloud instance
    public void updateMetrics(double cpu, double mem, double disk) {
        this.cpuUsage = cpu;
        this.memoryUsage = mem;
        this.diskUsage = disk;
    }
}
