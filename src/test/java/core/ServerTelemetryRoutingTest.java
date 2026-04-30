package test.core;

import core.RoutingDecision;
import core.RoutingDecisionExplanation;
import core.ServerScoreCalculator;
import core.ServerStateVector;
import core.TailLatencyPowerOfTwoStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ServerTelemetryRoutingTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ServerScoreCalculator scoreCalculator = new ServerScoreCalculator();

    @Test
    void p95AndP99LatencyIncreaseScore() {
        ServerStateVector lowTailLatency = state("low-tail", true, 10, 100.0, 100.0,
                25.0, 40.0, 60.0, 0.01, 2);
        ServerStateVector highTailLatency = state("high-tail", true, 10, 100.0, 100.0,
                25.0, 180.0, 260.0, 0.01, 2);

        assertTrue(scoreCalculator.score(highTailLatency) > scoreCalculator.score(lowTailLatency));
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
    void powerOfTwoOnlyEvaluatesTwoCandidatesWhenEnoughServersExist() {
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
        assertTrue(explanation.reason().contains("better"));
        assertTrue(explanation.reason().contains("score"));
        assertEquals(NOW, explanation.timestamp());
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
}
