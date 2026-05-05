package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LaseEvaluationInput(
        String evaluationId,
        RequestPriority requestPriority,
        List<ServerStateVector> serverCandidates,
        int currentConcurrencyLimit,
        ConcurrencyFeedback concurrencyFeedback,
        LoadSheddingSignal loadSheddingSignal,
        AutoscalingSignal autoscalingSignal,
        FailureScenarioSignal failureScenarioSignal,
        Instant timestamp) {

    public LaseEvaluationInput {
        evaluationId = requireNonBlank(evaluationId, "evaluationId");
        Objects.requireNonNull(requestPriority, "requestPriority cannot be null");
        Objects.requireNonNull(serverCandidates, "serverCandidates cannot be null");
        if (serverCandidates.isEmpty()) {
            throw new IllegalArgumentException("serverCandidates cannot be empty");
        }
        for (ServerStateVector candidate : serverCandidates) {
            Objects.requireNonNull(candidate, "serverCandidates cannot contain null candidates");
        }
        serverCandidates = List.copyOf(serverCandidates);
        if (currentConcurrencyLimit < 1) {
            throw new IllegalArgumentException("currentConcurrencyLimit must be positive");
        }
        Objects.requireNonNull(concurrencyFeedback, "concurrencyFeedback cannot be null");
        Objects.requireNonNull(loadSheddingSignal, "loadSheddingSignal cannot be null");
        Objects.requireNonNull(autoscalingSignal, "autoscalingSignal cannot be null");
        Objects.requireNonNull(failureScenarioSignal, "failureScenarioSignal cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }
}
