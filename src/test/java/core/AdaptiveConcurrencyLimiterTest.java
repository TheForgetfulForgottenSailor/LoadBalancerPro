package test.core;

import core.AdaptiveConcurrencyConfig;
import core.AdaptiveConcurrencyLimiter;
import core.ConcurrencyFeedback;
import core.ConcurrencyLimitDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveConcurrencyLimiterTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AdaptiveConcurrencyConfig CONFIG =
            new AdaptiveConcurrencyConfig(2, 100, 3, 0.5, 120.0, 0.05, 10);

    private final AdaptiveConcurrencyLimiter limiter = new AdaptiveConcurrencyLimiter(CONFIG, FIXED_CLOCK);

    @Test
    void healthyFeedbackIncreasesLimitAdditively() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(20, feedback("api-1", 15,
                40.0, 70.0, 90.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.INCREASE, decision.action());
        assertEquals(20, decision.previousLimit());
        assertEquals(23, decision.nextLimit());
        assertTrue(decision.reason().contains("healthy"));
    }

    @Test
    void latencyAboveTargetDecreasesLimitMultiplicatively() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(40, feedback("api-1", 25,
                80.0, 180.0, 240.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.DECREASE, decision.action());
        assertEquals(20, decision.nextLimit());
        assertTrue(decision.reason().contains("p95 latency"));
    }

    @Test
    void highErrorRateDecreasesLimit() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(40, feedback("api-1", 25,
                60.0, 90.0, 110.0, 0.20, 200));

        assertEquals(ConcurrencyLimitDecision.Action.DECREASE, decision.action());
        assertEquals(20, decision.nextLimit());
        assertTrue(decision.reason().contains("error rate"));
    }

    @Test
    void badLatencyAndHighErrorsChooseSafeDecrease() {
        ConcurrencyLimitDecision latencyOnly = limiter.calculateNextLimit(40, feedback("api-1", 25,
                60.0, 180.0, 240.0, 0.01, 200));
        ConcurrencyLimitDecision bothBad = limiter.calculateNextLimit(40, feedback("api-1", 25,
                60.0, 180.0, 240.0, 0.20, 200));

        assertEquals(ConcurrencyLimitDecision.Action.DECREASE, bothBad.action());
        assertTrue(bothBad.nextLimit() <= latencyOnly.nextLimit());
        assertTrue(bothBad.reason().contains("latency"));
        assertTrue(bothBad.reason().contains("error rate"));
    }

    @Test
    void limitNeverExceedsMaximum() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(99, feedback("api-1", 10,
                40.0, 70.0, 90.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(100, decision.nextLimit());
        assertTrue(decision.reason().contains("max"));
    }

    @Test
    void currentLimitAboveMaximumClampsNextLimitSafely() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(150, feedback("api-1", 10,
                40.0, 70.0, 90.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(150, decision.previousLimit());
        assertEquals(100, decision.nextLimit());
        assertTrue(decision.reason().contains("max"));
    }

    @Test
    void currentLimitExactlyMaximumClampsHealthyIncreaseSafely() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(100, feedback("api-1", 10,
                40.0, 70.0, 90.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(100, decision.previousLimit());
        assertEquals(100, decision.nextLimit());
        assertTrue(decision.reason().contains("max"));
    }

    @Test
    void additiveStepLargerThanRemainingHeadroomClampsToMaximum() {
        AdaptiveConcurrencyConfig config = new AdaptiveConcurrencyConfig(2, 100, 25, 0.5, 120.0, 0.05, 10);
        AdaptiveConcurrencyLimiter limiterWithLargeStep = new AdaptiveConcurrencyLimiter(config, FIXED_CLOCK);

        ConcurrencyLimitDecision decision = limiterWithLargeStep.calculateNextLimit(90, feedback("api-1", 10,
                40.0, 70.0, 90.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(100, decision.nextLimit());
        assertTrue(decision.reason().contains("max"));
    }

    @Test
    void limitNeverDropsBelowMinimum() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(3, feedback("api-1", 3,
                80.0, 200.0, 280.0, 0.30, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(2, decision.nextLimit());
        assertTrue(decision.reason().contains("min"));
    }

    @Test
    void currentLimitBelowMinimumClampsNextLimitSafely() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(1, feedback("api-1", 1,
                80.0, 200.0, 280.0, 0.30, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(1, decision.previousLimit());
        assertEquals(2, decision.nextLimit());
        assertTrue(decision.reason().contains("min"));
    }

    @Test
    void currentLimitExactlyMinimumCanIncreaseWhenTelemetryIsHealthy() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(2, feedback("api-1", 1,
                40.0, 70.0, 90.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.INCREASE, decision.action());
        assertEquals(2, decision.previousLimit());
        assertEquals(5, decision.nextLimit());
        assertTrue(decision.nextLimit() >= decision.minLimit());
    }

    @Test
    void decreaseRoundingBelowMinimumClampsToMinimum() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(3, feedback("api-1", 3,
                40.0, 200.0, 250.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(2, decision.nextLimit());
        assertTrue(decision.reason().contains("min"));
    }

    @Test
    void decreaseRoundingCanLandExactlyOnMinimum() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(4, feedback("api-1", 3,
                40.0, 200.0, 250.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.DECREASE, decision.action());
        assertEquals(2, decision.nextLimit());
        assertTrue(decision.nextLimit() >= decision.minLimit());
    }

    @Test
    void decreaseFromSmallCurrentLimitNeverProducesZero() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(1, feedback("api-1", 1,
                40.0, 200.0, 250.0, 0.01, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(2, decision.nextLimit());
        assertTrue(decision.nextLimit() >= decision.minLimit());
    }

    @Test
    void combinedDecreaseNearMinimumRemainsSafe() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(2, feedback("api-1", 2,
                40.0, 200.0, 250.0, 0.50, 200));

        assertEquals(ConcurrencyLimitDecision.Action.CLAMP, decision.action());
        assertEquals(2, decision.nextLimit());
        assertTrue(decision.reason().contains("min"));
    }

    @Test
    void lowSampleSizeHoldsLimit() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(30, feedback("api-1", 4,
                40.0, 70.0, 90.0, 0.01, 3));

        assertEquals(ConcurrencyLimitDecision.Action.HOLD, decision.action());
        assertEquals(30, decision.nextLimit());
        assertTrue(decision.reason().contains("sample size"));
        assertTrue(decision.reason().contains("below minimum"));
    }

    @Test
    void lowSampleSizeHoldsEvenWhenTelemetryLooksSevere() {
        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(30, feedback("api-1", 30,
                500.0, 1_200.0, 1_800.0, 0.90, 3));

        assertEquals(ConcurrencyLimitDecision.Action.HOLD, decision.action());
        assertEquals(30, decision.nextLimit());
        assertTrue(decision.reason().contains("sample size"));
        assertTrue(decision.reason().contains("below minimum"));
    }

    @Test
    void invalidConfigIsRejected() {
        assertAll("invalid config",
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(0, 100, 1, 0.5, 100.0, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(10, 9, 1, 0.5, 100.0, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 0, 0.5, 100.0, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.0, 100.0, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 1.0, 100.0, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5, 0.0, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5, Double.NaN, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5,
                        Double.POSITIVE_INFINITY, 0.1, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5, 100.0, -0.01, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5, 100.0, 1.01, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5, 100.0, Double.NaN, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5,
                        100.0, Double.POSITIVE_INFINITY, 0)),
                () -> assertInvalid(() -> new AdaptiveConcurrencyConfig(1, 100, 1, 0.5, 100.0, 0.1, -1))
        );
    }

    @Test
    void invalidFeedbackIsRejected() {
        assertAll("invalid feedback",
                () -> assertInvalid(() -> feedback(null, 0, 1.0, 2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("   ", 0, 1.0, 2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", -1, 1.0, 2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, -1.0, 2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, -2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, -3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, Double.NaN, 2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, Double.NaN, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, Double.NaN, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, Double.POSITIVE_INFINITY, 2.0, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, Double.POSITIVE_INFINITY, 3.0, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, Double.POSITIVE_INFINITY, 0.0, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, 3.0, -0.01, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, 3.0, 1.01, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, 3.0, Double.NaN, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, 3.0, Double.POSITIVE_INFINITY, 10)),
                () -> assertInvalid(() -> feedback("api-1", 0, 1.0, 2.0, 3.0, 0.0, -1)),
                () -> assertInvalid(() -> new ConcurrencyFeedback("api-1", 0, 1.0, 2.0,
                        3.0, 0.0, 10, null))
        );
    }

    @Test
    void decisionIncludesServerLimitActionReasonTimestampAndFeedbackSignals() {
        ConcurrencyFeedback feedback = feedback("api-1", 15, 40.0, 70.0, 90.0, 0.01, 200);

        ConcurrencyLimitDecision decision = limiter.calculateNextLimit(20, feedback);

        assertEquals("api-1", decision.serverId());
        assertEquals(20, decision.previousLimit());
        assertEquals(23, decision.nextLimit());
        assertEquals(2, decision.minLimit());
        assertEquals(100, decision.maxLimit());
        assertEquals(ConcurrencyLimitDecision.Action.INCREASE, decision.action());
        assertFalse(decision.reason().isBlank());
        assertEquals(NOW, decision.timestamp());
        assertEquals(feedback.currentInFlightRequestCount(), decision.currentInFlightRequestCount());
        assertEquals(feedback.observedP95LatencyMillis(), decision.observedP95LatencyMillis());
        assertEquals(feedback.observedP99LatencyMillis(), decision.observedP99LatencyMillis());
        assertEquals(feedback.observedErrorRate(), decision.observedErrorRate());
        assertEquals(feedback.sampleSize(), decision.sampleSize());
    }

    @Test
    void behaviorIsDeterministic() {
        ConcurrencyFeedback feedback = feedback("api-1", 25, 60.0, 180.0, 240.0, 0.20, 200);

        ConcurrencyLimitDecision first = limiter.calculateNextLimit(40, feedback);
        ConcurrencyLimitDecision second = limiter.calculateNextLimit(40, feedback);

        assertEquals(first, second);
    }

    @Test
    void invalidDecisionIsRejected() {
        assertAll("invalid decision",
                () -> assertInvalid(() -> new ConcurrencyLimitDecision(null, 10, 10, 1, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 0, 10, 1, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 0, 1, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 10, 0, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 10, 100, 99,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 101, 1, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 10, 1, 100,
                        null, "reason", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 10, 1, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "   ", NOW, 0, 1.0, 2.0, 3.0, 0.0, 1)),
                () -> assertInvalid(() -> new ConcurrencyLimitDecision("api-1", 10, 10, 1, 100,
                        ConcurrencyLimitDecision.Action.HOLD, "reason", null, 0, 1.0, 2.0, 3.0, 0.0, 1))
        );
    }

    private ConcurrencyFeedback feedback(String serverId,
                                         int currentInFlightRequestCount,
                                         double observedAverageLatencyMillis,
                                         double observedP95LatencyMillis,
                                         double observedP99LatencyMillis,
                                         double observedErrorRate,
                                         int sampleSize) {
        return new ConcurrencyFeedback(serverId, currentInFlightRequestCount, observedAverageLatencyMillis,
                observedP95LatencyMillis, observedP99LatencyMillis, observedErrorRate, sampleSize, NOW);
    }

    private void assertInvalid(Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable);
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }
}
