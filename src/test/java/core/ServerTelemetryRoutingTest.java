package test.core;

import core.RoutingDecision;
import core.RoutingDecisionExplanation;
import core.ServerScoreCalculator;
import core.ServerStateVector;
import core.TailLatencyPowerOfTwoStrategy;
import core.NetworkAwarenessSignal;
import core.LoadBalancer;
import core.LoadDistributionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ServerTelemetryRoutingTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ServerScoreCalculator scoreCalculator = new ServerScoreCalculator();

    @Test
    void invalidTelemetryStateIsRejected() {
        assertAll("invalid telemetry state",
                () -> assertInvalid("null server id",
                        () -> state(null, true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("blank server id",
                        () -> state("   ", true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("null configured capacity optional",
                        () -> canonicalState("server", true, 0, null, OptionalDouble.of(100.0),
                                1.0, 2.0, 3.0, 0.0, OptionalInt.of(0), NOW)),
                () -> assertInvalid("null estimated concurrency optional",
                        () -> canonicalState("server", true, 0, OptionalDouble.of(100.0), null,
                                1.0, 2.0, 3.0, 0.0, OptionalInt.of(0), NOW)),
                () -> assertInvalid("null queue depth optional",
                        () -> canonicalState("server", true, 0, OptionalDouble.of(100.0), OptionalDouble.of(100.0),
                                1.0, 2.0, 3.0, 0.0, null, NOW)),
                () -> assertInvalid("null network awareness signal",
                        () -> canonicalState("server", true, 0, OptionalDouble.of(100.0), OptionalDouble.of(100.0),
                                1.0, 2.0, 3.0, 0.0, OptionalInt.of(0), null, NOW)),
                () -> assertInvalid("null timestamp",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0,
                                (Instant) null)),
                () -> assertInvalid("negative in-flight count",
                        () -> state("server", true, -1, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("negative configured capacity",
                        () -> state("server", true, 0, -1.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("negative estimated concurrency limit",
                        () -> state("server", true, 0, 100.0, -1.0, 1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("zero estimated concurrency limit",
                        () -> state("server", true, 0, 100.0, 0.0, 1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("negative average latency",
                        () -> state("server", true, 0, 100.0, 100.0, -1.0, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("negative p95 latency",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, -2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("negative p99 latency",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, -3.0, 0.0, 0)),
                () -> assertInvalid("NaN average latency",
                        () -> state("server", true, 0, 100.0, 100.0, Double.NaN, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("NaN p95 latency",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, Double.NaN, 3.0, 0.0, 0)),
                () -> assertInvalid("NaN p99 latency",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, Double.NaN, 0.0, 0)),
                () -> assertInvalid("infinite average latency",
                        () -> state("server", true, 0, 100.0, 100.0,
                                Double.POSITIVE_INFINITY, 2.0, 3.0, 0.0, 0)),
                () -> assertInvalid("infinite p95 latency",
                        () -> state("server", true, 0, 100.0, 100.0,
                                1.0, Double.POSITIVE_INFINITY, 3.0, 0.0, 0)),
                () -> assertInvalid("infinite p99 latency",
                        () -> state("server", true, 0, 100.0, 100.0,
                                1.0, 2.0, Double.POSITIVE_INFINITY, 0.0, 0)),
                () -> assertInvalid("error rate below zero",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, -0.01, 0)),
                () -> assertInvalid("error rate above one",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 1.01, 0)),
                () -> assertInvalid("NaN error rate",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, Double.NaN, 0)),
                () -> assertInvalid("infinite error rate",
                        () -> state("server", true, 0, 100.0, 100.0,
                                1.0, 2.0, 3.0, Double.POSITIVE_INFINITY, 0)),
                () -> assertInvalid("negative queue depth",
                        () -> state("server", true, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, -1))
        );
    }

    @Test
    void p95AndP99LatencyIncreaseScore() {
        ServerStateVector lowTailLatency = state("low-tail", true, 10, 100.0, 100.0,
                25.0, 40.0, 60.0, 0.01, 2);
        ServerStateVector highTailLatency = state("high-tail", true, 10, 100.0, 100.0,
                25.0, 180.0, 260.0, 0.01, 2);

        assertTrue(scoreCalculator.score(highTailLatency) > scoreCalculator.score(lowTailLatency));
    }

    @Test
    void scoreCalculationIsDeterministic() {
        ServerStateVector server = state("stable", true, 12, 100.0, 80.0,
                20.0, 60.0, 90.0, 0.03, 4);

        double firstScore = scoreCalculator.score(server);
        double secondScore = scoreCalculator.score(server);

        assertEquals(firstScore, secondScore, 0.0);
    }

    @Test
    void unhealthyServersAreNotSelected() {
        TailLatencyPowerOfTwoStrategy strategy = strategyWithSeed(7);
        ServerStateVector unhealthyFastServer = state("unhealthy-fast", false, 0, 100.0, 100.0,
                5.0, 10.0, 15.0, 0.0, 0);
        ServerStateVector healthySlowServer = state("healthy-slow", true, 80, 100.0, 100.0,
                80.0, 160.0, 220.0, 0.2, 20);

        RoutingDecision decision = strategy.choose(List.of(unhealthyFastServer, healthySlowServer));

        assertTrue(decision.chosenServer().isPresent());
        assertEquals("healthy-slow", decision.chosenServer().get().serverId());
        assertFalse(decision.explanation().candidateServersConsidered().contains("unhealthy-fast"));
    }

    @Test
    void unhealthyServersReceiveCalculatorPenalty() {
        ServerStateVector healthyServer = state("same-metrics", true, 5, 100.0, 100.0,
                20.0, 40.0, 60.0, 0.01, 1);
        ServerStateVector unhealthyServer = state("same-metrics-unhealthy", false, 5, 100.0, 100.0,
                20.0, 40.0, 60.0, 0.01, 1);

        assertTrue(scoreCalculator.score(unhealthyServer) > scoreCalculator.score(healthyServer) + 900_000.0);
    }

    @Test
    void lowerInflightRatioWinsWhenLatencyIsSimilar() {
        TailLatencyPowerOfTwoStrategy strategy = strategyWithSeed(3);
        ServerStateVector lowerInflight = state("lower-inflight", true, 10, 100.0, 100.0,
                30.0, 70.0, 100.0, 0.01, 2);
        ServerStateVector higherInflight = state("higher-inflight", true, 80, 100.0, 100.0,
                30.0, 70.0, 100.0, 0.01, 2);

        assertTrue(scoreCalculator.score(lowerInflight) < scoreCalculator.score(higherInflight));

        RoutingDecision decision = strategy.choose(List.of(lowerInflight, higherInflight));

        assertTrue(decision.chosenServer().isPresent());
        assertEquals("lower-inflight", decision.chosenServer().get().serverId());
    }

    @Test
    void highErrorRatePenalizesScore() {
        ServerStateVector lowErrorRate = state("low-error", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1);
        ServerStateVector highErrorRate = state("high-error", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.40, 1);

        assertTrue(scoreCalculator.score(highErrorRate) > scoreCalculator.score(lowErrorRate));
    }

    @Test
    void neutralNetworkSignalDoesNotChangeScoreComparedWithCompatibilityConstructor() {
        ServerStateVector compatibilityState = state("server", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1);
        ServerStateVector explicitNeutral = state("server", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1, NetworkAwarenessSignal.neutral("server", NOW));

        assertEquals(scoreCalculator.score(compatibilityState), scoreCalculator.score(explicitNeutral), 0.0);
        assertEquals(0.0, scoreCalculator.networkRiskScore(explicitNeutral.networkAwarenessSignal()), 0.0);
    }

    @Test
    void networkAwarenessSignalsIncreaseShadowRiskScore() {
        ServerStateVector baseline = state("server", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1);
        ServerStateVector networkRisk = state("server", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1,
                new NetworkAwarenessSignal("server", 0.20, 0.30, 0.10, 50.0, true, 4, 100, NOW));

        assertTrue(scoreCalculator.networkRiskScore(networkRisk.networkAwarenessSignal()) > 0.0);
        assertTrue(scoreCalculator.score(networkRisk) > scoreCalculator.score(baseline));
    }

    @Test
    void eachNetworkAwarenessSignalContributesToScore() {
        ServerStateVector baseline = state("server", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1);
        double baselineScore = scoreCalculator.score(baseline);

        assertAll("network signal score contributions",
                () -> assertSignalRaisesScore(baselineScore, networkSignal(0.10, 0.0, 0.0, 0.0, false, 0)),
                () -> assertSignalRaisesScore(baselineScore, networkSignal(0.0, 0.10, 0.0, 0.0, false, 0)),
                () -> assertSignalRaisesScore(baselineScore, networkSignal(0.0, 0.0, 0.10, 0.0, false, 0)),
                () -> assertSignalRaisesScore(baselineScore, networkSignal(0.0, 0.0, 0.0, 25.0, false, 0)),
                () -> assertSignalRaisesScore(baselineScore, networkSignal(0.0, 0.0, 0.0, 0.0, true, 0)),
                () -> assertSignalRaisesScore(baselineScore, networkSignal(0.0, 0.0, 0.0, 0.0, false, 2))
        );
    }

    @Test
    void networkRiskCanChangeShadowRecommendationWithoutChangingLiveAllocation() {
        ServerStateVector networkHealthy = state("network-healthy", true, 20, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1, NetworkAwarenessSignal.neutral("network-healthy", NOW));
        ServerStateVector networkRisky = state("network-risky", true, 20, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1,
                new NetworkAwarenessSignal("network-risky", 0.30, 0.20, 0.15, 80.0, true, 5, 100, NOW));

        RoutingDecision decision = new TailLatencyPowerOfTwoStrategy(scoreCalculator, new Random(1), FIXED_CLOCK)
                .choose(List.of(networkHealthy, networkRisky));

        assertEquals("network-healthy", decision.chosenServer().orElseThrow().serverId());
        assertTrue(decision.explanation().scores().get("network-risky")
                > decision.explanation().scores().get("network-healthy"));

        LoadBalancer baseline = balancerForAllocationRegression(false);
        LoadBalancer observed = balancerForAllocationRegression(true);
        try {
            LoadDistributionResult expected = baseline.capacityAwareWithResult(60.0);
            LoadDistributionResult actual = observed.capacityAwareWithResult(60.0);

            assertEquals(expected.allocations(), actual.allocations());
            assertEquals(expected.unallocatedLoad(), actual.unallocatedLoad(), 0.01);
        } finally {
            baseline.shutdown();
            observed.shutdown();
        }
    }

    @Test
    void emptyServerListReturnsSafeEmptyDecision() {
        RoutingDecision decision = strategyWithSeed(1).choose(List.of());
        RoutingDecisionExplanation explanation = decision.explanation();

        assertTrue(decision.chosenServer().isEmpty());
        assertEquals("TAIL_LATENCY_POWER_OF_TWO", explanation.strategyUsed());
        assertTrue(explanation.candidateServersConsidered().isEmpty());
        assertTrue(explanation.chosenServerId().isEmpty());
        assertTrue(explanation.scores().isEmpty());
        assertTrue(explanation.reason().contains("No healthy eligible servers"));
        assertEquals(NOW, explanation.timestamp());
    }

    @Test
    void allUnhealthyServersReturnSafeEmptyDecisionWithoutScoring() {
        RoutingDecision decision = strategyWithSeed(1).choose(List.of(
                state("unhealthy-a", false, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0),
                state("unhealthy-b", false, 0, 100.0, 100.0, 1.0, 2.0, 3.0, 0.0, 0)
        ));
        RoutingDecisionExplanation explanation = decision.explanation();

        assertTrue(decision.chosenServer().isEmpty());
        assertTrue(explanation.candidateServersConsidered().isEmpty());
        assertTrue(explanation.scores().isEmpty());
        assertTrue(explanation.chosenServerId().isEmpty());
    }

    @Test
    void oneHealthyServerIsChosenAndScored() {
        RoutingDecision decision = strategyWithSeed(1).choose(List.of(
                state("solo", true, 1, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0)
        ));
        RoutingDecisionExplanation explanation = decision.explanation();

        assertEquals("solo", decision.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("solo"), explanation.candidateServersConsidered());
        assertEquals("solo", explanation.chosenServerId().orElseThrow());
        assertEquals(1, explanation.scores().size());
        assertTrue(explanation.scores().containsKey("solo"));
        assertTrue(explanation.reason().contains("only healthy candidate"));
    }

    @Test
    void twoHealthyServersEvaluateBothAndChooseLowerScore() {
        ServerStateVector lowerRisk = state("lower-risk", true, 5, 100.0, 100.0,
                20.0, 40.0, 60.0, 0.01, 1);
        ServerStateVector higherRisk = state("higher-risk", true, 50, 100.0, 100.0,
                30.0, 120.0, 180.0, 0.10, 5);

        RoutingDecision decision = strategyWithSeed(1).choose(List.of(lowerRisk, higherRisk));
        RoutingDecisionExplanation explanation = decision.explanation();

        assertEquals("lower-risk", decision.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("lower-risk", "higher-risk"), explanation.candidateServersConsidered());
        assertEquals(2, explanation.scores().size());
        assertTrue(explanation.scores().get("lower-risk") < explanation.scores().get("higher-risk"));
    }

    @Test
    void powerOfTwoOnlyEvaluatesTwoCandidatesWhenMoreThanTwoHealthyServersExist() {
        TailLatencyPowerOfTwoStrategy strategy = strategyWithSeed(11);
        List<ServerStateVector> servers = List.of(
                state("a", true, 1, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0),
                state("b", true, 2, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0),
                state("c", true, 3, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0),
                state("d", true, 4, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0)
        );

        RoutingDecision decision = strategy.choose(servers);
        RoutingDecisionExplanation explanation = decision.explanation();

        assertEquals(2, explanation.candidateServersConsidered().size());
        assertEquals(2, explanation.scores().size());
        assertTrue(servers.stream()
                .map(ServerStateVector::serverId)
                .toList()
                .containsAll(explanation.candidateServersConsidered()));
    }

    @Test
    void seededSamplingProducesRepeatableCandidates() {
        List<ServerStateVector> servers = List.of(
                state("a", true, 1, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0),
                state("b", true, 2, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0),
                state("c", true, 3, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0),
                state("d", true, 4, 100.0, 100.0, 10.0, 20.0, 30.0, 0.0, 0)
        );

        RoutingDecision firstDecision = strategyWithSeed(42).choose(servers);
        RoutingDecision secondDecision = strategyWithSeed(42).choose(servers);

        assertEquals(firstDecision.explanation().candidateServersConsidered(),
                secondDecision.explanation().candidateServersConsidered());
        assertEquals(firstDecision.chosenServer().orElseThrow().serverId(),
                secondDecision.chosenServer().orElseThrow().serverId());
    }

    @Test
    void decisionExplanationIncludesChosenServerCandidatesScoresAndReason() {
        TailLatencyPowerOfTwoStrategy strategy = strategyWithSeed(5);
        ServerStateVector better = state("better", true, 5, 100.0, 100.0,
                20.0, 40.0, 80.0, 0.01, 1);
        ServerStateVector riskier = state("riskier", true, 75, 100.0, 100.0,
                35.0, 120.0, 220.0, 0.15, 10);

        RoutingDecision decision = strategy.choose(List.of(better, riskier));
        RoutingDecisionExplanation explanation = decision.explanation();

        assertTrue(decision.chosenServer().isPresent());
        assertEquals("better", decision.chosenServer().get().serverId());
        assertEquals("TAIL_LATENCY_POWER_OF_TWO", explanation.strategyUsed());
        assertEquals(List.of("better", "riskier"), explanation.candidateServersConsidered());
        assertEquals("better", explanation.chosenServerId().orElseThrow());
        assertTrue(explanation.scores().containsKey("better"));
        assertTrue(explanation.scores().containsKey("riskier"));
        assertEquals(explanation.candidateServersConsidered().size(), explanation.scores().size());
        assertEquals(scoreCalculator.score(better), explanation.scores().get("better"));
        assertTrue(explanation.reason().contains("better"));
        assertTrue(explanation.reason().contains("score"));
        assertEquals(NOW, explanation.timestamp());
    }

    private void assertInvalid(String label, Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable, label);
        assertNotNull(thrown.getMessage(), label);
        assertFalse(thrown.getMessage().isBlank(), label);
    }

    private TailLatencyPowerOfTwoStrategy strategyWithSeed(long seed) {
        return new TailLatencyPowerOfTwoStrategy(scoreCalculator, new Random(seed), FIXED_CLOCK);
    }

    private ServerStateVector state(String id,
                                    boolean healthy,
                                    int inFlight,
                                    double configuredCapacity,
                                    double estimatedConcurrencyLimit,
                                    double averageLatencyMillis,
                                    double p95LatencyMillis,
                                    double p99LatencyMillis,
                                    double recentErrorRate,
                                    int queueDepth) {
        return new ServerStateVector(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth, NOW);
    }

    private ServerStateVector state(String id,
                                    boolean healthy,
                                    int inFlight,
                                    double configuredCapacity,
                                    double estimatedConcurrencyLimit,
                                    double averageLatencyMillis,
                                    double p95LatencyMillis,
                                    double p99LatencyMillis,
                                    double recentErrorRate,
                                    int queueDepth,
                                    Instant timestamp) {
        return new ServerStateVector(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth, timestamp);
    }

    private ServerStateVector canonicalState(String id,
                                             boolean healthy,
                                             int inFlight,
                                             OptionalDouble configuredCapacity,
                                             OptionalDouble estimatedConcurrencyLimit,
                                             double averageLatencyMillis,
                                             double p95LatencyMillis,
                                             double p99LatencyMillis,
                                             double recentErrorRate,
                                             OptionalInt queueDepth,
                                             Instant timestamp) {
        return canonicalState(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth,
                NetworkAwarenessSignal.neutral(id, timestamp), timestamp);
    }

    private ServerStateVector canonicalState(String id,
                                             boolean healthy,
                                             int inFlight,
                                             OptionalDouble configuredCapacity,
                                             OptionalDouble estimatedConcurrencyLimit,
                                             double averageLatencyMillis,
                                             double p95LatencyMillis,
                                             double p99LatencyMillis,
                                             double recentErrorRate,
                                             OptionalInt queueDepth,
                                             NetworkAwarenessSignal networkAwarenessSignal,
                                             Instant timestamp) {
        return new ServerStateVector(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth,
                networkAwarenessSignal, timestamp);
    }

    private ServerStateVector state(String id,
                                    boolean healthy,
                                    int inFlight,
                                    double configuredCapacity,
                                    double estimatedConcurrencyLimit,
                                    double averageLatencyMillis,
                                    double p95LatencyMillis,
                                    double p99LatencyMillis,
                                    double recentErrorRate,
                                    int queueDepth,
                                    NetworkAwarenessSignal networkAwarenessSignal) {
        return new ServerStateVector(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth,
                networkAwarenessSignal, NOW);
    }

    private void assertSignalRaisesScore(double baselineScore, NetworkAwarenessSignal signal) {
        ServerStateVector state = state("server", true, 10, 100.0, 100.0,
                30.0, 60.0, 90.0, 0.01, 1, signal);
        assertTrue(scoreCalculator.score(state) > baselineScore);
    }

    private NetworkAwarenessSignal networkSignal(double timeoutRate,
                                                 double retryRate,
                                                 double connectionFailureRate,
                                                 double latencyJitterMillis,
                                                 boolean recentErrorBurst,
                                                 int requestTimeoutCount) {
        return new NetworkAwarenessSignal("server", timeoutRate, retryRate, connectionFailureRate,
                latencyJitterMillis, recentErrorBurst, requestTimeoutCount, 100, NOW);
    }

    private LoadBalancer balancerForAllocationRegression(boolean shadowEnabled) {
        LoadBalancer balancer = new LoadBalancer(shadowEnabled);
        core.Server first = new core.Server("S1", 20.0, 20.0, 20.0);
        first.setCapacity(80.0);
        core.Server second = new core.Server("S2", 10.0, 10.0, 10.0);
        second.setCapacity(100.0);
        balancer.addServer(first);
        balancer.addServer(second);
        return balancer;
    }
}
