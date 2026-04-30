package core;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

public final class LaseDemoScenarioFactory {
    private static final Instant DEFAULT_DEMO_INSTANT = Instant.parse("2026-04-29T12:00:00Z");

    private final Clock clock;

    public LaseDemoScenarioFactory() {
        this(Clock.fixed(DEFAULT_DEMO_INSTANT, ZoneOffset.UTC));
    }

    public LaseDemoScenarioFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public List<LaseDemoScenario> createAll() {
        return List.of(
                create(LaseDemoScenarioType.HEALTHY),
                create(LaseDemoScenarioType.OVERLOADED),
                create(LaseDemoScenarioType.ERROR_STORM),
                create(LaseDemoScenarioType.PARTIAL_OUTAGE),
                create(LaseDemoScenarioType.LOW_SAMPLE)
        );
    }

    public LaseDemoScenario create(LaseDemoScenarioType type) {
        Objects.requireNonNull(type, "type cannot be null");
        Instant now = Instant.now(clock);
        return switch (type) {
            case HEALTHY -> scenario(type, "lase-demo-healthy",
                    "Healthy baseline with normal latency, low error rate, and low queue pressure.",
                    input("lase-demo-healthy", RequestPriority.USER, healthyCandidates(now), 10,
                            healthyFeedback(now), normalShedding(now), normalAutoscaling(now),
                            normalFailure("healthy-failure", FailureScenarioType.TRAFFIC_SPIKE, now), now),
                    now);
            case OVERLOADED -> scenario(type, "lase-demo-overloaded",
                    "High utilization, queue depth, and latency with low-priority traffic under pressure.",
                    input("lase-demo-overloaded", RequestPriority.PREFETCH, mixedPressureCandidates(now), 20,
                            overloadedFeedback(now), overloadedShedding(now), overloadedAutoscaling(now),
                            overloadedFailure(now), now),
                    now);
            case ERROR_STORM -> scenario(type, "lase-demo-error-storm",
                    "High error rate without matching queue or utilization pressure; autoscaling should investigate.",
                    input("lase-demo-error-storm", RequestPriority.USER, healthyCandidates(now), 10,
                            errorFeedback(now), errorShedding(now), errorOnlyAutoscaling(now),
                            errorStormFailure(now), now),
                    now);
            case PARTIAL_OUTAGE -> scenario(type, "lase-demo-partial-outage",
                    "Low healthy-server ratio with route-around and investigation recommendations.",
                    input("lase-demo-partial-outage", RequestPriority.USER, partialOutageCandidates(now), 10,
                            healthyFeedback(now), normalShedding(now), normalAutoscaling(now),
                            partialOutageFailure(now), now),
                    now);
            case LOW_SAMPLE -> scenario(type, "lase-demo-low-sample",
                    "Insufficient sample sizes drive conservative hold decisions for noisy telemetry.",
                    input("lase-demo-low-sample", RequestPriority.USER, healthyCandidates(now), 10,
                            lowSampleFeedback(now), normalShedding(now), lowSampleAutoscaling(now),
                            lowSampleFailure(now), now),
                    now);
        };
    }

    private LaseDemoScenario scenario(LaseDemoScenarioType type,
                                      String scenarioId,
                                      String description,
                                      LaseEvaluationInput input,
                                      Instant timestamp) {
        return new LaseDemoScenario(type, scenarioId, description, input, config(), timestamp);
    }

    private LaseEvaluationInput input(String evaluationId,
                                      RequestPriority priority,
                                      List<ServerStateVector> candidates,
                                      int currentConcurrencyLimit,
                                      ConcurrencyFeedback feedback,
                                      LoadSheddingSignal sheddingSignal,
                                      AutoscalingSignal autoscalingSignal,
                                      FailureScenarioSignal failureSignal,
                                      Instant timestamp) {
        return new LaseEvaluationInput(evaluationId, priority, candidates, currentConcurrencyLimit,
                feedback, sheddingSignal, autoscalingSignal, failureSignal, timestamp);
    }

