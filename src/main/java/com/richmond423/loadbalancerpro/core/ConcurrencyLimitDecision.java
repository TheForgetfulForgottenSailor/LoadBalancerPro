package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record ConcurrencyLimitDecision(
        String serverId,
        int previousLimit,
        int nextLimit,
        int minLimit,
        int maxLimit,
        Action action,
        String reason,
        Instant timestamp,
        int currentInFlightRequestCount,
        double observedAverageLatencyMillis,
        double observedP95LatencyMillis,
        double observedP99LatencyMillis,
        double observedErrorRate,
        int sampleSize) {

    public enum Action {
        INCREASE,
        DECREASE,
        HOLD,
        CLAMP
    }

    public ConcurrencyLimitDecision {
        serverId = requireNonBlank(serverId, "serverId");
        requirePositive(previousLimit, "previousLimit");
        requirePositive(nextLimit, "nextLimit");
        if (minLimit < 1) {
            throw new IllegalArgumentException("minLimit must be at least 1");
        }
        if (maxLimit < minLimit) {
            throw new IllegalArgumentException("maxLimit must be greater than or equal to minLimit");
        }
        if (nextLimit < minLimit || nextLimit > maxLimit) {
            throw new IllegalArgumentException("nextLimit must be within minLimit and maxLimit");
        }
        Objects.requireNonNull(action, "action cannot be null");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        requireNonNegative(currentInFlightRequestCount, "currentInFlightRequestCount");
        requireFiniteNonNegative(observedAverageLatencyMillis, "observedAverageLatencyMillis");
        requireFiniteNonNegative(observedP95LatencyMillis, "observedP95LatencyMillis");
        requireFiniteNonNegative(observedP99LatencyMillis, "observedP99LatencyMillis");
        requireRate(observedErrorRate, "observedErrorRate");
        requireNonNegative(sampleSize, "sampleSize");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static void requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1");
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
