package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record AutoscalingSignal(
        String targetId,
        int currentCapacity,
        int minCapacity,
        int maxCapacity,
        int currentInFlightRequestCount,
        int queueDepth,
        double observedP95LatencyMillis,
        double observedP99LatencyMillis,
        double observedErrorRate,
        int sampleSize,
        Instant timestamp) {

    public AutoscalingSignal {
        targetId = requireNonBlank(targetId, "targetId");
        requirePositive(currentCapacity, "currentCapacity");
        requirePositive(minCapacity, "minCapacity");
        if (maxCapacity < minCapacity) {
            throw new IllegalArgumentException("maxCapacity must be greater than or equal to minCapacity");
        }
        if (currentCapacity < minCapacity || currentCapacity > maxCapacity) {
            throw new IllegalArgumentException("currentCapacity must be within minCapacity and maxCapacity");
        }
        requireNonNegative(currentInFlightRequestCount, "currentInFlightRequestCount");
        requireNonNegative(queueDepth, "queueDepth");
        requireFiniteNonNegative(observedP95LatencyMillis, "observedP95LatencyMillis");
        requireFiniteNonNegative(observedP99LatencyMillis, "observedP99LatencyMillis");
        requireRate(observedErrorRate, "observedErrorRate");
        requireNonNegative(sampleSize, "sampleSize");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public double utilization() {
        return currentInFlightRequestCount / (double) currentCapacity;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static void requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static void requireFiniteNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }

    private static void requireRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
