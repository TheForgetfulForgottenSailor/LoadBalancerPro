package cli;

import core.LaseShadowReplayEngine;
import core.LaseShadowReplayException;
import core.LaseShadowReplayMetrics;
import core.LaseShadowReplayReader;
import core.LaseShadowReplayReport;
import core.LaseShadowReplayReportFormatter;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LaseReplayCommand {
    private static final String FLAG = "--lase-replay";
    private static final List<String> WARNINGS = List.of(
            "Replay evaluates saved shadow events only; it does not change routing or cloud behavior.",
            "Replay is local, deterministic, and read-only; it does not parse packets or contact external systems.",
            "Replay metrics are offline analysis, not production telemetry durability."
    );

    private LaseReplayCommand() {
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

        Optional<String> replayPath = selectedReplayPath(args);
        if (replayPath.isEmpty() || replayPath.get().isBlank()) {
            printUsage(err);
            return new Result(true, 2);
        }

        try {
            Path path = Path.of(replayPath.get());
            LaseShadowReplayReader reader = new LaseShadowReplayReader();
            LaseShadowReplayMetrics metrics = new LaseShadowReplayEngine().evaluate(path, reader);
            LaseShadowReplayReport report = new LaseShadowReplayReport(sourceName(path), metrics, WARNINGS);
            out.println(new LaseShadowReplayReportFormatter().format(report));
            return new Result(true, 0);
        } catch (InvalidPathException | LaseShadowReplayException e) {
            err.println("LASE replay failed safely: " + safeMessage(e));
            printUsage(err);
            return new Result(true, 2);
        } catch (RuntimeException e) {
            err.println("LASE replay failed safely: " + safeMessage(e));
            return new Result(true, 1);
        }
    }

    private static Optional<String> selectedReplayPath(String[] args) {
        return Arrays.stream(args)
                .filter(Objects::nonNull)
                .filter(arg -> arg.equals(FLAG) || arg.startsWith(FLAG + "="))
                .findFirst()
                .map(arg -> arg.equals(FLAG) ? "" : arg.substring((FLAG + "=").length()).trim());
    }

    private static void printUsage(PrintStream err) {
        err.println("Usage: --lase-replay=<path-to-shadow-events.jsonl>");
        err.println("Input: schemaVersion=1 JSON Lines exported from LASE shadow observability.");
        err.println("Safety: offline/read-only replay; no API server, network access, CloudManager calls, or cloud mutation.");
    }

    private static String sourceName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    public record Result(boolean requested, int exitCode) {
    }
}
