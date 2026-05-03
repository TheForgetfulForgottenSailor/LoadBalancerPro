package core;

import java.util.List;

public interface RoutingStrategy {
    RoutingStrategyId id();

    RoutingDecision choose(List<ServerStateVector> servers);
}
