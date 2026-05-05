package com.richmond423.loadbalancerpro.core;

public record AdaptiveConcurrencyConfig(
        int minLimit,
        int maxLimit,
        int additiveStep,
        double decreaseFactor,
        double targetP95LatencyMillis,
        double maxErrorRate,
        int minSampleSize) {

    public AdaptiveConcurrencyConfig {
        if (minLimit < 1) {
            throw new IllegalArgumentException("minLimit must be at least 1");
        }
        if (maxLimit < minLimit) {
            throw new IllegalArgumentException("maxLimit must be greater than or equal to minLimit");
        }
        if (additiveStep < 1) {
            throw new IllegalArgumentException("additiveStep must be at least 1");
        }
        if (!Double.isFinite(decreaseFactor) || decreaseFactor <= 0.0 || decreaseFactor >= 1.0) {
            throw new IllegalArgumentException("decreaseFactor must be finite and between 0.0 and 1.0");
        }
        if (!Double.isFinite(targetP95LatencyMillis) || targetP95LatencyMillis <= 0.0) {
            throw new IllegalArgumentException("targetP95LatencyMillis must be finite and positive");
        }
        if (!Double.isFinite(maxErrorRate) || maxErrorRate < 0.0 || maxErrorRate > 1.0) {
            throw new IllegalArgumentException("maxErrorRate must be between 0.0 and 1.0");
        }
        if (minSampleSize < 0) {
            throw new IllegalArgumentException("minSampleSize must be non-negative");
        }
    }
}
