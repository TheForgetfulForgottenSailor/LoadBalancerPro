package com.richmond423.loadbalancerpro.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;

final class ServerHealthCoordinator {
    private final ServerRegistry serverRegistry;
    private final LoadDistributionEngine loadDistributionEngine;
    private final ConsistentHashRing consistentHashRing;
    private final double maxUsageThreshold;
    private final Logger logger;
    private final Supplier<List<Server>> healthyServers;
    private final Function<Double, Map<String, Double>> leastLoadedDistributor;
    private final Function<ServerType, List<Server>> serversByType;
    private final Supplier<CloudManager> cloudManagerSupplier;

    ServerHealthCoordinator(ServerRegistry serverRegistry,
                            LoadDistributionEngine loadDistributionEngine,
                            ConsistentHashRing consistentHashRing,
                            double maxUsageThreshold,
                            Logger logger,
                            Supplier<List<Server>> healthyServers,
                            Function<Double, Map<String, Double>> leastLoadedDistributor,
                            Function<ServerType, List<Server>> serversByType,
                            Supplier<CloudManager> cloudManagerSupplier) {
        this.serverRegistry = serverRegistry;
        this.loadDistributionEngine = loadDistributionEngine;
        this.consistentHashRing = consistentHashRing;
        this.maxUsageThreshold = maxUsageThreshold;
        this.logger = logger;
        this.healthyServers = healthyServers;
        this.leastLoadedDistributor = leastLoadedDistributor;
        this.serversByType = serversByType;
        this.cloudManagerSupplier = cloudManagerSupplier;
    }

    List<Server> detectFailedServers() {
        List<Server> failedServers = new ArrayList<>();
        for (Server server : serverRegistry.snapshot()) {
            if (server.getCpuUsage() >= maxUsageThreshold || server.getMemoryUsage() >= maxUsageThreshold
                    || server.getDiskUsage() >= maxUsageThreshold || !server.isHealthy()) {
                server.setHealthy(false);
                failedServers.add(server);
                logger.warn("Server {} ({}) marked unhealthy.", server.getServerId(), server.getServerType());
            }
        }
        return failedServers;
    }

    void removeFailedServersAndRecover(List<Server> failedServers) {
        double redistributedData = 0;
        List<Server> failedCloudServers = new ArrayList<>();
        for (Server failed : failedServers) {
            redistributedData += removeFailedServer(failed);
            if (failed.getServerType() == ServerType.CLOUD) {
                failedCloudServers.add(failed);
            }
        }
        redistributeLoad(redistributedData);
        replaceFailedCloudCapacity(failedCloudServers);
    }

    double removeFailedServer(Server failed) {
        double removedData = loadDistributionEngine.removeServerAllocation(failed.getServerId());
        serverRegistry.remove(failed);
        consistentHashRing.removeServer(failed);
        return removedData;
    }

    private void redistributeLoad(double redistributedData) {
        if (redistributedData > 0) {
            List<Server> healthy = healthyServers.get();
            if (healthy.isEmpty()) {
                logger.error("No healthy servers available to redistribute {}GB.", redistributedData);
                return;
            }
            Map<String, Double> newDist = leastLoadedDistributor.apply(redistributedData);
            loadDistributionEngine.putAllAllocations(newDist);
            logger.info("Redistributed {}GB: {}", redistributedData, newDist);
        }
    }

    private void replaceFailedCloudCapacity(List<Server> failedCloudServers) {
        CloudManager cloudManager = cloudManagerSupplier.get();
        if (cloudManager != null && !failedCloudServers.isEmpty()) {
            int minServers = cloudManager.getMinServers();
            int currentCloudServers = serversByType.apply(ServerType.CLOUD).size();
            int desiredCapacity = Math.max(minServers, currentCloudServers + failedCloudServers.size());
            cloudManager.scaleServers(desiredCapacity);
            logger.info("Scaled cloud to {} servers after failover of {} cloud servers.",
                    desiredCapacity, failedCloudServers.size());
        }
    }
}
