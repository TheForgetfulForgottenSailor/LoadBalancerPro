package core;

import java.time.Instant;
import java.util.Objects;

public record LaseShadowEvent(
        String evaluationId,
        Instant timestamp,
        String strategy,
        double requestedLoad,
        double unallocatedLoad,
        String actualSelectedServerId,
        String recommendedServerId,
        String recommendedAction,
        Double decisionScore,
        String reason,
        Boolean agreedWithRouting,
        boolean failSafe,
        String failureReason) {

    public LaseShadowEvent {
        evaluationId = requireNonBlank(evaluationId, "evaluationId");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        strategy = requireNonBlank(strategy, "strategy");
        validateNonNegativeFinite(requestedLoad, "requestedLoad");
        validateNonNegativeFinite(unallocatedLoad, "unallocatedLoad");
        actualSelectedServerId = blankToNull(actualSelectedServerId);
        recommendedServerId = blankToNull(recommendedServerId);
        recommendedAction = requireNonBlank(recommendedAction, "recommendedAction");
        if (decisionScore != null) {
            validateNonNegativeFinite(decisionScore, "decisionScore");
        }
        reason = requireNonBlank(reason, "reason");
        if (failSafe && (failureReason == null || failureReason.isBlank())) {
            failureReason = "shadow evaluation failed safely";
        } else {
            failureReason = blankToNull(failureReason);
        }
    }

    private static void validateNonNegativeFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
