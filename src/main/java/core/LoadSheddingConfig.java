package core;

public record LoadSheddingConfig(
        double softUtilizationThreshold,
        double hardUtilizationThreshold,
        int maxQueueDepth,
        double maxP95LatencyMillis,
        double maxErrorRate,
        boolean criticalBypassEnabled,
        boolean shedUserOnHardPressure) {

    public LoadSheddingConfig {
        requireRate(softUtilizationThreshold, "softUtilizationThreshold");
        requireRate(hardUtilizationThreshold, "hardUtilizationThreshold");
        if (hardUtilizationThreshold < softUtilizationThreshold) {
            throw new IllegalArgumentException(
                    "hardUtilizationThreshold must be greater than or equal to softUtilizationThreshold");
        }
        if (maxQueueDepth < 0) {
            throw new IllegalArgumentException("maxQueueDepth must be non-negative");
        }
        if (!Double.isFinite(maxP95LatencyMillis) || maxP95LatencyMillis <= 0.0) {
            throw new IllegalArgumentException("maxP95LatencyMillis must be finite and positive");
        }
        requireRate(maxErrorRate, "maxErrorRate");
    }

    private static void requireRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
