package api;

import java.util.List;
import java.util.Map;

public record RoutingComparisonResultResponse(
        String strategyId,
        String status,
        String chosenServerId,
        String reason,
        List<String> candidateServersConsidered,
        Map<String, Double> scores) {
}
