package com.richmond423.loadbalancerpro.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record LaseShadowSummary(
        int maxSize,
        long totalEvaluations,
        long comparableEvaluations,
        long agreementCount,
        double agreementRate,
        long failSafeCount,
        Instant latestEventTimestamp,
        Map<String, Long> recommendationCounts,
        LaseShadowNetworkSummary networkSummary) {

    public LaseShadowSummary {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        validateNonNegative(totalEvaluations, "totalEvaluations");
        validateNonNegative(comparableEvaluations, "comparableEvaluations");
        validateNonNegative(agreementCount, "agreementCount");
        validateNonNegative(failSafeCount, "failSafeCount");
        if (comparableEvaluations > totalEvaluations) {
            throw new IllegalArgumentException("comparableEvaluations cannot exceed totalEvaluations");
        }
        if (agreementCount > comparableEvaluations) {
            throw new IllegalArgumentException("agreementCount cannot exceed comparableEvaluations");
        }
        if (!Double.isFinite(agreementRate) || agreementRate < 0.0 || agreementRate > 1.0) {
            throw new IllegalArgumentException("agreementRate must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(recommendationCounts, "recommendationCounts cannot be null");
        Objects.requireNonNull(networkSummary, "networkSummary cannot be null");
        recommendationCounts = Map.copyOf(recommendationCounts);
    }

    public LaseShadowSummary(int maxSize,
                             long totalEvaluations,
                             long comparableEvaluations,
                             long agreementCount,
                             double agreementRate,
                             long failSafeCount,
                             Instant latestEventTimestamp,
                             Map<String, Long> recommendationCounts) {
        this(maxSize, totalEvaluations, comparableEvaluations, agreementCount, agreementRate, failSafeCount,
                latestEventTimestamp, recommendationCounts, LaseShadowNetworkSummary.empty());
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }
}
