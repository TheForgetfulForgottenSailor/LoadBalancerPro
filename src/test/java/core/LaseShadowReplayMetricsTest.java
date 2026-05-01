package core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaseShadowReplayMetricsTest {
    private static final Instant FIRST = Instant.parse("2026-04-30T12:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-04-30T12:01:00Z");
    private static final Instant THIRD = Instant.parse("2026-04-30T12:02:00Z");

    @TempDir
    Path tempDir;

    @Test
    void calculatesReplayAggregatesFromShadowEvents() {
        LaseShadowReplayMetrics metrics = new LaseShadowReplayEngine().evaluate(List.of(
                LaseShadowReplayRecord.fromEvent(event("eval-1", FIRST, "HOLD", 10.0, 1.0, true,
                        false, null, signal("S1", 0.10, 0.20, 0.05, 10.0, true, 2))),
                LaseShadowReplayRecord.fromEvent(event("eval-2", SECOND, "SCALE_UP", 20.0, 3.0, false,
                        false, null, signal("S2", 0.20, 0.10, 0.15, 25.0, false, 4))),
                LaseShadowReplayRecord.fromEvent(event("eval-3", THIRD, "FAIL_SAFE", null, null, null,
                        true, "timeout storm\napi-key-value", signal("S3", 0.30, 0.30, 0.10, 5.0, true, 1)))
        ));

        assertEquals(3, metrics.totalEvents());
        assertEquals(2, metrics.comparableEvents());
        assertEquals(1, metrics.agreementCount());
        assertEquals(0.5, metrics.agreementRate(), 0.001);
        assertEquals(1, metrics.failSafeCount());
        assertEquals(1.0 / 3.0, metrics.failSafeRate(), 0.001);
        assertEquals(Map.of("HOLD", 1L, "SCALE_UP", 1L, "FAIL_SAFE", 1L), metrics.recommendationCounts());
        assertEquals(FIRST, metrics.firstEventTimestamp());
        assertEquals(THIRD, metrics.latestEventTimestamp());

        assertEquals(2, metrics.decisionScoreSummary().count());
        assertEquals(10.0, metrics.decisionScoreSummary().min(), 0.001);
        assertEquals(15.0, metrics.decisionScoreSummary().average(), 0.001);
        assertEquals(20.0, metrics.decisionScoreSummary().max(), 0.001);

        assertEquals(2, metrics.networkRiskSummary().count());
        assertEquals(1.0, metrics.networkRiskSummary().min(), 0.001);
        assertEquals(2.0, metrics.networkRiskSummary().average(), 0.001);
        assertEquals(3.0, metrics.networkRiskSummary().max(), 0.001);

        assertEquals(0.20, metrics.networkSummary().averageTimeoutRate(), 0.001);
        assertEquals(0.20, metrics.networkSummary().averageRetryRate(), 0.001);
        assertEquals(0.10, metrics.networkSummary().averageConnectionFailureRate(), 0.001);
        assertEquals(25.0, metrics.networkSummary().maxLatencyJitterMillis(), 0.001);
        assertEquals(2, metrics.networkSummary().recentErrorBurstCount());
        assertEquals(7, metrics.networkSummary().totalRequestTimeoutCount());

        assertEquals(1, metrics.failureReasonCounts().values().stream().mapToLong(Long::longValue).sum());
        String sanitizedReason = metrics.failureReasonCounts().keySet().iterator().next();
        assertFalse(sanitizedReason.contains("\n"));
        assertFalse(sanitizedReason.contains("\t"));
        assertFalse(sanitizedReason.contains("api-key-value"));
    }

    @Test
    void emptyReplayProducesZeroMetrics() {
        LaseShadowReplayMetrics metrics = new LaseShadowReplayEngine().evaluate(List.of());

        assertEquals(0, metrics.totalEvents());
        assertEquals(0.0, metrics.agreementRate(), 0.001);
        assertEquals(0.0, metrics.failSafeRate(), 0.001);
        assertEquals(LaseShadowReplayScoreSummary.empty(), metrics.decisionScoreSummary());
        assertEquals(LaseShadowReplayScoreSummary.empty(), metrics.networkRiskSummary());
        assertEquals(LaseShadowNetworkSummary.empty(), metrics.networkSummary());
        assertEquals(Map.of(), metrics.recommendationCounts());
        assertEquals(Map.of(), metrics.failureReasonCounts());
    }

    @Test
    void evaluatesReplayFileThroughStreamingReader() throws Exception {
        LaseShadowReplayReader reader = new LaseShadowReplayReader();
        Path replayFile = tempDir.resolve("shadow-events.jsonl");
        Files.writeString(replayFile, reader.toJsonLine(LaseShadowReplayRecord.fromEvent(
                event("eval-1", FIRST, "HOLD", 10.0, 1.0, true, false, null,
                        signal("S1", 0.10, 0.0, 0.0, 2.0, false, 1))))
                + System.lineSeparator()
                + reader.toJsonLine(LaseShadowReplayRecord.fromEvent(
                event("eval-2", SECOND, "SCALE_UP", 20.0, 3.0, false, false, null,
                        signal("S2", 0.20, 0.0, 0.0, 4.0, false, 2)))));

        LaseShadowReplayMetrics metrics = new LaseShadowReplayEngine().evaluate(replayFile, reader);

        assertEquals(2, metrics.totalEvents());
        assertEquals(2, metrics.comparableEvents());
        assertEquals(1, metrics.agreementCount());
        assertEquals(0.5, metrics.agreementRate(), 0.001);
        assertEquals(0.15, metrics.networkSummary().averageTimeoutRate(), 0.001);
    }

    @Test
    void replayMetricsAreReadOnly() {
        LaseShadowReplayMetrics metrics = new LaseShadowReplayEngine().evaluate(List.of(
                LaseShadowReplayRecord.fromEvent(event("eval-1", FIRST, "HOLD", 10.0, 1.0,
                        true, false, null, signal("S1", 0.0, 0.0, 0.0, 0.0, false, 0)))
        ));

        assertThrows(UnsupportedOperationException.class,
                () -> metrics.recommendationCounts().put("SCALE_UP", 1L));
        assertThrows(UnsupportedOperationException.class,
                () -> metrics.failureReasonCounts().put("reason", 1L));
    }

    @Test
    void rejectsNullRecords() {
        List<LaseShadowReplayRecord> records = new ArrayList<>();
        records.add(null);

        assertThrows(NullPointerException.class,
                () -> new LaseShadowReplayEngine().evaluate(records));
    }

    private static LaseShadowEvent event(String evaluationId,
                                         Instant timestamp,
                                         String action,
                                         Double decisionScore,
                                         Double networkRiskScore,
                                         Boolean agreed,
                                         boolean failSafe,
                                         String failureReason,
                                         NetworkAwarenessSignal signal) {
        return new LaseShadowEvent(
                evaluationId,
                timestamp,
                "CAPACITY_AWARE",
                50.0,
                5.0,
                "S1",
                agreed == null ? null : "S1",
                action,
                decisionScore,
                signal,
                networkRiskScore,
                "Evaluation completed",
                agreed,
                failSafe,
                failureReason);
    }

    private static NetworkAwarenessSignal signal(String targetId,
                                                 double timeoutRate,
                                                 double retryRate,
                                                 double connectionFailureRate,
                                                 double jitter,
                                                 boolean burst,
                                                 int timeouts) {
        return new NetworkAwarenessSignal(targetId, timeoutRate, retryRate, connectionFailureRate,
                jitter, burst, timeouts, 100, FIRST);
    }
}
