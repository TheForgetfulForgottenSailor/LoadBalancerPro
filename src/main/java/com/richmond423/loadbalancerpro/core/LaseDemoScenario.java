package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Objects;

public record LaseDemoScenario(
        LaseDemoScenarioType type,
        String scenarioId,
        String description,
        LaseEvaluationInput input,
        LaseEvaluationConfig config,
        Instant timestamp) {

    public LaseDemoScenario {
        Objects.requireNonNull(type, "type cannot be null");
        scenarioId = requireNonBlank(scenarioId, "scenarioId");
        description = requireNonBlank(description, "description");
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }
}
