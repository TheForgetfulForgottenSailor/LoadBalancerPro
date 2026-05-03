package test.core;

import core.RoutingComparisonEngine;
import core.RoutingComparisonReport;
import core.RoutingComparisonResult;
import core.RoutingDecision;
import core.RoutingStrategy;
import core.RoutingStrategyId;
import core.RoutingStrategyRegistry;
import core.ServerScoreCalculator;
import core.ServerStateVector;
import core.TailLatencyPowerOfTwoStrategy;
import core.WeightedLeastLoadStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RoutingComparisonEngineTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void tailLatencyPowerOfTwoStrategyKeepsExistingDecisionBehavior() {
        TailLatencyPowerOfTwoStrategy strategy = new TailLatencyPowerOfTwoStrategy(
                new ServerScoreCalculator(), new Random(7), FIXED_CLOCK);
        ServerStateVector better = state("better", true, 5, 100.0, 100.0,
                20.0, 40.0, 80.0, 0.01, 1);
        ServerStateVector riskier = state("riskier", true, 75, 100.0, 100.0,
                35.0, 120.0, 220.0, 0.15, 10);

        RoutingDecision decision = strategy.choose(List.of(better, riskier));

        assertEquals(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO, strategy.id());
        assertEquals("better", decision.chosenServer().orElseThrow().serverId());
        assertEquals(TailLatencyPowerOfTwoStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertEquals(List.of("better", "riskier"), decision.explanation().candidateServersConsidered());
        assertEquals(NOW, decision.explanation().timestamp());
    }

    @Test
    void defaultRegistryReturnsTailLatencyPowerOfTwoAndWeightedLeastLoadStrategies() {
        RoutingStrategyRegistry registry = RoutingStrategyRegistry.defaultRegistry();

        assertEquals(List.of(
                RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO,
                RoutingStrategyId.WEIGHTED_LEAST_LOAD), registry.registeredIds());
        assertTrue(registry.find(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO).isPresent());
        assertTrue(registry.find(RoutingStrategyId.WEIGHTED_LEAST_LOAD).isPresent());
        assertEquals(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO,
                registry.require(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO).id());
        assertEquals(RoutingStrategyId.WEIGHTED_LEAST_LOAD,
                registry.require(RoutingStrategyId.WEIGHTED_LEAST_LOAD).id());
    }

    @Test
    void unknownStrategyIdsAreAbsentOrRejectedSafely() {
        RoutingStrategyRegistry emptyRegistry = new RoutingStrategyRegistry(List.of());

        assertTrue(RoutingStrategyId.fromName("missing-strategy").isEmpty());
        assertTrue(RoutingStrategyId.fromName("").isEmpty());
        assertTrue(RoutingStrategyId.fromName(null).isEmpty());
        assertTrue(emptyRegistry.find(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> emptyRegistry.require(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO));
    }

    @Test
    void routingStrategyIdResolvesWeightedLeastLoadAliases() {
        assertEquals(RoutingStrategyId.WEIGHTED_LEAST_LOAD,
                RoutingStrategyId.fromName("WEIGHTED_LEAST_LOAD").orElseThrow());
        assertEquals(RoutingStrategyId.WEIGHTED_LEAST_LOAD,
                RoutingStrategyId.fromName("weighted-least-load").orElseThrow());
    }

    @Test
    void duplicateStrategyRegistrationRemainsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingStrategyRegistry(List.of(
                new WeightedLeastLoadStrategy(FIXED_CLOCK),
                new WeightedLeastLoadStrategy(FIXED_CLOCK))));
    }

    @Test
    void comparisonEngineReturnsOneResultPerRequestedStrategy() {
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of(new TailLatencyPowerOfTwoStrategy(
                        new ServerScoreCalculator(), new Random(3), FIXED_CLOCK))),
                FIXED_CLOCK);

        RoutingComparisonReport report = engine.compare(healthyCandidates(),
                List.of(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO));

        assertEquals(List.of(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO), report.requestedStrategies());
        assertEquals(2, report.candidateCount());
        assertEquals(NOW, report.timestamp());
        assertEquals(1, report.results().size());
        RoutingComparisonResult result = report.results().get(0);
        assertEquals(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO, result.strategyId());
        assertEquals(RoutingComparisonResult.Status.SUCCESS, result.status());
        assertTrue(result.successful());
        assertEquals("lower-risk", result.decision().orElseThrow().chosenServer().orElseThrow().serverId());
        assertTrue(result.reason().contains("Chose lower-risk"));
    }

    @Test
    void comparisonEnginePreservesRequestedOrderDeterministically() {
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of(
                        new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(11), FIXED_CLOCK),
                        new WeightedLeastLoadStrategy(FIXED_CLOCK))),
                FIXED_CLOCK);
        List<RoutingStrategyId> requested = List.of(
                RoutingStrategyId.WEIGHTED_LEAST_LOAD,
                RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO);

        RoutingComparisonReport report = engine.compare(healthyCandidates(), requested);

        assertEquals(requested, report.requestedStrategies());
        assertEquals(2, report.results().size());
        assertEquals(requested.get(0), report.results().get(0).strategyId());
        assertEquals(requested.get(1), report.results().get(1).strategyId());
    }

    @Test
    void comparisonEngineCanCompareBothStrategiesTogether() {
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of(
                        new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(3), FIXED_CLOCK),
                        new WeightedLeastLoadStrategy(FIXED_CLOCK))),
                FIXED_CLOCK);

        RoutingComparisonReport report = engine.compare(healthyCandidates(), List.of(
                RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO,
                RoutingStrategyId.WEIGHTED_LEAST_LOAD));

        assertEquals(List.of(
                RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO,
                RoutingStrategyId.WEIGHTED_LEAST_LOAD), report.requestedStrategies());
        assertEquals(2, report.results().size());
        assertTrue(report.results().stream().allMatch(RoutingComparisonResult::successful));
        assertEquals(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO, report.results().get(0).strategyId());
        assertEquals(RoutingStrategyId.WEIGHTED_LEAST_LOAD, report.results().get(1).strategyId());
    }

    @Test
    void defaultComparisonOrderFollowsRegistryOrder() {
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of(
                        new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(3), FIXED_CLOCK),
                        new WeightedLeastLoadStrategy(FIXED_CLOCK))),
                FIXED_CLOCK);

        RoutingComparisonReport report = engine.compare(healthyCandidates());

        assertEquals(List.of(
                RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO,
                RoutingStrategyId.WEIGHTED_LEAST_LOAD), report.requestedStrategies());
        assertEquals(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO, report.results().get(0).strategyId());
        assertEquals(RoutingStrategyId.WEIGHTED_LEAST_LOAD, report.results().get(1).strategyId());
    }

    @Test
    void comparisonEngineHandlesEmptyAndAllUnhealthyCandidatesSafely() {
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of(new TailLatencyPowerOfTwoStrategy(
                        new ServerScoreCalculator(), new Random(1), FIXED_CLOCK))),
                FIXED_CLOCK);

        RoutingComparisonResult emptyResult = engine.compare(List.of(),
                List.of(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO)).results().get(0);
        RoutingComparisonResult unhealthyResult = engine.compare(List.of(
                        state("unhealthy-a", false, 0, 100.0, 100.0, 5.0, 10.0, 20.0, 0.0, 0),
                        state("unhealthy-b", false, 0, 100.0, 100.0, 5.0, 10.0, 20.0, 0.0, 0)),
                List.of(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO)).results().get(0);

        assertAll("safe no-candidate decisions",
                () -> assertEquals(RoutingComparisonResult.Status.SUCCESS, emptyResult.status()),
                () -> assertTrue(emptyResult.decision().orElseThrow().chosenServer().isEmpty()),
                () -> assertTrue(emptyResult.reason().contains("No healthy eligible servers")),
                () -> assertEquals(RoutingComparisonResult.Status.SUCCESS, unhealthyResult.status()),
                () -> assertTrue(unhealthyResult.decision().orElseThrow().chosenServer().isEmpty()),
                () -> assertTrue(unhealthyResult.reason().contains("No healthy eligible servers")));
    }

    @Test
    void comparisonEngineIsolatesStrategyFailure() {
        RoutingStrategy failingStrategy = new RoutingStrategy() {
            @Override
            public RoutingStrategyId id() {
                return RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO;
            }

            @Override
            public RoutingDecision choose(List<ServerStateVector> servers) {
                throw new IllegalStateException("synthetic strategy failure");
            }
        };
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of(failingStrategy)), FIXED_CLOCK);

        RoutingComparisonReport report = engine.compare(healthyCandidates(),
                List.of(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO));

        RoutingComparisonResult result = report.results().get(0);
        assertEquals(RoutingComparisonResult.Status.FAILED, result.status());
        assertFalse(result.successful());
        assertTrue(result.decision().isEmpty());
        assertTrue(result.reason().contains("synthetic strategy failure"));
    }

    @Test
    void comparisonEngineReportsUnregisteredStrategyWithoutCrashing() {
        RoutingComparisonEngine engine = new RoutingComparisonEngine(
                new RoutingStrategyRegistry(List.of()), FIXED_CLOCK);

        RoutingComparisonReport report = engine.compare(healthyCandidates(),
                List.of(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO));

        RoutingComparisonResult result = report.results().get(0);
        assertEquals(RoutingComparisonResult.Status.FAILED, result.status());
        assertEquals(RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO, result.strategyId());
        assertTrue(result.decision().isEmpty());
        assertTrue(result.reason().contains("not registered"));
    }

    private List<ServerStateVector> healthyCandidates() {
        return List.of(
                state("lower-risk", true, 5, 100.0, 100.0, 20.0, 40.0, 80.0, 0.01, 1),
                state("higher-risk", true, 75, 100.0, 100.0, 35.0, 120.0, 220.0, 0.15, 10));
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
}
