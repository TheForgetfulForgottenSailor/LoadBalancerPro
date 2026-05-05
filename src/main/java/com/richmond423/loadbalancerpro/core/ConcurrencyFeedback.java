package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record ConcurrencyFeedback(
        String serverId,
        int currentInFlightRequestCount,
        double observedAverageLatencyMillis,
        double observedP95LatencyMillis,
        double observedP99LatencyMillis,
        double observedErrorRate,
        int sampleSize,
        Instant timestamp) {

    public ConcurrencyFeedback {
        serverId = requireNonBlank(serverId, "serverId");
        requireNonNegative(currentInFlightRequestCount, "currentInFlightRequestCount");
        requireFiniteNonNegative(observedAverageLatencyMillis, "observedAverageLatencyMillis");
        requireFiniteNonNegative(observedP95LatencyMillis, "observedP95LatencyMillis");
        requireFiniteNonNegative(observedP99LatencyMillis, "observedP99LatencyMillis");
        requireRate(observedErrorRate, "observedErrorRate");
        requireNonNegative(sampleSize, "sampleSize");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
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
