package api;

import java.time.Instant;
import java.util.List;

public record RoutingComparisonResponse(
        List<String> requestedStrategies,
        int candidateCount,
        Instant timestamp,
        List<RoutingComparisonResultResponse> results) {
}
