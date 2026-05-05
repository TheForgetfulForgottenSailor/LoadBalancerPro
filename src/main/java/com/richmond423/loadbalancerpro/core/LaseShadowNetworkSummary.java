package com.richmond423.loadbalancerpro.core;

public record LaseShadowNetworkSummary(
        double averageTimeoutRate,
        double averageRetryRate,
        double averageConnectionFailureRate,
        double maxLatencyJitterMillis,
        long recentErrorBurstCount,
        long totalRequestTimeoutCount) {

    public LaseShadowNetworkSummary {
        requireRate(averageTimeoutRate, "averageTimeoutRate");
        requireRate(averageRetryRate, "averageRetryRate");
        requireRate(averageConnectionFailureRate, "averageConnectionFailureRate");
        requireFiniteNonNegative(maxLatencyJitterMillis, "maxLatencyJitterMillis");
        requireNonNegative(recentErrorBurstCount, "recentErrorBurstCount");
        requireNonNegative(totalRequestTimeoutCount, "totalRequestTimeoutCount");
    }

    public static LaseShadowNetworkSummary empty() {
        return new LaseShadowNetworkSummary(0.0, 0.0, 0.0, 0.0, 0, 0);
    }

    private static void requireRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void requireFiniteNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }

    private static void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }
}
