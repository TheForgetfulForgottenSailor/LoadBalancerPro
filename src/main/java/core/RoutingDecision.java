package core;

import java.util.Objects;
import java.util.Optional;

public record RoutingDecision(
        Optional<ServerStateVector> chosenServer,
        RoutingDecisionExplanation explanation) {

    public RoutingDecision {
        Objects.requireNonNull(chosenServer, "chosenServer cannot be null");
        Objects.requireNonNull(explanation, "explanation cannot be null");
    }
}
