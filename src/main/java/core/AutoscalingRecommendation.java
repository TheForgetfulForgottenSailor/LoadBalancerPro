package core;

import java.time.Instant;
import java.util.Objects;

public record AutoscalingRecommendation(
        String targetId,
        AutoscalingAction action,
        int currentCapacity,
        int recommendedCapacity,
        int minCapacity,
        int maxCapacity,
        double severityScore,
        String reason,
        Instant timestamp,
        int currentInFlightRequestCount,
        int queueDepth,
        double utilization,
        double observedP95LatencyMillis,
        double observedP99LatencyMillis,
        double observedErrorRate,
        int sampleSize) {

    public AutoscalingRecommendation {
        targetId = requireNonBlank(targetId, "targetId");
        Objects.requireNonNull(action, "action cannot be null");
        requirePositive(currentCapacity, "currentCapacity");
        requirePositive(recommendedCapacity, "recommendedCapacity");
        requirePositive(minCapacity, "minCapacity");
        if (maxCapacity < minCapacity) {
            throw new IllegalArgumentException("maxCapacity must be greater than or equal to minCapacity");
        }
        if (currentCapacity < minCapacity || currentCapacity > maxCapacity) {
            throw new IllegalArgumentException("currentCapacity must be within minCapacity and maxCapacity");
        }
        if (recommendedCapacity < minCapacity || recommendedCapacity > maxCapacity) {
            throw new IllegalArgumentException("recommendedCapacity must be within minCapacity and maxCapacity");
        }
        requireRate(severityScore, "severityScore");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        requireNonNegative(currentInFlightRequestCount, "currentInFlightRequestCount");
        requireNonNegative(queueDepth, "queueDepth");
        requireFiniteNonNegative(utilization, "utilization");
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