    private LaseEvaluationConfig config() {
        return new LaseEvaluationConfig(
                new AdaptiveConcurrencyConfig(1, 100, 2, 0.5, 200.0, 0.10, 10),
                new LoadSheddingConfig(0.70, 0.90, 20, 250.0, 0.10, true, true),
                new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.85, 0.25, 2, 1, 10),
                new FailureScenarioConfig(20, 200.0, 350.0, 0.10, 0.85, 0.60, 10)
        );
    }

    private List<ServerStateVector> healthyCandidates(Instant now) {
        return List.of(
                server("server-a", true, 2, 80.0, 120.0, 150.0, 0.01, 1, now),
                server("server-b", true, 5, 140.0, 190.0, 260.0, 0.03, 5, now)
        );
    }

    private List<ServerStateVector> mixedPressureCandidates(Instant now) {
        return List.of(
                server("server-fast-but-unhealthy", false, 1, 50.0, 80.0, 100.0, 0.01, 0, now),
                server("server-slow", true, 18, 260.0, 340.0, 520.0, 0.08, 25, now),
                server("server-busy", true, 16, 230.0, 300.0, 450.0, 0.06, 18, now)
        );
    }

    private List<ServerStateVector> partialOutageCandidates(Instant now) {
        return List.of(
                server("server-a", true, 3, 100.0, 150.0, 210.0, 0.02, 2, now),
                server("server-b", false, 0, 0.0, 0.0, 0.0, 0.0, 0, now),
                server("server-c", false, 0, 0.0, 0.0, 0.0, 0.0, 0, now)
        );
    }

    private ServerStateVector server(String serverId,
                                     boolean healthy,
                                     int inFlight,
                                     double averageLatencyMillis,
                                     double p95LatencyMillis,
                                     double p99LatencyMillis,
                                     double errorRate,
                                     int queueDepth,
                                     Instant timestamp) {
        return new ServerStateVector(serverId, healthy, inFlight, 100.0, 50.0, averageLatencyMillis,
                p95LatencyMillis, p99LatencyMillis, errorRate, queueDepth, timestamp);
    }

    private ConcurrencyFeedback healthyFeedback(Instant now) {
        return new ConcurrencyFeedback("server-a", 2, 80.0, 120.0, 150.0, 0.01, 100, now);
    }

    private ConcurrencyFeedback overloadedFeedback(Instant now) {
        return new ConcurrencyFeedback("server-busy", 30, 260.0, 320.0, 480.0, 0.20, 100, now);
    }

    private ConcurrencyFeedback errorFeedback(Instant now) {
        return new ConcurrencyFeedback("server-a", 6, 90.0, 140.0, 180.0, 0.20, 100, now);
    }

    private ConcurrencyFeedback lowSampleFeedback(Instant now) {
        return new ConcurrencyFeedback("server-a", 20, 500.0, 500.0, 800.0, 0.90, 5, now);
    }

    private LoadSheddingSignal normalShedding(Instant now) {
        return new LoadSheddingSignal("checkout", 8, 40, 2, 100.0, 0.01, now);
    }

    private LoadSheddingSignal overloadedShedding(Instant now) {
        return new LoadSheddingSignal("checkout", 38, 40, 30, 300.0, 0.20, now);
    }

    private LoadSheddingSignal errorShedding(Instant now) {
        return new LoadSheddingSignal("checkout", 8, 40, 2, 100.0, 0.20, now);
    }

    private AutoscalingSignal normalAutoscaling(Instant now) {
        return new AutoscalingSignal("checkout", 4, 2, 10, 2, 0, 100.0, 150.0, 0.01, 100, now);
    }

    private AutoscalingSignal overloadedAutoscaling(Instant now) {
        return new AutoscalingSignal("checkout", 4, 2, 10, 38, 30, 300.0, 420.0, 0.05, 100, now);
    }

    private AutoscalingSignal errorOnlyAutoscaling(Instant now) {
        return new AutoscalingSignal("checkout", 4, 2, 10, 2, 0, 100.0, 150.0, 0.20, 100, now);
    }

    private AutoscalingSignal lowSampleAutoscaling(Instant now) {
        return new AutoscalingSignal("checkout", 4, 2, 10, 40, 40, 500.0, 800.0, 0.90, 5, now);
    }

    private FailureScenarioSignal normalFailure(String scenarioId,
                                                FailureScenarioType type,
                                                Instant now) {
        return new FailureScenarioSignal(scenarioId, type, "checkout", 4, 4, 8, 40,
                2, 100.0, 150.0, 0.01, 100, now);
    }

    private FailureScenarioSignal overloadedFailure(Instant now) {
        return new FailureScenarioSignal("overloaded-queue-pressure", FailureScenarioType.QUEUE_BACKLOG,
                "checkout", 4, 4, 38, 40, 30, 300.0, 420.0, 0.05, 100, now);
    }

    private FailureScenarioSignal errorStormFailure(Instant now) {
        return new FailureScenarioSignal("error-storm-pressure", FailureScenarioType.ERROR_STORM,
                "checkout", 4, 4, 8, 40, 2, 100.0, 150.0, 0.20, 100, now);
    }

    private FailureScenarioSignal partialOutageFailure(Instant now) {
        return new FailureScenarioSignal("partial-outage-pressure", FailureScenarioType.PARTIAL_OUTAGE,
                "checkout", 5, 2, 8, 40, 2, 100.0, 150.0, 0.01, 100, now);
    }

    private FailureScenarioSignal lowSampleFailure(Instant now) {
        return new FailureScenarioSignal("low-sample-pressure", FailureScenarioType.PARTIAL_OUTAGE,
                "checkout", 4, 0, 40, 40, 40, 500.0, 800.0, 0.90, 5, now);
    }
}
