package test.core;

import core.AutoscalingAction;
import core.AutoscalingRecommendation;
import core.AutoscalingSignal;
import core.ShadowAutoscaler;
import core.ShadowAutoscalerConfig;
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
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.80, 2, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 0, 1, 10)),
                () -> assertInvalid(() -> new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.80, 0.25, 2, 0, 10)),
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
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, Double.POSITIVE_INFINITY, 0.0, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, -0.01, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, 1.01, 100)),
                () -> assertInvalid(() -> signal("checkout", 4, 2, 10, 2, 0, 1.0, 1.0, Double.NaN, 100)),
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
}
