package com.richmond423.loadbalancerpro.core;

public record FailureScenarioConfig(
        int highQueueDepthThreshold,
        double highP95LatencyMillis,
        double highP99LatencyMillis,
        double highErrorRate,
        double saturationUtilizationThreshold,
        double partialOutageHealthyRatioThreshold,
        int minSampleSize) {

    public FailureScenarioConfig {
        if (highQueueDepthThreshold < 0) {
            throw new IllegalArgumentException("highQueueDepthThreshold must be non-negative");
        }
        requireFinitePositive(highP95LatencyMillis, "highP95LatencyMillis");
        requireFinitePositive(highP99LatencyMillis, "highP99LatencyMillis");
        requireRate(highErrorRate, "highErrorRate");
        requireRate(saturationUtilizationThreshold, "saturationUtilizationThreshold");
        requireRate(partialOutageHealthyRatioThreshold, "partialOutageHealthyRatioThreshold");
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
}
