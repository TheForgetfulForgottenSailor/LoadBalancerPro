package api;

public record ServerInput(
        String id,
        double cpuUsage,
        double memoryUsage,
        double diskUsage,
        double capacity,
        double weight,
        boolean healthy) {
}
