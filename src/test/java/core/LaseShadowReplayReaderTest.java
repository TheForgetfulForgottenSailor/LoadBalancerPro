package core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaseShadowReplayReaderTest {
    private static final Instant NOW = Instant.parse("2026-04-30T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void readsSchemaVersionedJsonLines() throws Exception {
        LaseShadowReplayReader reader = new LaseShadowReplayReader();
        Path replayFile = tempDir.resolve("shadow-events.jsonl");
        LaseShadowEvent first = event("eval-1", "S1", "S1", "HOLD", true);
        LaseShadowEvent second = event("eval-2", "S1", "S2", "SCALE_UP", false);
        Files.writeString(replayFile, reader.toJsonLine(LaseShadowReplayRecord.fromEvent(first))
                + System.lineSeparator()
                + reader.toJsonLine(LaseShadowReplayRecord.fromEvent(second))
                + System.lineSeparator());

        List<LaseShadowReplayRecord> records = reader.readAll(replayFile);

        assertEquals(2, records.size());
        assertEquals(LaseShadowReplayRecord.SCHEMA_VERSION, records.get(0).schemaVersion());
        assertEquals("eval-1", records.get(0).event().evaluationId());
        assertEquals("SCALE_UP", records.get(1).event().recommendedAction());
    }

    @Test
    void emptyFileReturnsNoRecords() throws Exception {
        Path replayFile = tempDir.resolve("empty.jsonl");
        Files.writeString(replayFile, "");

        assertTrue(new LaseShadowReplayReader().readAll(replayFile).isEmpty());
    }

    @Test
    void missingFileIsRejectedSafely() {
        Path missing = tempDir.resolve("missing.jsonl");

        LaseShadowReplayException ex = assertThrows(LaseShadowReplayException.class,
                () -> new LaseShadowReplayReader().readAll(missing));

        assertTrue(ex.getMessage().contains("Replay file not found"));
        assertTrue(ex.getMessage().contains("missing.jsonl"));
    }

    @Test
    void malformedJsonIsRejectedWithLineNumberButWithoutRawContent() throws Exception {
        Path replayFile = tempDir.resolve("bad.jsonl");
        Files.writeString(replayFile, "{not-json SECRET_VALUE}");

        LaseShadowReplayException ex = assertThrows(LaseShadowReplayException.class,
                () -> new LaseShadowReplayReader().readAll(replayFile));

        assertTrue(ex.getMessage().contains("line 1"));
        assertFalse(ex.getMessage().contains("SECRET_VALUE"));
        assertFalse(ex.getMessage().contains("{not-json"));
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws Exception {
        Path replayFile = tempDir.resolve("bad-schema.jsonl");
        LaseShadowReplayReader reader = new LaseShadowReplayReader();
        String line = reader.toJsonLine(LaseShadowReplayRecord.fromEvent(
                event("eval-1", "S1", "S1", "HOLD", true))).replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        Files.writeString(replayFile, line);

        LaseShadowReplayException ex = assertThrows(LaseShadowReplayException.class,
                () -> reader.readAll(replayFile));

        assertTrue(ex.getMessage().contains("Unsupported replay schema version"));
        assertTrue(ex.getMessage().contains("line 1"));
    }

    @Test
    void missingRequiredEventFieldsAreRejected() throws Exception {
        Path replayFile = tempDir.resolve("missing-field.jsonl");
        Files.writeString(replayFile, """
                {"schemaVersion":1,"event":{"timestamp":"2026-04-30T12:00:00Z","strategy":"CAPACITY_AWARE","requestedLoad":10.0,"unallocatedLoad":0.0,"recommendedAction":"HOLD","decisionScore":1.0,"networkAwarenessSignal":{"targetId":"S1","timeoutRate":0.0,"retryRate":0.0,"connectionFailureRate":0.0,"latencyJitterMillis":0.0,"recentErrorBurst":false,"requestTimeoutCount":0,"sampleSize":0,"timestamp":"2026-04-30T12:00:00Z"},"networkRiskScore":0.0,"reason":"ok","agreedWithRouting":true,"failSafe":false,"failureReason":null}}
                """);

        LaseShadowReplayException ex = assertThrows(LaseShadowReplayException.class,
                () -> new LaseShadowReplayReader().readAll(replayFile));

        assertTrue(ex.getMessage().contains("line 1"));
        assertFalse(ex.getMessage().contains("CAPACITY_AWARE"));
    }

    @Test
    void overlongLinesAreRejectedBeforeParsing() throws Exception {
        Path replayFile = tempDir.resolve("huge-line.jsonl");
        Files.writeString(replayFile, " ".repeat(LaseShadowReplayReader.DEFAULT_MAX_LINE_LENGTH + 1));

        LaseShadowReplayException ex = assertThrows(LaseShadowReplayException.class,
                () -> new LaseShadowReplayReader().readAll(replayFile));

        assertTrue(ex.getMessage().contains("line 1"));
        assertTrue(ex.getMessage().contains("maximum replay line length"));
    }

    private static LaseShadowEvent event(String evaluationId,
                                         String actualServer,
                                         String recommendedServer,
                                         String action,
                                         Boolean agreed) {
        return new LaseShadowEvent(
                evaluationId,
                NOW,
                "CAPACITY_AWARE",
                50.0,
                0.0,
                actualServer,
                recommendedServer,
                action,
                42.0,
                new NetworkAwarenessSignal(recommendedServer, 0.10, 0.20, 0.05,
                        12.5, true, 2, 100, NOW),
                123.0,
                "Evaluation completed",
                agreed,
                false,
                null);
    }
}
