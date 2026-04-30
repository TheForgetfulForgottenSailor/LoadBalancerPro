package cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaseDemoCommandTest {
    @Test
    void laseDemoPrintsAllScenarioReportsWithSafetyWording() {
        CapturedRun run = runDemo("--lase-demo");

        assertEquals(0, run.result().exitCode());
        assertTrue(run.output().contains("LoadBalancerPro LASE Synthetic Demo"));
        assertTrue(run.output().contains("recommendation-only"));
        assertTrue(run.output().contains("No live AWS resources touched"));
        assertTrue(run.output().contains("No real routing mutation"));
        assertTrue(run.output().contains("HEALTHY"));
        assertTrue(run.output().contains("OVERLOADED"));
        assertTrue(run.output().contains("Evaluation ID: lase-demo-healthy"));
        assertTrue(run.output().contains("Evaluation ID: lase-demo-overloaded"));
        assertTrue(run.output().contains("Routing Decision:"));
        assertTrue(run.output().contains("Adaptive Concurrency:"));
        assertTrue(run.output().contains("Load Shedding:"));
        assertTrue(run.output().contains("Shadow Autoscaling:"));
        assertTrue(run.output().contains("Failure Scenario:"));
        assertTrue(run.error().isBlank());
    }

    @Test
    void scenarioSelectionPrintsOnlyRequestedScenario() {
        CapturedRun run = runDemo("--lase-demo=error-storm");

        assertEquals(0, run.result().exitCode());
        assertTrue(run.output().contains("ERROR_STORM"));
        assertTrue(run.output().contains("Evaluation ID: lase-demo-error-storm"));
        assertTrue(run.output().contains("INVESTIGATE"));
        assertFalse(run.output().contains("lase-demo-healthy"));
        assertFalse(run.output().contains("lase-demo-overloaded"));
        assertTrue(run.error().isBlank());
    }

    @Test
    void allScenarioSelectionIsExplicitlySupported() {
        CapturedRun run = runDemo("--lase-demo=all");

        assertEquals(0, run.result().exitCode());
        assertTrue(run.output().contains("HEALTHY"));
        assertTrue(run.output().contains("OVERLOADED"));
        assertTrue(run.output().contains("ERROR_STORM"));
        assertTrue(run.output().contains("PARTIAL_OUTAGE"));
        assertTrue(run.output().contains("LOW_SAMPLE"));
    }

    @Test
    void invalidScenarioFailsSafelyWithHelpfulUsage() {
        CapturedRun run = runDemo("--lase-demo=unknown");

        assertEquals(2, run.result().exitCode());
        assertTrue(run.output().isBlank());
        assertTrue(run.error().contains("Invalid LASE demo scenario"));
        assertTrue(run.error().contains("healthy"));
        assertTrue(run.error().contains("overloaded"));
        assertTrue(run.error().contains("error-storm"));
        assertTrue(run.error().contains("partial-outage"));
        assertTrue(run.error().contains("low-sample"));
        assertFalse(run.error().contains("Exception"));
        assertFalse(run.error().contains("\tat "));
    }

    @Test
    void requestDetectionOnlyMatchesLaseDemoFlag() {
        assertTrue(LaseDemoCommand.isRequested(new String[]{"--lase-demo"}));
        assertTrue(LaseDemoCommand.isRequested(new String[]{"--lase-demo=healthy"}));
        assertFalse(LaseDemoCommand.isRequested(new String[]{"--server.port=18080"}));
    }

    @Test
    void outputAvoidsRawObjectDumpsAndObviousCredentialTerms() {
        CapturedRun run = runDemo("--lase-demo=overloaded");
        String lower = run.output().toLowerCase();

        assertFalse(run.output().contains("LaseEvaluationReport["));
        assertFalse(run.output().contains("AutoscalingRecommendation["));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("secret"));
        assertFalse(lower.contains("credential"));
        assertFalse(lower.contains("accesskey"));
        assertFalse(lower.contains("token"));
    }

    private CapturedRun runDemo(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        LaseDemoCommand.Result result = LaseDemoCommand.run(args,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new CapturedRun(result, output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private record CapturedRun(LaseDemoCommand.Result result, String output, String error) {
    }
}
