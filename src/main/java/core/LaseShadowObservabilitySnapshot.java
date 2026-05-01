package core;

import java.util.List;
import java.util.Objects;

public record LaseShadowObservabilitySnapshot(
        LaseShadowSummary summary,
        List<LaseShadowEvent> recentEvents) {

    public LaseShadowObservabilitySnapshot {
        Objects.requireNonNull(summary, "summary cannot be null");
        Objects.requireNonNull(recentEvents, "recentEvents cannot be null");
        recentEvents = List.copyOf(recentEvents);
    }
}
