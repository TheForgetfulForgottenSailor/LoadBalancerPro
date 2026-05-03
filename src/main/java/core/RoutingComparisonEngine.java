package core;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RoutingComparisonEngine {
    private final RoutingStrategyRegistry registry;
    private final Clock clock;

    public RoutingComparisonEngine() {
        this(RoutingStrategyRegistry.defaultRegistry(), Clock.systemUTC());
    }

    public RoutingComparisonEngine(RoutingStrategyRegistry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public RoutingComparisonReport compare(List<ServerStateVector> candidates) {
        return compare(candidates, registry.registeredIds());
    }

    public RoutingComparisonReport compare(List<ServerStateVector> candidates,
                                           List<RoutingStrategyId> requestedStrategies) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        Objects.requireNonNull(requestedStrategies, "requestedStrategies cannot be null");

        List<ServerStateVector> immutableCandidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        List<RoutingStrategyId> requested = List.copyOf(requestedStrategies);
        List<RoutingComparisonResult> results = new ArrayList<>();

        for (RoutingStrategyId strategyId : requested) {
            results.add(runStrategy(strategyId, immutableCandidates));
        }

        return new RoutingComparisonReport(requested, immutableCandidates.size(), results, Instant.now(clock));
    }

    private RoutingComparisonResult runStrategy(RoutingStrategyId strategyId, List<ServerStateVector> candidates) {
        return registry.find(strategyId)
                .map(strategy -> runRegisteredStrategy(strategyId, strategy, candidates))
                .orElseGet(() -> RoutingComparisonResult.failed(strategyId,
                        "Routing strategy is not registered: " + strategyId));
    }

    private RoutingComparisonResult runRegisteredStrategy(RoutingStrategyId strategyId,
                                                         RoutingStrategy strategy,
                                                         List<ServerStateVector> candidates) {
        try {
            RoutingDecision decision = strategy.choose(candidates);
            if (decision == null) {
                return RoutingComparisonResult.failed(strategyId,
                        "Routing strategy returned no decision: " + strategyId);
            }
            return RoutingComparisonResult.success(strategyId, decision);
        } catch (RuntimeException e) {
            return RoutingComparisonResult.failed(strategyId,
                    "Routing strategy failed safely: " + safeMessage(e));
        }
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
