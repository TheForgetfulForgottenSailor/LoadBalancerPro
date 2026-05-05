package com.richmond423.loadbalancerpro.core;

import com.richmond423.loadbalancerpro.core.AutoscalingAction;
import com.richmond423.loadbalancerpro.core.AutoscalingRecommendation;
import com.richmond423.loadbalancerpro.core.AutoscalingSignal;
import com.richmond423.loadbalancerpro.core.ShadowAutoscaler;
import com.richmond423.loadbalancerpro.core.ShadowAutoscalerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ShadowAutoscalerTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final ShadowAutoscalerConfig CONFIG =
            new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 2, 1, 10);

    private final ShadowAutoscaler autoscaler = new ShadowAutoscaler();

    @Test
    void lowSampleSizeReturnsHold() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 8, 25, 300.0, 450.0, 0.25, 9), CONFIG);

        assertEquals(AutoscalingAction.HOLD, recommendation.action());
        assertEquals(4, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("insufficient sample"));
    }

    @Test
    void highQueueDepthRecommendsScaleUp() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 8, 21, 100.0, 150.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertEquals(6, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("queue depth"));
    }

    @Test
    void highP95LatencyRecommendsScaleUp() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 8, 2, 201.0, 150.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertEquals(6, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("p95 latency"));
    }

    @Test
    void highP99LatencyRecommendsScaleUp() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 8, 2, 100.0, 351.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertEquals(6, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("p99 latency"));
    }

    @Test
    void highUtilizationRecommendsScaleUp() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 4, 2, 100.0, 150.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertEquals(6, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("utilization"));
    }

    @Test
    void highErrorRateAloneRecommendsInvestigate() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 1, 2, 100.0, 150.0, 0.20, 100), CONFIG);

        assertEquals(AutoscalingAction.INVESTIGATE, recommendation.action());
        assertEquals(4, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("error rate"));
        assertTrue(recommendation.reason().contains("without scaling pressure"));
    }

    @Test
    void lowUtilizationWithHealthySignalsRecommendsScaleDown() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 5, 2, 10, 1, 0, 80.0, 120.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.SCALE_DOWN, recommendation.action());
        assertEquals(4, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("low utilization"));
    }

    @Test
    void maxCapacityWithScaleUpPressureDoesNotExceedMax() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 10, 2, 10, 10, 30, 260.0, 450.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.HOLD, recommendation.action());
        assertEquals(10, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("at max capacity"));
    }

    @Test
    void minCapacityWithScaleDownPressureDoesNotGoBelowMin() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 2, 2, 10, 0, 0, 80.0, 120.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.HOLD, recommendation.action());
        assertEquals(2, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("at min capacity"));
    }

    @Test
    void normalHealthyStateReturnsHold() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 2, 2, 100.0, 150.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.HOLD, recommendation.action());
        assertEquals(4, recommendation.recommendedCapacity());
        assertTrue(recommendation.reason().contains("healthy"));
    }

    @Test
    void thresholdBoundariesAreExplicit() {
        assertHoldAtCurrent(signal("checkout", 4, 2, 10, 2, 20, 100.0, 150.0, 0.01, 100));
        assertScaleUp(signal("checkout", 4, 2, 10, 2, 21, 100.0, 150.0, 0.01, 100), "queue depth");

        assertHoldAtCurrent(signal("checkout", 4, 2, 10, 2, 2, 200.0, 150.0, 0.01, 100));
        assertScaleUp(signal("checkout", 4, 2, 10, 2, 2, 200.1, 150.0, 0.01, 100), "p95 latency");

        assertHoldAtCurrent(signal("checkout", 4, 2, 10, 2, 2, 100.0, 350.0, 0.01, 100));
        assertScaleUp(signal("checkout", 4, 2, 10, 2, 2, 100.0, 350.1, 0.01, 100), "p99 latency");

        assertHoldAtCurrent(signal("checkout", 4, 2, 10, 2, 2, 100.0, 150.0, 0.10, 100));
        AutoscalingRecommendation errorAboveThreshold = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 2, 2, 100.0, 150.0, 0.11, 100), CONFIG);
        assertEquals(AutoscalingAction.INVESTIGATE, errorAboveThreshold.action());
        assertTrue(errorAboveThreshold.reason().contains("error rate"));

        assertScaleUp(signal("checkout", 5, 2, 10, 4, 2, 100.0, 150.0, 0.01, 100), "utilization");
        assertScaleUp(signal("checkout", 4, 2, 10, 4, 2, 100.0, 150.0, 0.01, 100), "utilization");

        assertScaleDown(signal("checkout", 4, 2, 10, 1, 0, 100.0, 150.0, 0.01, 100));
        assertScaleDown(signal("checkout", 8, 2, 10, 1, 0, 100.0, 150.0, 0.01, 100));
    }

    @Test
    void capacityBoundariesClampRecommendations() {
        ShadowAutoscalerConfig largeSteps =
                new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 5, 5, 10);

        AutoscalingRecommendation scaleUpNearMax = autoscaler.recommend(
                signal("checkout", 9, 2, 10, 9, 25, 100.0, 150.0, 0.01, 100), largeSteps);
        assertEquals(AutoscalingAction.SCALE_UP, scaleUpNearMax.action());
        assertEquals(10, scaleUpNearMax.recommendedCapacity());

        AutoscalingRecommendation scaleUpAtMax = autoscaler.recommend(
                signal("checkout", 10, 2, 10, 10, 25, 100.0, 150.0, 0.01, 100), largeSteps);
        assertEquals(AutoscalingAction.HOLD, scaleUpAtMax.action());
        assertEquals(10, scaleUpAtMax.recommendedCapacity());
        assertTrue(scaleUpAtMax.reason().contains("at max capacity"));

        AutoscalingRecommendation scaleDownNearMin = autoscaler.recommend(
                signal("checkout", 3, 2, 10, 0, 0, 100.0, 150.0, 0.01, 100), largeSteps);
        assertEquals(AutoscalingAction.SCALE_DOWN, scaleDownNearMin.action());
        assertEquals(2, scaleDownNearMin.recommendedCapacity());

        AutoscalingRecommendation scaleDownAtMin = autoscaler.recommend(
                signal("checkout", 2, 2, 10, 0, 0, 100.0, 150.0, 0.01, 100), largeSteps);
        assertEquals(AutoscalingAction.HOLD, scaleDownAtMin.action());
        assertEquals(2, scaleDownAtMin.recommendedCapacity());
        assertTrue(scaleDownAtMin.reason().contains("at min capacity"));
    }

    @Test
    void conflictingPressureIsResolvedSafelyAndExplained() {
        AutoscalingRecommendation highErrorAndQueue = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 2, 25, 100.0, 150.0, 0.25, 100), CONFIG);
        assertEquals(AutoscalingAction.SCALE_UP, highErrorAndQueue.action());
        assertTrue(highErrorAndQueue.reason().contains("queue depth"));
        assertTrue(highErrorAndQueue.reason().contains("error rate"));

        AutoscalingRecommendation highErrorAndLatency = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 1, 0, 260.0, 450.0, 0.25, 100), CONFIG);
        assertEquals(AutoscalingAction.SCALE_UP, highErrorAndLatency.action());
        assertTrue(highErrorAndLatency.reason().contains("p95 latency"));
        assertTrue(highErrorAndLatency.reason().contains("p99 latency"));
        assertTrue(highErrorAndLatency.reason().contains("error rate"));

        AutoscalingRecommendation lowUtilizationHighLatency = autoscaler.recommend(
                signal("checkout", 8, 2, 10, 1, 0, 260.0, 150.0, 0.01, 100), CONFIG);
        assertEquals(AutoscalingAction.SCALE_UP, lowUtilizationHighLatency.action());
        assertNotEquals(AutoscalingAction.SCALE_DOWN, lowUtilizationHighLatency.action());

        AutoscalingRecommendation lowUtilizationHighError = autoscaler.recommend(
                signal("checkout", 8, 2, 10, 1, 0, 100.0, 150.0, 0.25, 100), CONFIG);
        assertEquals(AutoscalingAction.INVESTIGATE, lowUtilizationHighError.action());
        assertNotEquals(AutoscalingAction.SCALE_DOWN, lowUtilizationHighError.action());

        AutoscalingRecommendation lowUtilizationWithQueue = autoscaler.recommend(
                signal("checkout", 8, 2, 10, 1, 5, 100.0, 150.0, 0.01, 100), CONFIG);
        assertEquals(AutoscalingAction.HOLD, lowUtilizationWithQueue.action());
        assertNotEquals(AutoscalingAction.SCALE_DOWN, lowUtilizationWithQueue.action());
        assertTrue(lowUtilizationWithQueue.reason().contains("queue"));
    }

    @Test
    void combinedLatencyAndQueuePressureProduceClearScaleUpReason() {
        AutoscalingRecommendation recommendation = autoscaler.recommend(
                signal("checkout", 4, 2, 10, 4, 30, 260.0, 450.0, 0.01, 100), CONFIG);

        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertTrue(recommendation.reason().contains("scale-up pressure"));
        assertTrue(recommendation.reason().contains("queue depth"));
        assertTrue(recommendation.reason().contains("p95 latency"));
        assertTrue(recommendation.reason().contains("p99 latency"));
    }

    @Test
    void invalidConfigIsRejected() {
        assertAll("invalid config",
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(0.0, 350.0, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(-1.0, 350.0, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(Double.NaN, 350.0, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 0.0, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, Double.POSITIVE_INFINITY, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, -0.01, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 1.01, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, Double.NaN, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, -1, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, -0.01, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 1.01, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, Double.NaN, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, Double.POSITIVE_INFINITY, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, Double.NEGATIVE_INFINITY, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.80, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(Double.POSITIVE_INFINITY, 350.0, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, Double.NaN, 0.10, 20, 0.80, 0.25, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 0, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, -1, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 2, 0, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 2, -1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 2, 1, -1))
        );
    }

    @Test
    void invalidSignalIsRejected() {
        assertAll("invalid signal",
                () -> assertInvalid(() -> signal(null, 4, 2, 10, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("   ", 4, 2, 10, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 0, 2, 10, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 0, 10, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 1, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 1, 2, 10, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 11, 2, 10, 2, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, -1, 0, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, -1, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, -1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, Double.NaN, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, Double.POSITIVE_INFINITY, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, Double.NaN, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, Double.POSITIVE_INFINITY, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, -0.01, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, 1.01, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, Double.NaN, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, Double.POSITIVE_INFINITY, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, 0.0, -1)),
                () -> assertInvalid(() -> new AutoscalingSignal("checkout", 4, 2, 10, 2, 0,
                        1.0, 1.0, 0.0, 100, null))
        );
    }

    @Test
    void invalidRecommendationIsRejected() {
        assertAll("invalid recommendation",
                () -> assertInvalid(() -> new AutoscalingRecommendation(null, AutoscalingAction.HOLD, 4, 4,
                        2, 10, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("   ", AutoscalingAction.HOLD, 4, 4,
                        2, 10, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", null, 4, 4,
                        2, 10, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 0, 4,
                        2, 10, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 1,
                        2, 10, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 11,
                        2, 10, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 1, 0.0, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, Double.NaN, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, Double.POSITIVE_INFINITY, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, -0.01, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, 1.01, "reason", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, 0.0, "   ", NOW, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, 0.0, "reason", null, 2, 0, 0.50, 1.0, 1.0, 0.0, 100)),
                () -> assertInvalid(() -> new AutoscalingRecommendation("checkout", AutoscalingAction.HOLD, 4, 4,
                        2, 10, 0.0, "reason", NOW, 2, 0, -0.01, 1.0, 1.0, 0.0, 100))
        );
    }

    @Test
    void recommendationIncludesSignalContextAndDeterministicSeverity() {
        AutoscalingSignal signal = signal("checkout", 4, 2, 10, 4, 30, 260.0, 450.0, 0.01, 100);

        AutoscalingRecommendation recommendation = autoscaler.recommend(signal, CONFIG);

        assertEquals("checkout", recommendation.targetId());
        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertEquals(4, recommendation.currentCapacity());
        assertEquals(6, recommendation.recommendedCapacity());
        assertEquals(2, recommendation.minCapacity());
        assertEquals(10, recommendation.maxCapacity());
        assertTrue(recommendation.severityScore() > 0.0);
        assertFalse(recommendation.reason().isBlank());
        assertEquals(NOW, recommendation.timestamp());
        assertEquals(4, recommendation.currentInFlightRequestCount());
        assertEquals(30, recommendation.queueDepth());
        assertEquals(1.0, recommendation.utilization());
        assertEquals(260.0, recommendation.observedP95LatencyMillis());
        assertEquals(450.0, recommendation.observedP99LatencyMillis());
        assertEquals(0.01, recommendation.observedErrorRate());
        assertEquals(100, recommendation.sampleSize());
    }

    @Test
    void behaviorIsDeterministic() {
        AutoscalingSignal signal = signal("checkout", 4, 2, 10, 4, 30, 260.0, 450.0, 0.01, 100);

        AutoscalingRecommendation first = autoscaler.recommend(signal, CONFIG);
        AutoscalingRecommendation second = autoscaler.recommend(signal, CONFIG);

        assertEquals(first, second);
    }

    private AutoscalingSignal signal(String targetId,
                                     int currentCapacity,
                                     int minCapacity,
                                     int maxCapacity,
                                     int currentInFlightRequestCount,
                                     int queueDepth,
                                     double observedP95LatencyMillis,
                                     double observedP99LatencyMillis,
                                     double observedErrorRate,
                                     int sampleSize) {
        return new AutoscalingSignal(targetId, currentCapacity, minCapacity, maxCapacity,
                currentInFlightRequestCount, queueDepth, observedP95LatencyMillis,
                observedP99LatencyMillis, observedErrorRate, sampleSize, NOW);
    }

    private void assertInvalid(Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable);
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }

    private void assertHoldAtCurrent(AutoscalingSignal signal) {
        AutoscalingRecommendation recommendation = autoscaler.recommend(signal, CONFIG);

        assertEquals(AutoscalingAction.HOLD, recommendation.action());
        assertEquals(signal.currentCapacity(), recommendation.recommendedCapacity());
    }

    private void assertScaleUp(AutoscalingSignal signal, String expectedReasonFragment) {
        AutoscalingRecommendation recommendation = autoscaler.recommend(signal, CONFIG);

        assertEquals(AutoscalingAction.SCALE_UP, recommendation.action());
        assertTrue(recommendation.recommendedCapacity() > signal.currentCapacity());
        assertTrue(recommendation.reason().contains(expectedReasonFragment));
    }

    private void assertScaleDown(AutoscalingSignal signal) {
        AutoscalingRecommendation recommendation = autoscaler.recommend(signal, CONFIG);

        assertEquals(AutoscalingAction.SCALE_DOWN, recommendation.action());
        assertTrue(recommendation.recommendedCapacity() < signal.currentCapacity());
        assertTrue(recommendation.reason().contains("low utilization"));
    }
}
