package core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LoadDistributionPlanner {
    private LoadDistributionPlanner() {
    }

    static Map<String, Double> roundRobin(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = new HashMap<>();
        double dataPerServer = totalData / healthyServers.size();
        for (Server server : healthyServers) {
            distribution.put(server.getServerId(), dataPerServer);
        }
        return distribution;
    }

    static Map<String, Double> leastLoaded(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = new HashMap<>();
        List<Server> sorted = healthyServers.stream()
                .sorted(Comparator.comparingDouble(Server::getLoadScore))
                .toList();
        double remaining = totalData;
        for (Server server : sorted) {
            double allocation = Math.min(remaining, totalData / sorted.size());
            distribution.put(server.getServerId(), allocation);
            remaining -= allocation;
            if (remaining <= 0) {
                break;
            }
        }
        return distribution;
    }

    static Map<String, Double> weighted(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = new HashMap<>();
        double totalWeight = healthyServers.stream().mapToDouble(Server::getWeight).sum();
        if (totalWeight <= 0) {
            return roundRobin(healthyServers, totalData);
        }
        for (Server server : healthyServers) {
            double allocation = (server.getWeight() / totalWeight) * totalData;
            distribution.put(server.getServerId(), allocation);
        }
        return distribution;
    }

    static Map<String, Double> capacityAware(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        List<Server> sorted = healthyServers.stream()
                .filter(s -> availableCapacity(s) > 0)
                .sorted(Comparator.comparingDouble((Server s) -> s.getLoadScore() / s.getCapacity())
                        .thenComparing(Server::getServerId))
                .toList();
        double totalCapacity = sorted.stream()
                .mapToDouble(LoadDistributionPlanner::availableCapacity)
                .sum();
        double remaining = totalData;
        for (Server server : sorted) {
            double availableCapacity = availableCapacity(server);
            double proportionalAllocation = (availableCapacity / totalCapacity) * totalData;
            double allocation = Math.min(Math.min(remaining, proportionalAllocation), availableCapacity);
            distribution.put(server.getServerId(), allocation);
            remaining -= allocation;
            if (remaining <= 0) {
                break;
            }
        }
        return distribution;
    }

    private static double availableCapacity(Server server) {
        return server.getCapacity() - server.getLoadScore();
    }
}
