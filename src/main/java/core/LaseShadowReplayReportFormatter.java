package core;

import java.util.Map;
import java.util.Objects;

public final class LaseShadowReplayReportFormatter {
    public String format(LaseShadowReplayReport report) {
        Objects.requireNonNull(report, "report cannot be null");
        LaseShadowReplayMetrics metrics = report.metrics();
        StringBuilder builder = new StringBuilder();

        builder.append("=== LoadBalancerPro LASE Replay Report ===").append(System.lineSeparator());
        builder.append("Mode: offline/read-only replay of JSON Lines shadow events.").append(System.lineSeparator());
        builder.append("Safety: No routing mutation. No CloudManager calls. No live AWS resources touched.")
                .append(System.lineSeparator());
        builder.append("Source: ").append(report.sourceName()).append(System.lineSeparator());
        builder.append(System.lineSeparator());

        appendEventTotals(builder, metrics);
        appendAgreementSummary(builder, metrics);
        appendRecommendationCounts(builder, metrics.recommendationCounts());
        appendScoreSummary(builder, metrics);
        appendNetworkSummary(builder, metrics.networkSummary());
        appendTimeRange(builder, metrics);
        appendWarnings(builder, report);

        return builder.toString();
    }

    private static void appendEventTotals(StringBuilder builder, LaseShadowReplayMetrics metrics) {
        builder.append("Event Totals:").append(System.lineSeparator());
        builder.append("  Total events: ").append(metrics.totalEvents()).append(System.lineSeparator());
        builder.append("  Comparable events: ").append(metrics.comparableEvents()).append(System.lineSeparator());
        builder.append("  Fail-safe events: ").append(metrics.failSafeCount()).append(System.lineSeparator());
        builder.append(System.lineSeparator());
    }

    private static void appendAgreementSummary(StringBuilder builder, LaseShadowReplayMetrics metrics) {
        builder.append("Agreement / Fail-Safe:").append(System.lineSeparator());
        builder.append("  Agreement count: ").append(metrics.agreementCount()).append(System.lineSeparator());
        builder.append("  Agreement rate: ").append(percent(metrics.agreementRate())).append(System.lineSeparator());
        builder.append("  Fail-safe rate: ").append(percent(metrics.failSafeRate())).append(System.lineSeparator());
        if (!metrics.failureReasonCounts().isEmpty()) {
            builder.append("  Failure reasons:").append(System.lineSeparator());
            metrics.failureReasonCounts().forEach((reason, count) ->
                    builder.append("    ").append(reason).append(": ").append(count).append(System.lineSeparator()));
        }
        builder.append(System.lineSeparator());
    }

    private static void appendRecommendationCounts(StringBuilder builder, Map<String, Long> counts) {
        builder.append("Recommendation Counts:").append(System.lineSeparator());
        if (counts.isEmpty()) {
            builder.append("  none").append(System.lineSeparator());
        } else {
            counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append("  ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(entry.getValue())
                            .append(System.lineSeparator()));
        }
        builder.append(System.lineSeparator());
    }

    private static void appendScoreSummary(StringBuilder builder, LaseShadowReplayMetrics metrics) {
        builder.append("Score Summary:").append(System.lineSeparator());
        appendScore(builder, "Decision score", metrics.decisionScoreSummary());
        appendScore(builder, "Network risk score", metrics.networkRiskSummary());
        builder.append(System.lineSeparator());
    }

    private static void appendScore(StringBuilder builder, String label, LaseShadowReplayScoreSummary summary) {
        builder.append("  ").append(label).append(": count=")
                .append(summary.count())
                .append(", min=")
                .append(number(summary.min()))
                .append(", avg=")
                .append(number(summary.average()))
                .append(", max=")
                .append(number(summary.max()))
                .append(System.lineSeparator());
    }

    private static void appendNetworkSummary(StringBuilder builder, LaseShadowNetworkSummary summary) {
        builder.append("Network Signal Summary:").append(System.lineSeparator());
        builder.append("  Average timeout rate: ").append(percent(summary.averageTimeoutRate()))
                .append(System.lineSeparator());
        builder.append("  Average retry rate: ").append(percent(summary.averageRetryRate()))
                .append(System.lineSeparator());
        builder.append("  Average connection failure rate: ").append(percent(summary.averageConnectionFailureRate()))
                .append(System.lineSeparator());
        builder.append("  Max latency jitter millis: ").append(number(summary.maxLatencyJitterMillis()))
                .append(System.lineSeparator());
        builder.append("  Recent error bursts: ").append(summary.recentErrorBurstCount())
                .append(System.lineSeparator());
        builder.append("  Total request timeouts: ").append(summary.totalRequestTimeoutCount())
                .append(System.lineSeparator());
        builder.append(System.lineSeparator());
    }

    private static void appendTimeRange(StringBuilder builder, LaseShadowReplayMetrics metrics) {
        builder.append("Time Range:").append(System.lineSeparator());
        builder.append("  First event: ").append(metrics.firstEventTimestamp() == null ? "none" : metrics.firstEventTimestamp())
                .append(System.lineSeparator());
        builder.append("  Latest event: ").append(metrics.latestEventTimestamp() == null ? "none" : metrics.latestEventTimestamp())
                .append(System.lineSeparator());
        builder.append(System.lineSeparator());
    }

    private static void appendWarnings(StringBuilder builder, LaseShadowReplayReport report) {
        builder.append("Warnings / Limitations:").append(System.lineSeparator());
        if (report.warnings().isEmpty()) {
            builder.append("  Replay evaluates saved shadow events only; it is not production telemetry durability.")
                    .append(System.lineSeparator());
        } else {
            report.warnings().forEach(warning -> builder.append("  ").append(warning).append(System.lineSeparator()));
        }
    }

    private static String percent(double value) {
        return String.format("%.2f%%", value * 100.0);
    }

    private static String number(double value) {
        return String.format("%.2f", value);
    }
}
