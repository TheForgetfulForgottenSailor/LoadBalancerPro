package core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoutingStrategyRegistry {
    private final Map<RoutingStrategyId, RoutingStrategy> strategies;

    public RoutingStrategyRegistry() {
        this(List.of(new TailLatencyPowerOfTwoStrategy()));
    }

    public RoutingStrategyRegistry(Collection<? extends RoutingStrategy> strategies) {
        Objects.requireNonNull(strategies, "strategies cannot be null");
        Map<RoutingStrategyId, RoutingStrategy> registeredStrategies = new LinkedHashMap<>();
        for (RoutingStrategy strategy : strategies) {
            RoutingStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategies cannot contain null");
            RoutingStrategyId strategyId = Objects.requireNonNull(nonNullStrategy.id(),
                    "strategy id cannot be null");
            RoutingStrategy previous = registeredStrategies.putIfAbsent(strategyId, nonNullStrategy);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate routing strategy id: " + strategyId);
            }
        }
        this.strategies = Collections.unmodifiableMap(registeredStrategies);
    }

    public static RoutingStrategyRegistry defaultRegistry() {
        return new RoutingStrategyRegistry();
    }

    public Optional<RoutingStrategy> find(RoutingStrategyId strategyId) {
        if (strategyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(strategyId));
    }

    public RoutingStrategy require(RoutingStrategyId strategyId) {
        return find(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("Routing strategy is not registered: " + strategyId));
    }

    public List<RoutingStrategyId> registeredIds() {
        return List.copyOf(strategies.keySet());
    }
}
