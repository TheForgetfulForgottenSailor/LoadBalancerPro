package test.core;

import core.NetworkAwarenessSignal;
import core.RoutingDecision;
import core.ServerStateVector;
import core.WeightedLeastLoadStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class WeightedLeastLoadStrategyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final WeightedLeastLoadStrategy strategy = new WeightedLeastLoadStrategy(FIXED_CLOCK);

    @Test
    void selectsLowerNormalizedLoad() {
        RoutingDecision decision = strategy.choose(List.of(
                state("busy", 80, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("quiet", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("quiet", decision.chosenServer().orElseThrow().serverId());
        assertTrue(score(decision, "quiet") < score(decision, "busy"));
    }

    @Test
    void favorsHigherCapacityWhenRawInFlightCountsAreSimilar() {
        RoutingDecision decision = strategy.choose(List.of(
                state("small", 10, 10.0, 10.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("large", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("large", decision.chosenServer().orElseThrow().serverId());
        assertTrue(score(decision, "large") < score(decision, "small"));
    }

    @Test
    void respectsServerWeight() {
        RoutingDecision decision = strategy.choose(List.of(
                state("base", 20, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("weighted", 20, 100.0, 100.0, 4.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("weighted", decision.chosenServer().orElseThrow().serverId());
        assertTrue(score(decision, "weighted") < score(decision, "base"));
    }

    @Test
    void missingWeightDefaultsSafely() {
        ServerStateVector missingWeight = stateWithoutWeight(
                "missing", 20, 100.0, 100.0, 10.0, 20.0, 40.0, 0.0, 0);
        ServerStateVector explicitWeight = state(
                "explicit", 20, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0);

        RoutingDecision decision = strategy.choose(List.of(missingWeight, explicitWeight));

        assertEquals(1.0, missingWeight.weight(), 0.0);
        assertEquals(score(decision, "missing"), score(decision, "explicit"), 0.0);
    }

    @Test
    void zeroWeightDefaultsSafely() {
        RoutingDecision decision = strategy.choose(List.of(
                state("default", 20, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("zero", 20, 100.0, 100.0, 0.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals(score(decision, "default"), score(decision, "zero"), 0.0);
    }

    @Test
    void verySmallPositiveWeightClampsSafely() {
        RoutingDecision decision = strategy.choose(List.of(
                state("min", 20, 100.0, 100.0, 0.1, 10.0, 20.0, 40.0, 0.0, 0),
                state("tiny", 20, 100.0, 100.0, 0.01, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals(score(decision, "min"), score(decision, "tiny"), 0.0);
    }

    @Test
    void invalidNegativeOrNonFiniteWeightIsRejectedByStateValidation() {
        assertAll("invalid weight",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> state("negative", 1, 100.0, 100.0, -0.1, 10.0, 20.0, 40.0, 0.0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> state("nan", 1, 100.0, 100.0, Double.NaN, 10.0, 20.0, 40.0, 0.0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> state("infinite", 1, 100.0, 100.0, Double.POSITIVE_INFINITY,
                                10.0, 20.0, 40.0, 0.0, 0)));
    }

    @Test
    void excludesUnhealthyServers() {
        RoutingDecision decision = strategy.choose(List.of(
                state("unhealthy-light", false, 0, 100.0, 100.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("healthy-busy", true, 90, 100.0, 100.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("healthy-busy", decision.chosenServer().orElseThrow().serverId());
        assertFalse(decision.explanation().candidateServersConsidered().contains("unhealthy-light"));
    }

    @Test
    void handlesMissingCapacitySafely() {
        RoutingDecision decision = strategy.choose(List.of(
                state("higher-load", true, 2, OptionalDouble.empty(), OptionalDouble.empty(),
                        1.0, 10.0, 20.0, 40.0, 0.0, OptionalInt.empty()),
                state("lower-load", true, 1, OptionalDouble.empty(), OptionalDouble.empty(),
                        1.0, 10.0, 20.0, 40.0, 0.0, OptionalInt.empty())));

        assertEquals("lower-load", decision.chosenServer().orElseThrow().serverId());
    }

    @Test
    void handlesZeroCapacitySafely() {
        RoutingDecision decision = strategy.choose(List.of(
                state("one", true, 1, OptionalDouble.of(0.0), OptionalDouble.empty(),
                        1.0, 10.0, 20.0, 40.0, 0.0, OptionalInt.of(0)),
                state("zero", true, 0, OptionalDouble.of(0.0), OptionalDouble.empty(),
                        1.0, 10.0, 20.0, 40.0, 0.0, OptionalInt.of(0))));

        assertEquals("zero", decision.chosenServer().orElseThrow().serverId());
    }

    @Test
    void handlesQueuePressure() {
        RoutingDecision decision = strategy.choose(List.of(
                state("queued", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 50),
                state("clear", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("clear", decision.chosenServer().orElseThrow().serverId());
        assertTrue(score(decision, "clear") < score(decision, "queued"));
    }

    @Test
    void handlesLatencyPressure() {
        RoutingDecision decision = strategy.choose(List.of(
                state("high-latency", 10, 100.0, 100.0, 1.0, 70.0, 80.0, 90.0, 0.0, 0),
                state("low-latency", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("low-latency", decision.chosenServer().orElseThrow().serverId());
        assertTrue(score(decision, "low-latency") < score(decision, "high-latency"));
    }

    @Test
    void handlesErrorRatePressure() {
        RoutingDecision decision = strategy.choose(List.of(
                state("erroring", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.5, 0),
                state("clean", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("clean", decision.chosenServer().orElseThrow().serverId());
        assertTrue(score(decision, "clean") < score(decision, "erroring"));
    }

    @Test
    void deterministicTieBreakingUsesServerId() {
        RoutingDecision decision = strategy.choose(List.of(
                state("zeta", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("alpha", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals("alpha", decision.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("alpha", "zeta"), decision.explanation().candidateServersConsidered());
    }

    @Test
    void emptyCandidateListReturnsSafeNoDecision() {
        RoutingDecision decision = strategy.choose(List.of());

        assertTrue(decision.chosenServer().isEmpty());
        assertEquals(WeightedLeastLoadStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertTrue(decision.explanation().candidateServersConsidered().isEmpty());
        assertTrue(decision.explanation().chosenServerId().isEmpty());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("No healthy eligible servers"));
        assertEquals(NOW, decision.explanation().timestamp());
    }

    @Test
    void allUnhealthyCandidatesReturnSafeNoDecision() {
        RoutingDecision decision = strategy.choose(List.of(
                state("a", false, 0, 100.0, 100.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("b", false, 0, 100.0, 100.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertTrue(decision.chosenServer().isEmpty());
        assertTrue(decision.explanation().candidateServersConsidered().isEmpty());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().chosenServerId().isEmpty());
    }

    @Test
    void doesNotMutateInputState() {
        List<ServerStateVector> candidates = new ArrayList<>(List.of(
                state("b", 20, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("a", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));
        List<ServerStateVector> before = List.copyOf(candidates);

        strategy.choose(candidates);

        assertEquals(before, candidates);
    }

    @Test
    void producesCompleteDecisionMetadata() {
        RoutingDecision decision = strategy.choose(List.of(
                state("b", 20, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("a", 10, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0),
                state("c", 30, 100.0, 100.0, 1.0, 10.0, 20.0, 40.0, 0.0, 0)));

        assertEquals(WeightedLeastLoadStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertEquals(List.of("a", "b", "c"), decision.explanation().candidateServersConsidered());
        assertEquals("a", decision.explanation().chosenServerId().orElseThrow());
        assertEquals(3, decision.explanation().scores().size());
        assertTrue(decision.explanation().scores().containsKey("a"));
        assertTrue(decision.explanation().reason().contains("weighted least-load score"));
        assertEquals(NOW, decision.explanation().timestamp());
    }

    private double score(RoutingDecision decision, String serverId) {
        return decision.explanation().scores().get(serverId);
    }

    private ServerStateVector state(String id,
                                    int inFlight,
                                    double configuredCapacity,
                                    double estimatedConcurrencyLimit,
                                    double weight,
                                    double averageLatencyMillis,
                                    double p95LatencyMillis,
                                    double p99LatencyMillis,
                                    double recentErrorRate,
                                    int queueDepth) {
        return state(id, true, inFlight, configuredCapacity, estimatedConcurrencyLimit, weight,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth);
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
        return state(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit, 1.0,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth);
    }

    private ServerStateVector state(String id,
                                    boolean healthy,
                                    int inFlight,
                                    double configuredCapacity,
                                    double estimatedConcurrencyLimit,
                                    double weight,
                                    double averageLatencyMillis,
                                    double p95LatencyMillis,
                                    double p99LatencyMillis,
                                    double recentErrorRate,
                                    int queueDepth) {
        return state(id, healthy, inFlight, OptionalDouble.of(configuredCapacity),
                OptionalDouble.of(estimatedConcurrencyLimit), weight, averageLatencyMillis, p95LatencyMillis,
                p99LatencyMillis, recentErrorRate, OptionalInt.of(queueDepth));
    }

    private ServerStateVector state(String id,
                                    boolean healthy,
                                    int inFlight,
                                    OptionalDouble configuredCapacity,
                                    OptionalDouble estimatedConcurrencyLimit,
                                    double weight,
                                    double averageLatencyMillis,
                                    double p95LatencyMillis,
                                    double p99LatencyMillis,
                                    double recentErrorRate,
                                    OptionalInt queueDepth) {
        return new ServerStateVector(id, healthy, inFlight, configuredCapacity, estimatedConcurrencyLimit, weight,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth,
                NetworkAwarenessSignal.neutral(id, NOW), NOW);
    }

    private ServerStateVector stateWithoutWeight(String id,
                                                 int inFlight,
                                                 double configuredCapacity,
                                                 double estimatedConcurrencyLimit,
                                                 double averageLatencyMillis,
                                                 double p95LatencyMillis,
                                                 double p99LatencyMillis,
                                                 double recentErrorRate,
                                                 int queueDepth) {
        return new ServerStateVector(id, true, inFlight, OptionalDouble.of(configuredCapacity),
                OptionalDouble.of(estimatedConcurrencyLimit), averageLatencyMillis, p95LatencyMillis,
                p99LatencyMillis, recentErrorRate, OptionalInt.of(queueDepth),
                NetworkAwarenessSignal.neutral(id, NOW), NOW);
    }
}
