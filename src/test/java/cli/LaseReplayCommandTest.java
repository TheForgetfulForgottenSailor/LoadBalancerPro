package cli;

import core.CloudConfig;
import core.CloudManager;
import core.LaseShadowEvent;
import core.LaseShadowReplayReader;
import core.LaseShadowReplayRecord;
import core.NetworkAwarenessSignal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaseReplayCommandTest {
    private static final Instant NOW = Instant.parse("2026-04-30T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void validReplayFilePrintsReportAndExitsCleanly() throws Exception {
        Path replayFile = writeReplayFile("shadow-events.jsonl");

        CapturedRun run = runReplay("--lase-replay=" + replayFile);

        assertEquals(0, run.result().exitCode());
        assertTrue(run.output().contains("LoadBalancerPro LASE Replay Report"));
        assertTrue(run.output().contains("offline/read-only replay"));
        assertTrue(run.output().contains("No routing mutation"));
        assertTrue(run.output().contains("No CloudManager calls"));
        assertTrue(run.output().contains("Total events: 2"));
        assertTrue(run.output().contains("Agreement rate: 50.00%"));
        assertTrue(run.output().contains("Network Signal Summary:"));
        assertTrue(run.error().isBlank());
    }

    @Test
    void replayModeDoesNotConstructCloudManager() throws Exception {
        Path replayFile = writeReplayFile("shadow-events.jsonl");

        try (MockedConstruction<CloudManager> mockedCloudManager =
                Mockito.mockConstruction(CloudManager.class)) {
            CapturedRun run = runReplay("--lase-replay=" + replayFile);

            assertEquals(0, run.result().exitCode());
            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Replay mode must not construct CloudManager or enter live cloud paths.");
        }
    }

    @Test
    void replayDoesNotRequireAwsCredentialsOrMutateSystemProperties() throws Exception {
        Path replayFile = writeReplayFile("shadow-events.jsonl");
        Map<String, String> previousProperties = replaceSystemProperties(Map.of(
                "aws.accessKeyId", "mock_access_key",
                "aws.secretAccessKey", "your-secret-key",
                "aws.region", "us-east-1",
                CloudConfig.LIVE_MODE_PROPERTY, "true",
                "spring.profiles.active", "cloud-sandbox"
        ));

        try {
            CapturedRun run = runReplay("--lase-replay=" + replayFile);

            assertEquals(0, run.result().exitCode());
            assertTrue(run.error().isBlank());
            assertEquals("mock_access_key", System.getProperty("aws.accessKeyId"));
            assertEquals("your-secret-key", System.getProperty("aws.secretAccessKey"));
            assertEquals("true", System.getProperty(CloudConfig.LIVE_MODE_PROPERTY));
            assertEquals("cloud-sandbox", System.getProperty("spring.profiles.active"));
        } finally {
            restoreSystemProperties(previousProperties);
        }
    }

    @Test
    void emptyReplayFilePrintsZeroEventReport() throws Exception {
        Path replayFile = tempDir.resolve("empty-shadow-events.jsonl");
        Files.writeString(replayFile, "");

        CapturedRun run = runReplay("--lase-replay=" + replayFile);

        assertEquals(0, run.result().exitCode());
        assertTrue(run.output().contains("Total events: 0"));
        assertTrue(run.output().contains("Comparable events: 0"));
        assertTrue(run.output().contains("First event: none"));
        assertTrue(run.output().contains("Latest event: none"));
        assertTrue(run.error().isBlank());
    }

    @Test
    void identicalReplayInputProducesDeterministicOutput() throws Exception {
        Path replayFile = writeReplayFile("shadow-events.jsonl");

        CapturedRun first = runReplay("--lase-replay=" + replayFile);
        CapturedRun second = runReplay("--lase-replay=" + replayFile);

        assertEquals(0, first.result().exitCode());
        assertEquals(first.output(), second.output());
        assertEquals(first.error(), second.error());
    }

    @Test
    void replayScaleUpRecommendationsRemainAdvisoryOnly() throws Exception {
        Path replayFile = writeReplayFile("shadow-events.jsonl");

        try (MockedConstruction<CloudManager> mockedCloudManager =
                Mockito.mockConstruction(CloudManager.class)) {
            CapturedRun run = runReplay("--lase-replay=" + replayFile);

            assertEquals(0, run.result().exitCode());
            assertTrue(run.output().contains("SCALE_UP: 1"));
            assertTrue(run.output().contains("No routing mutation"));
            assertTrue(run.output().contains("No CloudManager calls"));
            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Replay must summarize LASE recommendations without promoting them to cloud mutation.");
        }
    }

    @Test
    void missingReplayFileFailsSafely() {
        Path missing = tempDir.resolve("missing.jsonl");

        CapturedRun run = runReplay("--lase-replay=" + missing);

        assertEquals(2, run.result().exitCode());
        assertTrue(run.output().isBlank());
        assertTrue(run.error().contains("LASE replay failed safely"));
        assertTrue(run.error().contains("Replay file not found"));
        assertFalse(run.error().contains("\tat "));
    }

    @Test
    void malformedReplayFileFailsSafelyWithoutRawContent() throws Exception {
        Path replayFile = tempDir.resolve("bad.jsonl");
        Files.writeString(replayFile, "{not-json SECRET_VALUE}");

        CapturedRun run = runReplay("--lase-replay=" + replayFile);

        assertEquals(2, run.result().exitCode());
        assertTrue(run.error().contains("line 1"));
        assertFalse(run.error().contains("SECRET_VALUE"));
        assertFalse(run.error().contains("\tat "));
    }

    @Test
    void missingReplayPathFailsWithUsage() {
        CapturedRun run = runReplay("--lase-replay=");

        assertEquals(2, run.result().exitCode());
        assertTrue(run.error().contains("Usage: --lase-replay=<path-to-shadow-events.jsonl>"));
        assertFalse(run.error().contains("\tat "));
    }

    @Test
    void requestDetectionOnlyMatchesReplayFlag() {
        assertTrue(LaseReplayCommand.isRequested(new String[]{"--lase-replay=events.jsonl"}));
        assertFalse(LaseReplayCommand.isRequested(new String[]{"--lase-demo=healthy"}));
        assertFalse(LaseReplayCommand.isRequested(new String[]{"--server.port=18080"}));
    }

    @Test
    void outputAvoidsSpringStartupMarkersAndObviousCredentialTerms() throws Exception {
        Path replayFile = writeReplayFile("shadow-events.jsonl");

        CapturedRun run = runReplay("--lase-replay=" + replayFile);
        String combined = run.output() + run.error();
        String lower = combined.toLowerCase();

        assertFalse(combined.contains("Started LoadBalancerApiApplication"));
        assertFalse(combined.contains("Tomcat started"));
        assertFalse(combined.contains("SpringApplication"));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("secret"));
        assertFalse(lower.contains("credential"));
        assertFalse(lower.contains("accesskey"));
        assertFalse(lower.contains("token"));
    }

    private Path writeReplayFile(String fileName) throws Exception {
        LaseShadowReplayReader reader = new LaseShadowReplayReader();
        Path replayFile = tempDir.resolve(fileName);
        Files.writeString(replayFile,
                reader.toJsonLine(LaseShadowReplayRecord.fromEvent(event("eval-1", "S1", "S1", "HOLD", true)))
                        + System.lineSeparator()
                        + reader.toJsonLine(LaseShadowReplayRecord.fromEvent(event("eval-2", "S1", "S2", "SCALE_UP", false)))
                        + System.lineSeparator());
        return replayFile;
    }

    private CapturedRun runReplay(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        LaseReplayCommand.Result result = LaseReplayCommand.run(args,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new CapturedRun(result, output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private static Map<String, String> replaceSystemProperties(Map<String, String> replacements) {
        Map<String, String> previous = new HashMap<>();
        replacements.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        return previous;
    }

    private static void restoreSystemProperties(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
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

    private record CapturedRun(LaseReplayCommand.Result result, String output, String error) {
    }
}
