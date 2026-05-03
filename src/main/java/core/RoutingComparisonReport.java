package core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RoutingComparisonReport(
        List<RoutingStrategyId> requestedStrategies,
        int candidateCount,
        List<RoutingComparisonResult> results,
        Instant timestamp) {

    public RoutingComparisonReport {
        Objects.requireNonNull(requestedStrategies, "requestedStrategies cannot be null");
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        Objects.requireNonNull(results, "results cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        requestedStrategies = List.copyOf(requestedStrategies);
        results = List.copyOf(results);
    }
}
