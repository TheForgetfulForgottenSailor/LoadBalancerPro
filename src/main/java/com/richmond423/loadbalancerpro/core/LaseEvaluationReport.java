package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record LaseEvaluationReport(
        String evaluationId,
        RoutingDecision routingDecision,
        ConcurrencyLimitDecision concurrencyDecision,
        LoadSheddingDecision loadSheddingDecision,
        AutoscalingRecommendation autoscalingRecommendation,
        FailureScenarioResult failureScenarioResult,
        String summary,
        Instant timestamp) {

    public LaseEvaluationReport {
        evaluationId = requireNonBlank(evaluationId, "evaluationId");
        Objects.requireNonNull(routingDecision, "routingDecision cannot be null");
        Objects.requireNonNull(concurrencyDecision, "concurrencyDecision cannot be null");
        Objects.requireNonNull(loadSheddingDecision, "loadSheddingDecision cannot be null");
        Objects.requireNonNull(autoscalingRecommendation, "autoscalingRecommendation cannot be null");
        Objects.requireNonNull(failureScenarioResult, "failureScenarioResult cannot be null");
        summary = requireNonBlank(summary, "summary");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }
}
