package core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record RoutingDecisionExplanation(
        String strategyUsed,
        List<String> candidateServersConsidered,
        Optional<String> chosenServerId,
        Map<String, Double> scores,
        String reason,
        Instant timestamp) {

    public RoutingDecisionExplanation {
        strategyUsed = requireNonBlank(strategyUsed, "strategyUsed");
        Objects.requireNonNull(candidateServersConsidered, "candidateServersConsidered cannot be null");
        Objects.requireNonNull(chosenServerId, "chosenServerId cannot be null");
        Objects.requireNonNull(scores, "scores cannot be null");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        candidateServersConsidered = List.copyOf(candidateServersConsidered);
        scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }
}
