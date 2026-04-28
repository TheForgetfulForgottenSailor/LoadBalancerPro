package api;

public record ScalingSimulationResult(
        int recommendedAdditionalServers,
        String reason,
        boolean simulatedOnly) {
}
