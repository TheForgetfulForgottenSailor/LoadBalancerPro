package core;

public record ShadowAutoscalerConfig(
        double targetP95LatencyMillis,
        double targetP99LatencyMillis,
        double maxErrorRate,
        int queueScaleUpThreshold,
        double utilizationScaleUpThreshold,
        double utilizationScaleDownThreshold,
        int scaleUpStep,
        int scaleDownStep,
        int minSampleSize) {

    public ShadowAutoscalerConfig {
        requireFinitePositive(targetP95LatencyMillis, "targetP95LatencyMillis");
        requireFinitePositive(targetP99LatencyMillis, "targetP99LatencyMillis");
        requireRate(maxErrorRate, "maxErrorRate");
        if (queueScaleUpThreshold < 0) {
            throw new IllegalArgumentException("queueScaleUpThreshold must be non-negative");
        }
        requireRate(utilizationScaleUpThreshold, "utilizationScaleUpThreshold");
        requireRate(utilizationScaleDownThreshold, "utilizationScaleDownThreshold");
        if (utilizationScaleDownThreshold >= utilizationScaleUpThreshold) {
            throw new IllegalArgumentException(
                    "utilizationScaleDownThreshold must be lower than utilizationScaleUpThreshold");
        }
        requirePositive(scaleUpStep, "scaleUpStep");
        requirePositive(scaleDownStep, "scaleDownStep");
        if (minSampleSize < 0) {
            throw new IllegalArgumentException("minSampleSize must be non-negative");
        }
    }

    private static void requireFinitePositive(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and positive");
        }
    }

    private static void requireRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
