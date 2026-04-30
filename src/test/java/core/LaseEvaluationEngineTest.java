package core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LaseEvaluationEngineTest {
    private static final Instant NOW = Instant.parse("2026-04-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final AdaptiveConcurrencyConfig CONCURRENCY_CONFIG =
            new AdaptiveConcurrencyConfig(1, 100, 2, 0.5, 200.0, 0.10, 10);
    private static final LoadSheddingConfig SHEDDING_CONFIG =
            new LoadSheddingConfig(0.70, 0.90, 20, 250.0, 0.10, true, true);
    private static final ShadowAutoscalerConfig AUTOSCALER_CONFIG =
            new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.85, 0.25, 2, 1, 10);
    private static final FailureScenarioConfig FAILURE_CONFIG =
            new FailureScenarioConfig(20, 200.0, 350.0, 0.10, 0.85, 0.60, 10);
    private static final LaseEvaluationConfig CONFIG =
            new LaseEvaluationConfig(CONCURRENCY_CONFIG, SHEDDING_CONFIG, AUTOSCALER_CONFIG, FAILURE_CONFIG);

    @Test
    void normalHealthyEvaluationProducesCompleteReport() {
        LaseEvaluationReport report = engine(7).evaluate(normalInput(), CONFIG);

        assertTrue(report.routingDecision().chosenServer().isPresent());
        assertEquals("server-a", report.routingDecision().chosenServer().orElseThrow().serverId());
        assertEquals(ConcurrencyLimitDecision.Action.INCREASE, report.concurrencyDecision().action());
        assertEquals(LoadSheddingDecision.Action.ALLOW, report.loadSheddingDecision().action());
        assertEquals(AutoscalingAction.HOLD, report.autoscalingRecommendation().action());
        assertEquals(FailureSeverity.LOW, report.failureScenarioResult().severity());
        assertEquals(List.of(MitigationAction.HOLD), report.failureScenarioResult().recommendations());
        assertFalse(report.summary().isBlank());
    }

    @Test
    void overloadedEvaluationComposesProtectiveOutcomes() {
        LaseEvaluationReport report = engine(7).evaluate(overloadedInput(), CONFIG);

        assertTrue(report.concurrencyDecision().nextLimit() <= report.concurrencyDecision().previousLimit());
        assertEquals(LoadSheddingDecision.Action.SHED, report.loadSheddingDecision().action());
        assertEquals(AutoscalingAction.SCALE_UP, report.autoscalingRecommendation().action());
        assertTrue(report.autoscalingRecommendation().recommendedCapacity()
                > report.autoscalingRecommendation().currentCapacity());
        assertEquals(FailureSeverity.HIGH, report.failureScenarioResult().severity());
        assertEquals(List.of(MitigationAction.SHED_LOW_PRIORITY, MitigationAction.SCALE_UP_SHADOW),
                report.failureScenarioResult().recommendations());
        assertTrue(report.summary().contains("DECREASE"));
        assertTrue(report.summary().contains("SHED"));
        assertTrue(report.summary().contains("SCALE_UP"));
    }

    @Test
    void noHealthyRoutingCandidatesStillProducesSafeReport() {
        LaseEvaluationInput input = input("no-healthy", RequestPriority.USER,
                List.of(unhealthyServer("server-a"), unhealthyServer("server-b")),
                10, healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE));

        LaseEvaluationReport report = engine(7).evaluate(input, CONFIG);

        assertTrue(report.routingDecision().chosenServer().isEmpty());
        assertTrue(report.routingDecision().explanation().reason().contains("No healthy eligible servers"));
        assertEquals(ConcurrencyLimitDecision.Action.INCREASE, report.concurrencyDecision().action());
        assertEquals(LoadSheddingDecision.Action.ALLOW, report.loadSheddingDecision().action());
        assertEquals(AutoscalingAction.HOLD, report.autoscalingRecommendation().action());
        assertEquals(FailureSeverity.LOW, report.failureScenarioResult().severity());
        assertTrue(report.summary().contains("no eligible server"));
    }

    @Test
    void highErrorOnlyAutoscalingSignalInvestigatesInsteadOfBlindScaleUp() {
        AutoscalingSignal errorOnlyAutoscalingSignal = new AutoscalingSignal("checkout", 4, 2, 10,
                2, 0, 100.0, 150.0, 0.20, 100, NOW);
        LaseEvaluationInput input = input("error-only-autoscale", RequestPriority.USER, healthyCandidates(),
                10, healthyFeedback(), normalSheddingSignal(), errorOnlyAutoscalingSignal,
                normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE));

        LaseEvaluationReport report = engine(7).evaluate(input, CONFIG);

        assertEquals(AutoscalingAction.INVESTIGATE, report.autoscalingRecommendation().action());
        assertEquals(report.autoscalingRecommendation().currentCapacity(),
                report.autoscalingRecommendation().recommendedCapacity());
        assertTrue(report.summary().contains("INVESTIGATE"));
    }

    @Test
    void lowSampleSignalsProduceHoldAndLowSeverityBehavior() {
        LaseEvaluationInput input = input("low-sample", RequestPriority.USER, healthyCandidates(),
                10,
                new ConcurrencyFeedback("server-a", 20, 500.0, 500.0, 800.0, 0.90, 5, NOW),
                normalSheddingSignal(),
                new AutoscalingSignal("checkout", 4, 2, 10, 40, 40, 500.0, 800.0, 0.90, 5, NOW),
                new FailureScenarioSignal("low-sample-failure", FailureScenarioType.PARTIAL_OUTAGE, "checkout",
                        4, 0, 40, 40, 40, 500.0, 800.0, 0.90, 5, NOW));

        LaseEvaluationReport report = engine(7).evaluate(input, CONFIG);

        assertEquals(ConcurrencyLimitDecision.Action.HOLD, report.concurrencyDecision().action());
        assertEquals(AutoscalingAction.HOLD, report.autoscalingRecommendation().action());
        assertEquals(FailureSeverity.LOW, report.failureScenarioResult().severity());
        assertEquals(List.of(MitigationAction.HOLD), report.failureScenarioResult().recommendations());
    }

    @Test
    void reportContainsAllComponentDecisionsAndTimestamp() {
        LaseEvaluationReport report = engine(7).evaluate(overloadedInput(), CONFIG);

        assertEquals("overloaded", report.evaluationId());
        assertNotNull(report.routingDecision());
        assertNotNull(report.concurrencyDecision());
        assertNotNull(report.loadSheddingDecision());
        assertNotNull(report.autoscalingRecommendation());
        assertNotNull(report.failureScenarioResult());
        assertEquals(NOW, report.timestamp());
        assertTrue(report.summary().contains("chosen server"));
        assertTrue(report.summary().contains("concurrency"));
        assertTrue(report.summary().contains("load shedding"));
        assertTrue(report.summary().contains("autoscaling"));
        assertTrue(report.summary().contains("failure severity"));
    }

    @Test
    void inputBoundaryValidationRejectsNullCandidatesAndNegativeConcurrencyLimit() {
        assertAll("input boundaries",
                () -> assertInvalid(() -> input("null-candidates", RequestPriority.USER, null, 10,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("negative-limit", RequestPriority.USER, healthyCandidates(), -1,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE)))
        );
    }

    @Test
    void reportUsesDirectResultsFromComposedLaseComponents() {
        LaseEvaluationInput input = overloadedInput();
        TailLatencyPowerOfTwoStrategy routingStrategy =
                new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(7), CLOCK);
        LoadSheddingPolicy sheddingPolicy = new LoadSheddingPolicy();
        ShadowAutoscaler autoscaler = new ShadowAutoscaler();
        FailureScenarioRunner failureRunner = new FailureScenarioRunner();

        LaseEvaluationReport report = new LaseEvaluationEngine(routingStrategy, sheddingPolicy, autoscaler,
                failureRunner, CLOCK).evaluate(input, CONFIG);

        assertEquals(new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(7), CLOCK)
                .choose(input.serverCandidates()), report.routingDecision());
        assertEquals(new AdaptiveConcurrencyLimiter(CONCURRENCY_CONFIG, CLOCK)
                .calculateNextLimit(input.currentConcurrencyLimit(), input.concurrencyFeedback()),
                report.concurrencyDecision());
        assertEquals(sheddingPolicy.decide(input.requestPriority(), input.loadSheddingSignal(), SHEDDING_CONFIG),
                report.loadSheddingDecision());
        assertEquals(autoscaler.recommend(input.autoscalingSignal(), AUTOSCALER_CONFIG),
                report.autoscalingRecommendation());
        assertEquals(failureRunner.evaluate(input.failureScenarioSignal(), FAILURE_CONFIG),
                report.failureScenarioResult());
    }

    @Test
    void summaryIncludesOutcomesAndExplanationReasons() {
        LaseEvaluationReport report = engine(7).evaluate(overloadedInput(), CONFIG);

        assertTrue(report.summary().contains("overloaded"));
        assertTrue(report.summary().contains("chosen server"));
        assertTrue(report.summary().contains("DECREASE"));
        assertTrue(report.summary().contains("SHED"));
        assertTrue(report.summary().contains("SCALE_UP"));
        assertTrue(report.summary().contains("HIGH"));
        assertTrue(report.summary().contains("SHED_LOW_PRIORITY"));
        assertTrue(report.summary().contains("SCALE_UP_SHADOW"));
        assertTrue(report.summary().contains("p95 latency"));
        assertTrue(report.summary().contains("overload pressure"));
        assertTrue(report.summary().contains("scale-up pressure"));
        assertTrue(report.summary().contains("Queue backlog pressure"));
    }

    @Test
    void overloadedEvaluationAvoidsUnhealthyHighRiskRoutingCandidate() {
        LaseEvaluationInput input = input("overloaded-routing", RequestPriority.PREFETCH,
                List.of(unhealthyServer("server-fast-but-unhealthy"), slowerServer("server-slow"),
                        healthyServer("server-healthy")),
                20,
                new ConcurrencyFeedback("server-healthy", 30, 260.0, 320.0, 480.0, 0.20, 100, NOW),
                new LoadSheddingSignal("checkout", 38, 40, 30, 300.0, 0.20, NOW),
                new AutoscalingSignal("checkout", 4, 2, 10, 38, 30, 300.0, 420.0, 0.05, 100, NOW),
                new FailureScenarioSignal("queue-pressure", FailureScenarioType.QUEUE_BACKLOG, "checkout",
                        4, 4, 38, 40, 30, 300.0, 420.0, 0.05, 100, NOW));

        LaseEvaluationReport report = engine(7).evaluate(input, CONFIG);

        assertTrue(report.routingDecision().chosenServer().isPresent());
        assertNotEquals("server-fast-but-unhealthy", report.routingDecision().chosenServer().orElseThrow().serverId());
        assertFalse(report.routingDecision().explanation().candidateServersConsidered()
                .contains("server-fast-but-unhealthy"));
        assertEquals(LoadSheddingDecision.Action.SHED, report.loadSheddingDecision().action());
        assertEquals(AutoscalingAction.SCALE_UP, report.autoscalingRecommendation().action());
        assertEquals(FailureSeverity.HIGH, report.failureScenarioResult().severity());
        assertTrue(report.summary().contains("overload pressure"));
    }

    @Test
    void lowSampleSummaryReflectsConservativeInsufficientSampleBehavior() {
        LaseEvaluationInput input = input("low-sample", RequestPriority.USER, healthyCandidates(),
                10,
                new ConcurrencyFeedback("server-a", 20, 500.0, 500.0, 800.0, 0.90, 5, NOW),
                normalSheddingSignal(),
                new AutoscalingSignal("checkout", 4, 2, 10, 40, 40, 500.0, 800.0, 0.90, 5, NOW),
                new FailureScenarioSignal("low-sample-failure", FailureScenarioType.PARTIAL_OUTAGE, "checkout",
                        4, 0, 40, 40, 40, 500.0, 800.0, 0.90, 5, NOW));

        LaseEvaluationReport report = engine(7).evaluate(input, CONFIG);

        assertTrue(report.summary().contains("HOLD"));
        assertTrue(report.summary().contains("LOW"));
        assertTrue(report.summary().contains("insufficient sample"));
    }

    @Test
    void inputValidationRejectsMissingOrUnsafeFields() {
        assertAll("invalid input",
                () -> assertInvalid(() -> input(null, RequestPriority.USER, healthyCandidates(), 10,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input(" ", RequestPriority.USER, healthyCandidates(), 10,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("missing-priority", null, healthyCandidates(), 10,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("empty-candidates", RequestPriority.USER, List.of(), 10,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("null-candidate", RequestPriority.USER,
                        Arrays.asList(healthyServer("server-a"), null), 10, healthyFeedback(), normalSheddingSignal(),
                        normalAutoscalingSignal(), normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("bad-limit", RequestPriority.USER, healthyCandidates(), 0,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("missing-feedback", RequestPriority.USER, healthyCandidates(), 10,
                        null, normalSheddingSignal(), normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("missing-shedding", RequestPriority.USER, healthyCandidates(), 10,
                        healthyFeedback(), null, normalAutoscalingSignal(),
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("missing-autoscale", RequestPriority.USER, healthyCandidates(), 10,
                        healthyFeedback(), normalSheddingSignal(), null,
                        normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE))),
                () -> assertInvalid(() -> input("missing-failure", RequestPriority.USER, healthyCandidates(), 10,
                        healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(), null)),
                () -> assertInvalid(() -> new LaseEvaluationInput("missing-time", RequestPriority.USER,
                        healthyCandidates(), 10, healthyFeedback(), normalSheddingSignal(),
                        normalAutoscalingSignal(), normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE), null))
        );
    }

    @Test
    void configValidationRejectsMissingComponentConfigs() {
        assertAll("invalid config",
                () -> assertInvalid(() -> new LaseEvaluationConfig(null, SHEDDING_CONFIG,
                        AUTOSCALER_CONFIG, FAILURE_CONFIG)),
                () -> assertInvalid(() -> new LaseEvaluationConfig(CONCURRENCY_CONFIG, null,
                        AUTOSCALER_CONFIG, FAILURE_CONFIG)),
                () -> assertInvalid(() -> new LaseEvaluationConfig(CONCURRENCY_CONFIG, SHEDDING_CONFIG,
                        null, FAILURE_CONFIG)),
                () -> assertInvalid(() -> new LaseEvaluationConfig(CONCURRENCY_CONFIG, SHEDDING_CONFIG,
                        AUTOSCALER_CONFIG, null))
        );
    }

    @Test
    void reportValidationRejectsMissingComponentsSummaryOrTimestamp() {
        LaseEvaluationReport valid = engine(7).evaluate(normalInput(), CONFIG);

        assertAll("invalid report",
                () -> assertInvalid(() -> report(null, valid)),
                () -> assertInvalid(() -> new LaseEvaluationReport(" ", valid.routingDecision(),
                        valid.concurrencyDecision(), valid.loadSheddingDecision(),
                        valid.autoscalingRecommendation(), valid.failureScenarioResult(),
                        valid.summary(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("missing-routing", null,
                        valid.concurrencyDecision(), valid.loadSheddingDecision(),
                        valid.autoscalingRecommendation(), valid.failureScenarioResult(),
                        valid.summary(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("missing-concurrency",
                        valid.routingDecision(), null, valid.loadSheddingDecision(),
                        valid.autoscalingRecommendation(), valid.failureScenarioResult(),
                        valid.summary(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("missing-shedding",
                        valid.routingDecision(), valid.concurrencyDecision(), null,
                        valid.autoscalingRecommendation(), valid.failureScenarioResult(),
                        valid.summary(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("missing-autoscaling",
                        valid.routingDecision(), valid.concurrencyDecision(), valid.loadSheddingDecision(),
                        null, valid.failureScenarioResult(), valid.summary(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("missing-failure",
                        valid.routingDecision(), valid.concurrencyDecision(), valid.loadSheddingDecision(),
                        valid.autoscalingRecommendation(), null, valid.summary(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("blank-summary",
                        valid.routingDecision(), valid.concurrencyDecision(), valid.loadSheddingDecision(),
                        valid.autoscalingRecommendation(), valid.failureScenarioResult(), " ", valid.timestamp())),
                () -> assertInvalid(() -> new LaseEvaluationReport("missing-time",
                        valid.routingDecision(), valid.concurrencyDecision(), valid.loadSheddingDecision(),
                        valid.autoscalingRecommendation(), valid.failureScenarioResult(), valid.summary(), null))
        );
    }

    @Test
    void behaviorIsDeterministicWithFixedClockAndSeededRoutingSampler() {
        LaseEvaluationInput input = input("deterministic", RequestPriority.USER,
                List.of(healthyServer("server-a"), healthyServer("server-b"), healthyServer("server-c")),
                10, healthyFeedback(), normalSheddingSignal(), normalAutoscalingSignal(),
                normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE));

        LaseEvaluationReport first = engine(11).evaluate(input, CONFIG);
        LaseEvaluationReport second = engine(11).evaluate(input, CONFIG);

        assertEquals(first, second);
    }

    private LaseEvaluationEngine engine(long seed) {
        return new LaseEvaluationEngine(
                new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(seed), CLOCK),
                new LoadSheddingPolicy(),
                new ShadowAutoscaler(),
                new FailureScenarioRunner(),
                CLOCK);
    }

    private LaseEvaluationInput normalInput() {
        return input("normal", RequestPriority.USER, healthyCandidates(), 10, healthyFeedback(),
                normalSheddingSignal(), normalAutoscalingSignal(), normalFailureSignal(FailureScenarioType.TRAFFIC_SPIKE));
    }

    private LaseEvaluationInput overloadedInput() {
        return input("overloaded", RequestPriority.PREFETCH, healthyCandidates(), 20,
                new ConcurrencyFeedback("server-a", 30, 260.0, 320.0, 480.0, 0.20, 100, NOW),
                new LoadSheddingSignal("checkout", 38, 40, 30, 300.0, 0.20, NOW),
                new AutoscalingSignal("checkout", 4, 2, 10, 38, 30, 300.0, 420.0, 0.05, 100, NOW),
                new FailureScenarioSignal("queue-pressure", FailureScenarioType.QUEUE_BACKLOG, "checkout",
                        4, 4, 38, 40, 30, 300.0, 420.0, 0.05, 100, NOW));
    }

    private LaseEvaluationInput input(String evaluationId,
                                      RequestPriority priority,
                                      List<ServerStateVector> candidates,
                                      int currentConcurrencyLimit,
                                      ConcurrencyFeedback feedback,
                                      LoadSheddingSignal sheddingSignal,
                                      AutoscalingSignal autoscalingSignal,
                                      FailureScenarioSignal failureSignal) {
        return new LaseEvaluationInput(evaluationId, priority, candidates, currentConcurrencyLimit,
                feedback, sheddingSignal, autoscalingSignal, failureSignal, NOW);
    }

    private LaseEvaluationReport report(String evaluationId, LaseEvaluationReport valid) {
        return new LaseEvaluationReport(evaluationId, valid.routingDecision(), valid.concurrencyDecision(),
                valid.loadSheddingDecision(), valid.autoscalingRecommendation(), valid.failureScenarioResult(),
                valid.summary(), valid.timestamp());
    }

    private List<ServerStateVector> healthyCandidates() {
        return List.of(healthyServer("server-a"), slowerServer("server-b"));
    }

    private ServerStateVector healthyServer(String serverId) {
        return new ServerStateVector(serverId, true, 2, 100.0, 50.0, 80.0, 120.0,
                150.0, 0.01, 1, NOW);
    }

    private ServerStateVector slowerServer(String serverId) {
        return new ServerStateVector(serverId, true, 5, 100.0, 50.0, 140.0, 190.0,
                260.0, 0.03, 5, NOW);
    }

    private ServerStateVector unhealthyServer(String serverId) {
        return new ServerStateVector(serverId, false, 2, 100.0, 50.0, 80.0, 120.0,
                150.0, 0.01, 1, NOW);
    }

    private ConcurrencyFeedback healthyFeedback() {
        return new ConcurrencyFeedback("server-a", 2, 80.0, 120.0, 150.0, 0.01, 100, NOW);
    }

    private LoadSheddingSignal normalSheddingSignal() {
        return new LoadSheddingSignal("checkout", 8, 40, 2, 100.0, 0.01, NOW);
    }

    private AutoscalingSignal normalAutoscalingSignal() {
        return new AutoscalingSignal("checkout", 4, 2, 10, 2, 0, 100.0, 150.0, 0.01, 100, NOW);
    }

    private FailureScenarioSignal normalFailureSignal(FailureScenarioType scenarioType) {
        return new FailureScenarioSignal("normal-" + scenarioType.name().toLowerCase(), scenarioType, "checkout",
                4, 4, 8, 40, 2, 100.0, 150.0, 0.01, 100, NOW);
    }

    private void assertInvalid(Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable);
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }
}
