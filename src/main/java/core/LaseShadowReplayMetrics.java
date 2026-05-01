package core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record LaseShadowReplayMetrics(
        long totalEvents,
        long comparableEvents,
        long agreementCount,
        double agreementRate,
        long failSafeCount,
        double failSafeRate,
        Map<String, Long> recommendationCounts,
        LaseShadowReplayScoreSummary decisionScoreSummary,
        LaseShadowReplayScoreSummary networkRiskSummary,
        LaseShadowNetworkSummary networkSummary,
        Instant firstEventTimestamp,
        Instant latestEventTimestamp,
        Map<String, Long> failureReasonCounts) {

    public LaseShadowReplayMetrics {
        validateNonNegative(totalEvents, "totalEvents");
        validateNonNegative(comparableEvents, "comparableEvents");
        validateNonNegative(agreementCount, "agreementCount");
        validateNonNegative(failSafeCount, "failSafeCount");
        if (comparableEvents > totalEvents) {
            throw new IllegalArgumentException("comparableEvents cannot exceed totalEvents");
        }
        if (agreementCount > comparableEvents) {
            throw new IllegalArgumentException("agreementCount cannot exceed comparableEvents");
        }
        validateRate(agreementRate, "agreementRate");
        validateRate(failSafeRate, "failSafeRate");
        Objects.requireNonNull(recommendationCounts, "recommendationCounts cannot be null");
        Objects.requireNonNull(decisionScoreSummary, "decisionScoreSummary cannot be null");
        Objects.requireNonNull(networkRiskSummary, "networkRiskSummary cannot be null");
        Objects.requireNonNull(networkSummary, "networkSummary cannot be null");
        Objects.requireNonNull(failureReasonCounts, "failureReasonCounts cannot be null");
        recommendationCounts = Map.copyOf(recommendationCounts);
        failureReasonCounts = Map.copyOf(failureReasonCounts);
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    private static void validateRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
