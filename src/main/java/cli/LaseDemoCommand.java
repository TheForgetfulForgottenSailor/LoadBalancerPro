package cli;

import core.FailureScenarioRunner;
import core.LaseDemoScenario;
import core.LaseDemoScenarioFactory;
import core.LaseDemoScenarioType;
import core.LaseEvaluationEngine;
import core.LaseEvaluationReport;
import core.LaseEvaluationReportFormatter;
import core.LoadSheddingPolicy;
import core.ServerScoreCalculator;
import core.ShadowAutoscaler;
import core.TailLatencyPowerOfTwoStrategy;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public final class LaseDemoCommand {
    private static final String FLAG = "--lase-demo";
    private static final Clock DEMO_CLOCK = Clock.fixed(
            Instant.parse("2026-04-29T12:00:00Z"), ZoneOffset.UTC);
    private static final long ROUTING_SEED = 7L;
    private static final Map<String, LaseDemoScenarioType> SCENARIOS = Map.of(
            "healthy", LaseDemoScenarioType.HEALTHY,
            "overloaded", LaseDemoScenarioType.OVERLOADED,
            "error-storm", LaseDemoScenarioType.ERROR_STORM,
            "partial-outage", LaseDemoScenarioType.PARTIAL_OUTAGE,
            "low-sample", LaseDemoScenarioType.LOW_SAMPLE
    );

    private LaseDemoCommand() {
    }

    public static boolean isRequested(String[] args) {
        if (args == null) {
            return false;
        }
        return Arrays.stream(args)
                .filter(Objects::nonNull)
                .anyMatch(arg -> arg.equals(FLAG) || arg.startsWith(FLAG + "="));
    }

    public static Result runIfRequested(String[] args, PrintStream out, PrintStream err) {
        if (!isRequested(args)) {
            return new Result(false, 0);
        }
        return run(args, out, err);
    }

    public static Result run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args cannot be null");
        Objects.requireNonNull(out, "out cannot be null");
        Objects.requireNonNull(err, "err cannot be null");

        Optional<String> selection = selectedScenarioName(args);
        if (selection.isPresent() && !isAllSelection(selection.get()) && !SCENARIOS.containsKey(selection.get())) {
            err.println("Invalid LASE demo scenario: " + selection.get());
            err.println("Valid values: all, " + validScenarioNames());
            return new Result(true, 2);
        }

        try {
            printHeader(out);
            LaseDemoScenarioFactory factory = new LaseDemoScenarioFactory(DEMO_CLOCK);
            List<LaseDemoScenario> scenarios = selectedScenarios(factory, selection);
            LaseEvaluationReportFormatter formatter = new LaseEvaluationReportFormatter();
            for (LaseDemoScenario scenario : scenarios) {
                out.println();
                out.println("Scenario: " + scenario.type());
                out.println("Description: " + scenario.description());
                LaseEvaluationReport report = newEngine().evaluate(scenario.input(), scenario.config());
                out.println(formatter.format(report));
            }
            return new Result(true, 0);
        } catch (RuntimeException e) {
            err.println("LASE demo failed safely: " + safeMessage(e));
            return new Result(true, 1);
        }
    }

    private static void printHeader(PrintStream out) {
        out.println("=== LoadBalancerPro LASE Synthetic Demo ===");
        out.println("Mode: synthetic demo, recommendation-only evaluation.");
        out.println("Safety: No live AWS resources touched. No real routing mutation. No CloudManager calls.");
        out.println("Runtime: deterministic local inputs; no AWS keys, network access, or API server required.");
    }

    private static Optional<String> selectedScenarioName(String[] args) {
        return Arrays.stream(args)
                .filter(Objects::nonNull)
                .filter(arg -> arg.equals(FLAG) || arg.startsWith(FLAG + "="))
                .findFirst()
                .map(arg -> arg.equals(FLAG) ? "all" : normalize(arg.substring((FLAG + "=").length())));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean isAllSelection(String value) {
        return "all".equals(value);
    }

    private static List<LaseDemoScenario> selectedScenarios(LaseDemoScenarioFactory factory,
                                                            Optional<String> selection) {
        if (selection.isEmpty() || isAllSelection(selection.get())) {
            return factory.createAll();
        }
        return List.of(factory.create(SCENARIOS.get(selection.get())));
    }

    private static LaseEvaluationEngine newEngine() {
        return new LaseEvaluationEngine(
                new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(ROUTING_SEED), DEMO_CLOCK),
                new LoadSheddingPolicy(),
                new ShadowAutoscaler(),
                new FailureScenarioRunner(),
                DEMO_CLOCK);
    }

    private static String validScenarioNames() {
        return SCENARIOS.keySet().stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String safeMessage(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    public record Result(boolean requested, int exitCode) {
    }
}
