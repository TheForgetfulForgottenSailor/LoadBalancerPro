package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record LoadSheddingDecision(
        RequestPriority priority,
        Action action,
        String reason,
        Instant timestamp,
        String targetId,
        int currentInFlightRequestCount,
        int concurrencyLimit,
        int queueDepth,
        double utilization,
        double observedP95LatencyMillis,
        double observedErrorRate) {

    public enum Action {
        ALLOW,
        SHED
    }

    public LoadSheddingDecision {
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        targetId = requireNonBlank(targetId, "targetId");
        requireNonNegative(currentInFlightRequestCount, "currentInFlightRequestCount");
        requirePositive(concurrencyLimit, "concurrencyLimit");
        requireNonNegative(queueDepth, "queueDepth");
        requireFiniteNonNegative(utilization, "utilization");
        requireFiniteNonNegative(observedP95LatencyMillis, "observedP95LatencyMillis");
        requireRate(observedErrorRate, "observedErrorRate");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
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
