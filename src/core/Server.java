package core;

import org.json.JSONObject;

/**
 * Represents a server with dynamic metrics and cloud status, managed by the LoadBalancer.
 *
 * This class encapsulates server attributes such as CPU, memory, and disk usage,
 * along with health status, weight, capacity, and cloud instance identification.
 * It supports JSON serialization/deserialization and metric updates.
 *
 * <p><b>UML Diagram:</b></p>
 * <p><img src="loadbalancer.png" alt="Server UML Diagram"></p>
 *
 * @author Richmond Dhaenens
 * @version 112.1
 */
public class Server {
    private String serverId;
    private volatile double cpuUsage;
    private volatile double memoryUsage;
    private volatile double diskUsage;
    private double weight;
    private volatile boolean isHealthy;
    private double capacity;
    private boolean cloudInstance; 

    public Server(String serverId, double cpuUsage, double memoryUsage, double diskUsage) {
        this.serverId = serverId;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.diskUsage = diskUsage;
        this.weight = 1.0;
        this.isHealthy = true;
        this.capacity = 100.0;
        this.cloudInstance = false;
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
        json.put("cloudInstance", cloudInstance);
        return json;
    }

    public static Server fromJson(JSONObject json) {
        Server server = new Server(
            json.getString("serverId"),
            json.optDouble("cpuUsage", 0.0),  
            json.optDouble("memoryUsage", 0.0),
            json.optDouble("diskUsage", 0.0)
        );
        server.setWeight(json.optDouble("weight", 1.0));
        server.setHealthy(json.optBoolean("isHealthy", true));
        server.setCapacity(json.optDouble("capacity", 100.0));
        server.setCloudInstance(json.optBoolean("cloudInstance", false));
        return server;
    }

    public synchronized void updateMetrics(double cpu, double mem, double disk) {
        this.cpuUsage = cpu;
        this.memoryUsage = mem;
        this.diskUsage = disk;
    }

    public String getServerId() { return serverId; }
    public double getCpuUsage() { return cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public double getDiskUsage() { return diskUsage; }
    public double getWeight() { return weight; }
    public boolean isHealthy() { return isHealthy; }
    public double getCapacity() { return capacity; }
    public boolean isCloudInstance() { return cloudInstance; } 

    public void setWeight(double weight) { this.weight = weight; }
    public void setHealthy(boolean healthy) { this.isHealthy = healthy; }
    public void setCapacity(double capacity) { this.capacity = capacity; }
    public void setCloudInstance(boolean cloudInstance) { this.cloudInstance = cloudInstance; } 

    @Override
    public String toString() {
        return String.format(
            "Server[%s]: CPU=%.2f%%, Mem=%.2f%%, Disk=%.2f%%, Weight=%.1f, Healthy=%s, Capacity=%.1f, Cloud=%s",
            serverId, cpuUsage, memoryUsage, diskUsage, weight, isHealthy, capacity, cloudInstance
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Server server = (Server) obj;
        return serverId.equals(server.serverId);
    }

    @Override
    public int hashCode() {
        return serverId.hashCode();
    }
}
