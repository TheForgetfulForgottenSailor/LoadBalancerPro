package core;

import java.util.Objects;
import java.util.Optional;

public record RoutingComparisonResult(
        RoutingStrategyId strategyId,
        Status status,
        Optional<RoutingDecision> decision,
        String reason) {

    public RoutingComparisonResult {
        Objects.requireNonNull(strategyId, "strategyId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(decision, "decision cannot be null");
        reason = requireNonBlank(reason, "reason");
    }

    public static RoutingComparisonResult success(RoutingStrategyId strategyId, RoutingDecision decision) {
        Objects.requireNonNull(decision, "decision cannot be null");
        return new RoutingComparisonResult(strategyId, Status.SUCCESS, Optional.of(decision),
                decision.explanation().reason());
    }

    public static RoutingComparisonResult failed(RoutingStrategyId strategyId, String reason) {
        return new RoutingComparisonResult(strategyId, Status.FAILED, Optional.empty(), reason);
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        FAILED
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }
}
