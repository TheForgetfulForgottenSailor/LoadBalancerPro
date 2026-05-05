package com.richmond423.loadbalancerpro.api;

public record ScalingSimulationResult(
        int recommendedAdditionalServers,
        String reason,
        boolean simulatedOnly) {
}
