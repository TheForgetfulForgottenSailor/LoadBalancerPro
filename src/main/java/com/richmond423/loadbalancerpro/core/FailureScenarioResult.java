package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record FailureScenarioResult(
        String scenarioId,
        FailureScenarioType scenarioType,
        String targetId,
        FailureSeverity severity,
        List<MitigationAction> recommendations,
        String reason,
        Instant timestamp,
        double healthyRatio,
        double utilization,
        int queueDepth,
        double observedP95LatencyMillis,
        double observedP99LatencyMillis,
        double observedErrorRate,
        int sampleSize) {

    public FailureScenarioResult {
        scenarioId = requireNonBlank(scenarioId, "scenarioId");
        Objects.requireNonNull(scenarioType, "scenarioType cannot be null");
        targetId = requireNonBlank(targetId, "targetId");
        Objects.requireNonNull(severity, "severity cannot be null");
        recommendations = requireRecommendations(recommendations);
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        requireRate(healthyRatio, "healthyRatio");
        requireFiniteNonNegative(utilization, "utilization");
        requireNonNegative(queueDepth, "queueDepth");
        requireFiniteNonNegative(observedP95LatencyMillis, "observedP95LatencyMillis");
        requireFiniteNonNegative(observedP99LatencyMillis, "observedP99LatencyMillis");
        requireRate(observedErrorRate, "observedErrorRate");
        requireNonNegative(sampleSize, "sampleSize");
    }

    private static List<MitigationAction> requireRecommendations(List<MitigationAction> actions) {
        Objects.requireNonNull(actions, "recommendations cannot be null");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("recommendations cannot be empty");
        }
        for (MitigationAction action : actions) {
            Objects.requireNonNull(action, "recommendations cannot contain null actions");
        }
        return List.copyOf(actions);
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
