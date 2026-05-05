package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record FailureScenarioSignal(
        String scenarioId,
        FailureScenarioType scenarioType,
        String targetId,
        int totalServers,
        int healthyServers,
        int currentInFlightRequestCount,
        int concurrencyLimit,
        int queueDepth,
        double observedP95LatencyMillis,
        double observedP99LatencyMillis,
        double observedErrorRate,
        int sampleSize,
        Instant timestamp) {

    public FailureScenarioSignal {
        scenarioId = requireNonBlank(scenarioId, "scenarioId");
        Objects.requireNonNull(scenarioType, "scenarioType cannot be null");
        targetId = requireNonBlank(targetId, "targetId");
        requirePositive(totalServers, "totalServers");
        requireNonNegative(healthyServers, "healthyServers");
        if (healthyServers > totalServers) {
            throw new IllegalArgumentException("healthyServers cannot exceed totalServers");
        }
        requireNonNegative(currentInFlightRequestCount, "currentInFlightRequestCount");
        requirePositive(concurrencyLimit, "concurrencyLimit");
        requireNonNegative(queueDepth, "queueDepth");
        requireFiniteNonNegative(observedP95LatencyMillis, "observedP95LatencyMillis");
        requireFiniteNonNegative(observedP99LatencyMillis, "observedP99LatencyMillis");
        requireRate(observedErrorRate, "observedErrorRate");
        requireNonNegative(sampleSize, "sampleSize");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public double healthyRatio() {
        return healthyServers / (double) totalServers;
    }

    public double utilization() {
        return currentInFlightRequestCount / (double) concurrencyLimit;
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
