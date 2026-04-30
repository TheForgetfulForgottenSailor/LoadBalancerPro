package test.core;

import core.LoadSheddingConfig;
import core.LoadSheddingDecision;
import core.LoadSheddingPolicy;
import core.LoadSheddingSignal;
import core.RequestPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LoadSheddingPolicyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LoadSheddingConfig CONFIG =
            new LoadSheddingConfig(0.75, 0.90, 20, 250.0, 0.10, true, true);

    private final LoadSheddingPolicy policy = new LoadSheddingPolicy();

    @Test
    void priorityOrderingIsExplicit() {
        assertTrue(RequestPriority.CRITICAL.isMoreImportantThan(RequestPriority.USER));
        assertTrue(RequestPriority.USER.isMoreImportantThan(RequestPriority.BACKGROUND));
        assertTrue(RequestPriority.BACKGROUND.isMoreImportantThan(RequestPriority.PREFETCH));
        assertEquals(4, RequestPriority.CRITICAL.importanceRank());
        assertEquals(3, RequestPriority.USER.importanceRank());
        assertEquals(2, RequestPriority.BACKGROUND.importanceRank());
        assertEquals(1, RequestPriority.PREFETCH.importanceRank());
        assertNotEquals(RequestPriority.CRITICAL.ordinal(), RequestPriority.CRITICAL.importanceRank());
    }

    @Test
    void normalPressureAllowsAllPriorities() {
        LoadSheddingSignal normal = signal("cluster", 30, 100, 2, 80.0, 0.01);

        for (RequestPriority priority : RequestPriority.values()) {
            LoadSheddingDecision decision = policy.decide(priority, normal, CONFIG);

            assertEquals(LoadSheddingDecision.Action.ALLOW, decision.action(), priority.name());
            assertTrue(decision.reason().contains("normal"), priority.name());
        }
    }

    @Test
    void softUtilizationPressureShedsPrefetchFirst() {
        LoadSheddingDecision decision = policy.decide(RequestPriority.PREFETCH,
                signal("cluster", 80, 100, 2, 80.0, 0.01), CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertTrue(decision.reason().contains("soft utilization"));
        assertTrue(decision.reason().contains("PREFETCH"));
    }

    @Test
    void softPressureAllowsUserAndCriticalTraffic() {
        LoadSheddingSignal soft = signal("cluster", 80, 100, 2, 80.0, 0.01);

        LoadSheddingDecision userDecision = policy.decide(RequestPriority.USER, soft, CONFIG);
        LoadSheddingDecision criticalDecision = policy.decide(RequestPriority.CRITICAL, soft, CONFIG);

        assertEquals(LoadSheddingDecision.Action.ALLOW, userDecision.action());
        assertEquals(LoadSheddingDecision.Action.ALLOW, criticalDecision.action());
        assertTrue(userDecision.reason().contains("soft"));
        assertTrue(criticalDecision.reason().contains("soft"));
    }

    @Test
    void hardUtilizationPressureShedsLowPriorityWork() {
        LoadSheddingSignal hard = signal("cluster", 95, 100, 2, 80.0, 0.01);

        LoadSheddingDecision prefetch = policy.decide(RequestPriority.PREFETCH, hard, CONFIG);
        LoadSheddingDecision background = policy.decide(RequestPriority.BACKGROUND, hard, CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, prefetch.action());
        assertEquals(LoadSheddingDecision.Action.SHED, background.action());
        assertTrue(prefetch.reason().contains("hard utilization"));
        assertTrue(background.reason().contains("hard utilization"));
    }

    @Test
    void hardUtilizationPressureShedsUserTrafficWhenConfigured() {
        LoadSheddingDecision decision = policy.decide(RequestPriority.USER,
                signal("cluster", 95, 100, 2, 80.0, 0.01), CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertTrue(decision.reason().contains("USER"));
        assertTrue(decision.reason().contains("hard utilization"));
    }

    @Test
    void hardUtilizationAllowsUserTrafficWhenConfiguredNotToShedUser() {
        LoadSheddingConfig userProtectedConfig =
                new LoadSheddingConfig(0.75, 0.90, 20, 250.0, 0.10, true, false);

        LoadSheddingDecision decision = policy.decide(RequestPriority.USER,
                signal("cluster", 95, 100, 2, 80.0, 0.01), userProtectedConfig);

        assertEquals(LoadSheddingDecision.Action.ALLOW, decision.action());
        assertTrue(decision.reason().contains("USER"));
        assertTrue(decision.reason().contains("configured"));
    }

    @Test
    void criticalTrafficIsAllowedUnderOverloadWhenBypassIsEnabled() {
        LoadSheddingDecision decision = policy.decide(RequestPriority.CRITICAL,
                signal("cluster", 100, 100, 50, 400.0, 0.50), CONFIG);

        assertEquals(LoadSheddingDecision.Action.ALLOW, decision.action());
        assertTrue(decision.reason().contains("critical bypass"));
    }

    @Test
    void criticalTrafficIsShedUnderHardPressureWhenBypassIsDisabled() {
        LoadSheddingConfig noBypassConfig =
                new LoadSheddingConfig(0.75, 0.90, 20, 250.0, 0.10, false, true);

        LoadSheddingDecision decision = policy.decide(RequestPriority.CRITICAL,
                signal("cluster", 95, 100, 2, 80.0, 0.01), noBypassConfig);

        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertTrue(decision.reason().contains("CRITICAL"));
        assertTrue(decision.reason().contains("hard utilization"));
    }

    @Test
    void utilizationThresholdBoundariesAreExplicit() {
        LoadSheddingDecision belowSoft = policy.decide(RequestPriority.PREFETCH,
                signal("cluster", 74, 100, 2, 80.0, 0.01), CONFIG);
        LoadSheddingDecision exactSoft = policy.decide(RequestPriority.PREFETCH,
                signal("cluster", 75, 100, 2, 80.0, 0.01), CONFIG);
        LoadSheddingDecision exactHard = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 90, 100, 2, 80.0, 0.01), CONFIG);
        LoadSheddingDecision aboveHard = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 91, 100, 2, 80.0, 0.01), CONFIG);

        assertEquals(LoadSheddingDecision.Action.ALLOW, belowSoft.action());
        assertTrue(belowSoft.reason().contains("normal"));
        assertEquals(LoadSheddingDecision.Action.SHED, exactSoft.action());
        assertTrue(exactSoft.reason().contains("soft utilization"));
        assertEquals(LoadSheddingDecision.Action.SHED, exactHard.action());
        assertTrue(exactHard.reason().contains("hard utilization"));
        assertEquals(LoadSheddingDecision.Action.SHED, aboveHard.action());
        assertTrue(aboveHard.reason().contains("hard utilization"));
    }

    @Test
    void queueLatencyAndErrorThresholdBoundariesAreExplicit() {
        LoadSheddingDecision queueAtLimit = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 20, 80.0, 0.01), CONFIG);
        LoadSheddingDecision queueAboveLimit = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 21, 80.0, 0.01), CONFIG);
        LoadSheddingDecision latencyAtLimit = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 250.0, 0.01), CONFIG);
        LoadSheddingDecision latencyAboveLimit = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 250.1, 0.01), CONFIG);
        LoadSheddingDecision errorAtLimit = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 80.0, 0.10), CONFIG);
        LoadSheddingDecision errorAboveLimit = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 80.0, 0.11), CONFIG);

        assertEquals(LoadSheddingDecision.Action.ALLOW, queueAtLimit.action());
        assertEquals(LoadSheddingDecision.Action.SHED, queueAboveLimit.action());
        assertTrue(queueAboveLimit.reason().contains("queue depth"));
        assertEquals(LoadSheddingDecision.Action.ALLOW, latencyAtLimit.action());
        assertEquals(LoadSheddingDecision.Action.SHED, latencyAboveLimit.action());
        assertTrue(latencyAboveLimit.reason().contains("p95 latency"));
        assertEquals(LoadSheddingDecision.Action.ALLOW, errorAtLimit.action());
        assertEquals(LoadSheddingDecision.Action.SHED, errorAboveLimit.action());
        assertTrue(errorAboveLimit.reason().contains("error rate"));
    }

    @Test
    void priorityBehaviorUnderSoftAndHardPressureIsExplicit() {
        LoadSheddingConfig userProtectedConfig =
                new LoadSheddingConfig(0.75, 0.90, 20, 250.0, 0.10, true, false);
        LoadSheddingSignal soft = signal("cluster", 80, 100, 2, 80.0, 0.01);
        LoadSheddingSignal hard = signal("cluster", 95, 100, 2, 80.0, 0.01);

        assertEquals(LoadSheddingDecision.Action.SHED,
                policy.decide(RequestPriority.PREFETCH, soft, CONFIG).action());
        assertEquals(LoadSheddingDecision.Action.ALLOW,
                policy.decide(RequestPriority.BACKGROUND, soft, CONFIG).action());
        assertEquals(LoadSheddingDecision.Action.SHED,
                policy.decide(RequestPriority.USER, hard, CONFIG).action());
        assertEquals(LoadSheddingDecision.Action.ALLOW,
                policy.decide(RequestPriority.USER, hard, userProtectedConfig).action());
    }

    @Test
    void combinedPressureReasonsIdentifyActiveSignals() {
        LoadSheddingDecision utilizationAndQueue = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 95, 100, 25, 80.0, 0.01), CONFIG);
        LoadSheddingDecision latencyAndError = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 300.0, 0.20), CONFIG);
        LoadSheddingDecision allSignals = policy.decide(RequestPriority.USER,
                signal("cluster", 95, 100, 25, 300.0, 0.20), CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, utilizationAndQueue.action());
        assertTrue(utilizationAndQueue.reason().contains("overload"));
        assertTrue(utilizationAndQueue.reason().contains("hard utilization"));
        assertTrue(utilizationAndQueue.reason().contains("queue depth"));
        assertEquals(LoadSheddingDecision.Action.SHED, latencyAndError.action());
        assertTrue(latencyAndError.reason().contains("overload"));
        assertTrue(latencyAndError.reason().contains("p95 latency"));
        assertTrue(latencyAndError.reason().contains("error rate"));
        assertEquals(LoadSheddingDecision.Action.SHED, allSignals.action());
        assertTrue(allSignals.reason().contains("overload"));
        assertTrue(allSignals.reason().contains("hard utilization"));
        assertTrue(allSignals.reason().contains("queue depth"));
        assertTrue(allSignals.reason().contains("p95 latency"));
        assertTrue(allSignals.reason().contains("error rate"));
    }

    @Test
    void queueDepthAboveThresholdTriggersShedding() {
        LoadSheddingDecision decision = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 21, 80.0, 0.01), CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertTrue(decision.reason().contains("queue depth"));
    }

    @Test
    void p95LatencyAboveThresholdTriggersShedding() {
        LoadSheddingDecision decision = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 251.0, 0.01), CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertTrue(decision.reason().contains("p95 latency"));
    }

    @Test
    void errorRateAboveThresholdTriggersShedding() {
        LoadSheddingDecision decision = policy.decide(RequestPriority.BACKGROUND,
                signal("cluster", 20, 100, 2, 80.0, 0.11), CONFIG);

        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertTrue(decision.reason().contains("error rate"));
    }

    @Test
    void invalidConfigIsRejected() {
        assertAll("invalid config",
                () -> assertInvalid(() -> new LoadSheddingConfig(-0.01, 0.90, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(1.01, 0.90, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(Double.NaN, 0.90, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(Double.POSITIVE_INFINITY,
                        0.90, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, Double.NaN, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 1.01, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.70, 20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, Double.POSITIVE_INFINITY,
                        20, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, -1, 250.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20, 0.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20, -1.0, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20,
                        Double.NaN, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20,
                        Double.POSITIVE_INFINITY, 0.10, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20, 250.0, -0.01, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20, 250.0, 1.01, true, true)),
                () -> assertInvalid(() -> new LoadSheddingConfig(0.75, 0.90, 20,
                        250.0, Double.POSITIVE_INFINITY, true, true))
        );
    }

    @Test
    void invalidSignalIsRejected() {
        assertAll("invalid signal",
                () -> assertInvalid(() -> signal(null, 1, 100, 0, 1.0, 0.0)),
                () -> assertInvalid(() -> signal("   ", 1, 100, 0, 1.0, 0.0)),
                () -> assertInvalid(() -> signal("cluster", -1, 100, 0, 1.0, 0.0)),
                () -> assertInvalid(() -> signal("cluster", 1, 0, 0, 1.0, 0.0)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, -1, 1.0, 0.0)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, -1.0, 0.0)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, Double.NaN, 0.0)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, Double.POSITIVE_INFINITY, 0.0)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, 1.0, -0.01)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, 1.0, 1.01)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, 1.0, Double.NaN)),
                () -> assertInvalid(() -> signal("cluster", 1, 100, 0, 1.0, Double.POSITIVE_INFINITY)),
                () -> assertInvalid(() -> new LoadSheddingSignal("cluster", 1, 100, 0,
                        1.0, 0.0, null))
        );
    }

    @Test
    void decisionIncludesPriorityActionReasonTimestampAndSignalContext() {
        LoadSheddingSignal signal = signal("cluster", 80, 100, 2, 80.0, 0.01);

        LoadSheddingDecision decision = policy.decide(RequestPriority.PREFETCH, signal, CONFIG);

        assertEquals(RequestPriority.PREFETCH, decision.priority());
        assertEquals(LoadSheddingDecision.Action.SHED, decision.action());
        assertFalse(decision.reason().isBlank());
        assertEquals(NOW, decision.timestamp());
        assertEquals("cluster", decision.targetId());
        assertEquals(80, decision.currentInFlightRequestCount());
        assertEquals(100, decision.concurrencyLimit());
        assertEquals(2, decision.queueDepth());
        assertEquals(0.80, decision.utilization());
        assertEquals(80.0, decision.observedP95LatencyMillis());
        assertEquals(0.01, decision.observedErrorRate());
    }

    @Test
    void invalidDecisionIsRejected() {
        assertAll("invalid decision",
                () -> assertInvalid(() -> new LoadSheddingDecision(null, LoadSheddingDecision.Action.ALLOW,
                        "reason", NOW, "cluster", 1, 100, 0, 0.01, 1.0, 0.0)),
                () -> assertInvalid(() -> new LoadSheddingDecision(RequestPriority.USER, null,
                        "reason", NOW, "cluster", 1, 100, 0, 0.01, 1.0, 0.0)),
                () -> assertInvalid(() -> new LoadSheddingDecision(RequestPriority.USER,
                        LoadSheddingDecision.Action.ALLOW, "   ", NOW, "cluster", 1, 100, 0, 0.01, 1.0, 0.0)),
                () -> assertInvalid(() -> new LoadSheddingDecision(RequestPriority.USER,
                        LoadSheddingDecision.Action.ALLOW, "reason", null, "cluster", 1, 100, 0, 0.01, 1.0, 0.0)),
                () -> assertInvalid(() -> new LoadSheddingDecision(RequestPriority.USER,
                        LoadSheddingDecision.Action.ALLOW, "reason", NOW, "   ", 1, 100, 0, 0.01, 1.0, 0.0)),
                () -> assertInvalid(() -> new LoadSheddingDecision(RequestPriority.USER,
                        LoadSheddingDecision.Action.ALLOW, "reason", NOW, "cluster", 1, 100, 0, -0.01, 1.0, 0.0))
        );
    }

    @Test
    void behaviorIsDeterministic() {
        LoadSheddingSignal signal = signal("cluster", 95, 100, 25, 300.0, 0.20);

        LoadSheddingDecision first = policy.decide(RequestPriority.USER, signal, CONFIG);
        LoadSheddingDecision second = policy.decide(RequestPriority.USER, signal, CONFIG);

        assertEquals(first, second);
    }

    private LoadSheddingSignal signal(String targetId,
                                      int currentInFlightRequestCount,
                                      int concurrencyLimit,
                                      int queueDepth,
                                      double observedP95LatencyMillis,
                                      double observedErrorRate) {
        return new LoadSheddingSignal(targetId, currentInFlightRequestCount, concurrencyLimit,
                queueDepth, observedP95LatencyMillis, observedErrorRate, NOW);
    }

    private void assertInvalid(Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable);
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }
}
