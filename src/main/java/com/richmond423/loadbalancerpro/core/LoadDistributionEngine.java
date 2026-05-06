package com.richmond423.loadbalancerpro.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LoadDistributionEngine {
    private final Map<String, Double> currentDistribution = new ConcurrentHashMap<>();
    private final double predictiveLoadFactor;

    LoadDistributionEngine(double predictiveLoadFactor) {
        this.predictiveLoadFactor = predictiveLoadFactor;
    }

    Map<String, Double> roundRobin(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = LoadDistributionPlanner.roundRobin(healthyServers, totalData);
        accumulate(distribution);
        DomainMetrics.recordAllocation("ROUND_ROBIN", healthyServers.size(), 0.0);
        return distribution;
    }

    Map<String, Double> leastLoaded(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = LoadDistributionPlanner.leastLoaded(healthyServers, totalData);
        accumulate(distribution);
        DomainMetrics.recordAllocation("LEAST_LOADED", healthyServers.size(), 0.0);
        return distribution;
    }

    Map<String, Double> weightedDistribution(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = LoadDistributionPlanner.weighted(healthyServers, totalData);
        accumulate(distribution);
        DomainMetrics.recordAllocation("WEIGHTED", healthyServers.size(), 0.0);
        return distribution;
    }

    LoadDistributionResult capacityAwareWithResult(List<Server> healthyServers, double totalData) {
        LoadDistributionResult result = LoadDistributionPlanner.capacityAwareResult(healthyServers, totalData);
        accumulate(result.allocations());
        DomainMetrics.recordAllocation("CAPACITY_AWARE", healthyServers.size(), result.unallocatedLoad());
        return result;
    }

    LoadDistributionResult predictiveLoadBalancingWithResult(List<Server> healthyServers, double totalData) {
        LoadDistributionResult result = LoadDistributionPlanner.predictiveResult(
                healthyServers, totalData, calculatePredictedLoads(healthyServers));
        accumulate(result.allocations());
        DomainMetrics.recordAllocation("PREDICTIVE", healthyServers.size(), result.unallocatedLoad());
        return result;
    }

    void accumulate(String serverId, double allocation) {
        currentDistribution.merge(serverId, allocation, Double::sum);
    }

    double removeServerAllocation(String serverId) {
        Double data = currentDistribution.remove(serverId);
        return data != null ? data : 0;
    }

    void putAllAllocations(Map<String, Double> allocations) {
        currentDistribution.putAll(allocations);
    }

    double totalAccumulatedLoad() {
        return currentDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    void clearAccumulatedLoad() {
        currentDistribution.clear();
    }

    private void accumulate(Map<String, Double> allocations) {
        for (Map.Entry<String, Double> entry : allocations.entrySet()) {
            currentDistribution.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    private Map<String, Double> calculatePredictedLoads(List<Server> servers) {
        Map<String, Double> predicted = new HashMap<>();
        for (Server server : servers) {
            predicted.put(server.getServerId(), server.getLoadScore() * predictiveLoadFactor);
        }
        return predicted;
    }
}
