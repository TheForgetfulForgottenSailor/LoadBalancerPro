package com.richmond423.loadbalancerpro.core;

import java.util.Objects;

public record LaseEvaluationConfig(
        AdaptiveConcurrencyConfig adaptiveConcurrencyConfig,
        LoadSheddingConfig loadSheddingConfig,
        ShadowAutoscalerConfig shadowAutoscalerConfig,
        FailureScenarioConfig failureScenarioConfig) {

    public LaseEvaluationConfig {
        Objects.requireNonNull(adaptiveConcurrencyConfig, "adaptiveConcurrencyConfig cannot be null");
        Objects.requireNonNull(loadSheddingConfig, "loadSheddingConfig cannot be null");
        Objects.requireNonNull(shadowAutoscalerConfig, "shadowAutoscalerConfig cannot be null");
        Objects.requireNonNull(failureScenarioConfig, "failureScenarioConfig cannot be null");
    }
}
