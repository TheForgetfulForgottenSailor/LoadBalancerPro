package com.richmond423.loadbalancerpro.core;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class RoundRobinRoutingStrategy implements RoutingStrategy {
    public static final String STRATEGY_NAME = "ROUND_ROBIN";

    private final AtomicLong cursor = new AtomicLong();
    private final Clock clock;

    public RoundRobinRoutingStrategy() {
        this(Clock.systemUTC());
    }

    RoundRobinRoutingStrategy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public RoutingStrategyId id() {
        return RoutingStrategyId.ROUND_ROBIN;
    }

    @Override
    public RoutingDecision choose(List<ServerStateVector> servers) {
        Objects.requireNonNull(servers, "servers cannot be null");
        List<ServerStateVector> eligible = servers.stream()
                .filter(Objects::nonNull)
                .filter(ServerStateVector::healthy)
                .toList();
        if (eligible.isEmpty()) {
            return noCandidateDecision("No healthy eligible servers were available.");
        }

        int selectedIndex = eligible.size() == 1
                ? 0
                : Math.floorMod(cursor.getAndIncrement(), eligible.size());
        ServerStateVector chosen = eligible.get(selectedIndex);
        RoutingDecisionExplanation explanation = new RoutingDecisionExplanation(
                STRATEGY_NAME,
                eligible.stream().map(ServerStateVector::serverId).toList(),
                Optional.of(chosen.serverId()),
                Map.of(),
                reasonForChoice(chosen, eligible.size(), selectedIndex),
                Instant.now(clock));
        return new RoutingDecision(Optional.of(chosen), explanation);
    }

    private RoutingDecision noCandidateDecision(String reason) {
        RoutingDecisionExplanation explanation = new RoutingDecisionExplanation(
                STRATEGY_NAME, List.of(), Optional.empty(), Map.of(), reason, Instant.now(clock));
        return new RoutingDecision(Optional.empty(), explanation);
    }

    private String reasonForChoice(ServerStateVector chosen, int candidateCount, int selectedIndex) {
        if (candidateCount == 1) {
            return "Chose " + chosen.serverId() + " because it was the only healthy candidate.";
        }
        return "Chose " + chosen.serverId() + " using round-robin position "
                + (selectedIndex + 1) + " of " + candidateCount + " healthy candidates.";
    }
}
