package core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaseShadowReplayReportFormatterTest {
    private static final Instant FIRST = Instant.parse("2026-04-30T12:00:00Z");
    private static final Instant LATEST = Instant.parse("2026-04-30T12:03:00Z");

    @Test
    void formatsReplayReportWithRequiredSections() {
        LaseShadowReplayMetrics metrics = metrics();
        LaseShadowReplayReport report = new LaseShadowReplayReport("sample-shadow-events.jsonl",
                metrics, List.of("Replay input is local JSON Lines shadow data."));

        String output = new LaseShadowReplayReportFormatter().format(report);

        assertTrue(output.contains("LoadBalancerPro LASE Replay Report"));
        assertTrue(output.contains("Source: sample-shadow-events.jsonl"));
        assertTrue(output.contains("Event Totals:"));
        assertTrue(output.contains("Agreement / Fail-Safe:"));
        assertTrue(output.contains("Recommendation Counts:"));
        assertTrue(output.contains("Score Summary:"));
        assertTrue(output.contains("Network Signal Summary:"));
        assertTrue(output.contains("Time Range:"));
        assertTrue(output.contains("Warnings / Limitations:"));
        assertTrue(output.contains("Total events: 3"));
        assertTrue(output.contains("Agreement rate: 50.00%"));
        assertTrue(output.contains("Fail-safe rate: 33.33%"));
        assertTrue(output.contains("SCALE_UP: 2"));
        assertTrue(output.contains("Average timeout rate: 20.00%"));
        assertTrue(output.contains("Total request timeouts: 7"));
        assertTrue(output.contains("First event: 2026-04-30T12:00:00Z"));
        assertTrue(output.contains("Latest event: 2026-04-30T12:03:00Z"));
        assertTrue(output.contains("Replay input is local JSON Lines shadow data."));
    }

    @Test
    void formatterAvoidsRawObjectDumpsAndObviousSecretTerms() {
        LaseShadowReplayReport report = new LaseShadowReplayReport("sample-shadow-events.jsonl",
                metrics(), List.of("Sanitized replay report only."));

        String output = new LaseShadowReplayReportFormatter().format(report);
        String lower = output.toLowerCase();

        assertFalse(output.contains("LaseShadowReplayMetrics["));
        assertFalse(output.contains("LaseShadowReplayReport["));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("secret"));
        assertFalse(lower.contains("credential"));
        assertFalse(lower.contains("accesskey"));
        assertFalse(lower.contains("token"));
    }

    @Test
    void formatterRejectsNullReport() {
        assertThrows(NullPointerException.class, () -> new LaseShadowReplayReportFormatter().format(null));
    }

    @Test
    void reportValidationRejectsInvalidInput() {
        LaseShadowReplayMetrics metrics = metrics();

        assertThrows(IllegalArgumentException.class,
                () -> new LaseShadowReplayReport(" ", metrics, List.of()));
        assertThrows(NullPointerException.class,
                () -> new LaseShadowReplayReport("source.jsonl", null, List.of()));
        assertThrows(NullPointerException.class,
                () -> new LaseShadowReplayReport("source.jsonl", metrics, null));
    }

    private static LaseShadowReplayMetrics metrics() {
        return new LaseShadowReplayMetrics(
                3,
                2,
                1,
                0.5,
                1,
                1.0 / 3.0,
                Map.of("SCALE_UP", 2L, "FAIL_SAFE", 1L),
                new LaseShadowReplayScoreSummary(2, 10.0, 15.0, 20.0),
                new LaseShadowReplayScoreSummary(2, 1.0, 2.0, 3.0),
                new LaseShadowNetworkSummary(0.20, 0.10, 0.05, 25.0, 1, 7),
                FIRST,
                LATEST,
                Map.of("timeout storm [redacted]", 1L));
    }
}
