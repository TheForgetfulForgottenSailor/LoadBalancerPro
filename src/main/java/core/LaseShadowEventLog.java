package core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LaseShadowEventLog {
    public static final int DEFAULT_MAX_SIZE = 100;

    private final int maxSize;
    private final ArrayDeque<LaseShadowEvent> recentEvents = new ArrayDeque<>();
    private final Map<String, Long> recommendationCounts = new LinkedHashMap<>();
    private long totalEvaluations;
    private long comparableEvaluations;
    private long agreementCount;
    private long failSafeCount;
    private Instant latestEventTimestamp;

    public LaseShadowEventLog() {
        this(DEFAULT_MAX_SIZE);
    }

    public LaseShadowEventLog(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
    }

    public synchronized void record(LaseShadowEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        totalEvaluations++;
        if (event.agreedWithRouting() != null) {
            comparableEvaluations++;
            if (Boolean.TRUE.equals(event.agreedWithRouting())) {
                agreementCount++;
            }
        }
        if (event.failSafe()) {
            failSafeCount++;
        }
        latestEventTimestamp = event.timestamp();
        recommendationCounts.merge(event.recommendedAction(), 1L, Long::sum);

        recentEvents.addLast(event);
        while (recentEvents.size() > maxSize) {
            recentEvents.removeFirst();
        }
    }

    public synchronized LaseShadowObservabilitySnapshot snapshot() {
        double agreementRate = comparableEvaluations == 0
                ? 0.0
                : agreementCount / (double) comparableEvaluations;
        LaseShadowSummary summary = new LaseShadowSummary(
                maxSize,
                totalEvaluations,
                comparableEvaluations,
                agreementCount,
                agreementRate,
                failSafeCount,
                latestEventTimestamp,
                new LinkedHashMap<>(recommendationCounts));
        return new LaseShadowObservabilitySnapshot(summary, new ArrayList<>(recentEvents));
    }
}
