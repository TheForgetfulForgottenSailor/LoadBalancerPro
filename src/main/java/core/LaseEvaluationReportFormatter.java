package core;

import java.util.Objects;
import java.util.stream.Collectors;

public final class LaseEvaluationReportFormatter {
    public String format(LaseEvaluationReport report) {
        Objects.requireNonNull(report, "report cannot be null");

        return String.join(System.lineSeparator(),
                "Evaluation ID: " + report.evaluationId(),
                "Routing Decision:",
                "  Strategy: " + report.routingDecision().explanation().strategyUsed(),
                "  Chosen server: " + chosenServer(report),
                "  Candidates: " + String.join(", ",
                        report.routingDecision().explanation().candidateServersConsidered()),
                "  Reason: " + report.routingDecision().explanation().reason(),
                "Adaptive Concurrency:",
                "  Action: " + report.concurrencyDecision().action(),
                "  Limit: " + report.concurrencyDecision().previousLimit()
                        + " -> " + report.concurrencyDecision().nextLimit(),
                "  Reason: " + report.concurrencyDecision().reason(),
                "Load Shedding:",
                "  Priority: " + report.loadSheddingDecision().priority(),
                "  Action: " + report.loadSheddingDecision().action(),
                "  Target: " + report.loadSheddingDecision().targetId(),
                "  Reason: " + report.loadSheddingDecision().reason(),
                "Shadow Autoscaling:",
                "  Action: " + report.autoscalingRecommendation().action(),
                "  Capacity: " + report.autoscalingRecommendation().currentCapacity()
                        + " -> " + report.autoscalingRecommendation().recommendedCapacity(),
                "  Severity score: " + report.autoscalingRecommendation().severityScore(),
                "  Reason: " + report.autoscalingRecommendation().reason(),
                "Failure Scenario:",
                "  Severity: " + report.failureScenarioResult().severity(),
                "  Mitigation actions: " + mitigationActions(report),
                "  Reason: " + report.failureScenarioResult().reason(),
                "Summary:",
                "  " + report.summary()
        );
    }

    private String chosenServer(LaseEvaluationReport report) {
        return report.routingDecision().chosenServer()
                .map(ServerStateVector::serverId)
                .orElse("no eligible server");
    }

    private String mitigationActions(LaseEvaluationReport report) {
        return report.failureScenarioResult().recommendations().stream()
                .map(MitigationAction::name)
                .collect(Collectors.joining(", "));
    }
}
