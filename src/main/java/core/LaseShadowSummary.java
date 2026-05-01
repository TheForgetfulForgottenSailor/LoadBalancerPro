package core;

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
        Map<String, Long> recommendationCounts) {

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
        recommendationCounts = Map.copyOf(recommendationCounts);
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }
}
