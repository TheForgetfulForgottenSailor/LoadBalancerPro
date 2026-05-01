package core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaseShadowEventLogTest {
    private static final Instant FIRST = Instant.parse("2026-04-30T12:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-04-30T12:01:00Z");
    private static final Instant THIRD = Instant.parse("2026-04-30T12:02:00Z");

    @Test
    void recordsEventsAndCalculatesAggregates() {
        LaseShadowEventLog log = new LaseShadowEventLog(5);

        log.record(successEvent("eval-1", FIRST, "S1", "S1", "SCALE_UP", true));
        log.record(successEvent("eval-2", SECOND, "S1", "S2", "HOLD", false));

        LaseShadowObservabilitySnapshot snapshot = log.snapshot();

        assertEquals(2, snapshot.summary().totalEvaluations());
        assertEquals(2, snapshot.summary().comparableEvaluations());
        assertEquals(1, snapshot.summary().agreementCount());
        assertEquals(0.5, snapshot.summary().agreementRate(), 0.001);
        assertEquals(0, snapshot.summary().failSafeCount());
        assertEquals(SECOND, snapshot.summary().latestEventTimestamp());
        assertEquals(Map.of("SCALE_UP", 1L, "HOLD", 1L), snapshot.summary().recommendationCounts());
        assertEquals(2, snapshot.recentEvents().size());
    }

    @Test
    void boundedLogKeepsMostRecentEventsWithoutLosingAggregateCounts() {
        LaseShadowEventLog log = new LaseShadowEventLog(2);

        log.record(successEvent("eval-1", FIRST, "S1", "S1", "HOLD", true));
        log.record(successEvent("eval-2", SECOND, "S1", "S2", "SCALE_UP", false));
        log.record(successEvent("eval-3", THIRD, "S2", "S2", "INVESTIGATE", true));

        LaseShadowObservabilitySnapshot snapshot = log.snapshot();

        assertEquals(3, snapshot.summary().totalEvaluations());
        assertEquals(List.of("eval-2", "eval-3"),
                snapshot.recentEvents().stream().map(LaseShadowEvent::evaluationId).toList());
        assertEquals(Map.of("HOLD", 1L, "SCALE_UP", 1L, "INVESTIGATE", 1L),
                snapshot.summary().recommendationCounts());
    }

    @Test
    void recordsFailSafeEventsSeparatelyFromComparableRoutingEvents() {
        LaseShadowEventLog log = new LaseShadowEventLog(5);

        log.record(new LaseShadowEvent(
                "eval-failure",
                FIRST,
                "CAPACITY_AWARE",
                50.0,
                5.0,
                "S1",
                null,
                "FAIL_SAFE",
                null,
                "LASE shadow evaluation failed safely",
                null,
                true,
                "synthetic failure"));

        LaseShadowObservabilitySnapshot snapshot = log.snapshot();

        assertEquals(1, snapshot.summary().totalEvaluations());
        assertEquals(0, snapshot.summary().comparableEvaluations());
        assertEquals(0.0, snapshot.summary().agreementRate(), 0.001);
        assertEquals(1, snapshot.summary().failSafeCount());
        assertTrue(snapshot.recentEvents().get(0).failSafe());
        assertEquals("FAIL_SAFE", snapshot.recentEvents().get(0).recommendedAction());
    }

    @Test
    void snapshotCollectionsAreReadOnly() {
        LaseShadowEventLog log = new LaseShadowEventLog(5);
        log.record(successEvent("eval-1", FIRST, "S1", "S1", "HOLD", true));

        LaseShadowObservabilitySnapshot snapshot = log.snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.recentEvents().add(successEvent("eval-2", SECOND, "S1", "S1", "HOLD", true)));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.summary().recommendationCounts().put("SCALE_UP", 1L));
    }

    @Test
    void rejectsInvalidMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new LaseShadowEventLog(0));
    }

    private static LaseShadowEvent successEvent(String evaluationId,
                                                Instant timestamp,
                                                String actualServer,
                                                String recommendedServer,
                                                String action,
                                                boolean agreed) {
        return new LaseShadowEvent(
                evaluationId,
                timestamp,
                "CAPACITY_AWARE",
                50.0,
                0.0,
                actualServer,
                recommendedServer,
                action,
                42.0,
                "Evaluation completed",
                agreed,
                false,
                null);
    }
}
