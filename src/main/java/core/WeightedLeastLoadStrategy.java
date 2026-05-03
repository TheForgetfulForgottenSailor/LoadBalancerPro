package core;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WeightedLeastLoadStrategy implements RoutingStrategy {
    public static final String STRATEGY_NAME = "WEIGHTED_LEAST_LOAD";

    private static final double MIN_CAPACITY = 1.0;
    private static final double DEFAULT_WEIGHT = 1.0;
    private static final double MIN_POSITIVE_WEIGHT = 0.1;

    private static final double LOAD_PRESSURE_WEIGHT = 0.45;
    private static final double QUEUE_PRESSURE_WEIGHT = 0.20;
    private static final double LATENCY_PRESSURE_WEIGHT = 0.15;
    private static final double TAIL_PRESSURE_WEIGHT = 0.10;
    private static final double ERROR_PRESSURE_WEIGHT = 0.10;

    private final Clock clock;

    public WeightedLeastLoadStrategy() {
        this(Clock.systemUTC());
    }

    public WeightedLeastLoadStrategy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public RoutingStrategyId id() {
        return RoutingStrategyId.WEIGHTED_LEAST_LOAD;
    }

    @Override
    public RoutingDecision choose(List<ServerStateVector> servers) {
        Objects.requireNonNull(servers, "servers cannot be null");
        List<ServerStateVector> eligible = servers.stream()
                .filter(Objects::nonNull)
                .filter(ServerStateVector::healthy)
                .sorted(Comparator.comparing(ServerStateVector::serverId))
                .toList();
        if (eligible.isEmpty()) {
            return noCandidateDecision("No healthy eligible servers were available.");
        }

        Map<String, Double> scores = scoreCandidates(eligible);
        ServerStateVector chosen = eligible.stream()
                .min(Comparator.comparingDouble((ServerStateVector state) -> scores.get(state.serverId()))
                        .thenComparing(ServerStateVector::serverId))
                .orElseThrow();

        RoutingDecisionExplanation explanation = new RoutingDecisionExplanation(
                STRATEGY_NAME,
                eligible.stream().map(ServerStateVector::serverId).toList(),
                Optional.of(chosen.serverId()),
                scores,
                reasonForChoice(chosen, eligible, scores),
                Instant.now(clock));
        return new RoutingDecision(Optional.of(chosen), explanation);
    }

    private RoutingDecision noCandidateDecision(String reason) {
        RoutingDecisionExplanation explanation = new RoutingDecisionExplanation(
                STRATEGY_NAME, List.of(), Optional.empty(), Map.of(), reason, Instant.now(clock));
        return new RoutingDecision(Optional.empty(), explanation);
    }

    private Map<String, Double> scoreCandidates(List<ServerStateVector> candidates) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (ServerStateVector candidate : candidates) {
            scores.put(candidate.serverId(), score(candidate));
        }
        return scores;
    }

    private double score(ServerStateVector state) {
        double effectiveCapacity = effectiveCapacity(state);
        double effectiveWeight = effectiveWeight(state.weight());
        double loadPressure = state.inFlightRequestCount() / effectiveCapacity;
        double queuePressure = state.queueDepth().orElse(0) / effectiveCapacity;
        double latencyPressure = state.averageLatencyMillis()
                / Math.max(Math.max(state.p95LatencyMillis(), state.averageLatencyMillis()), 1.0);
        double tailPressure = state.p95LatencyMillis()
                / Math.max(Math.max(state.p99LatencyMillis(), state.p95LatencyMillis()), 1.0);
        double weightedScore = (loadPressure * LOAD_PRESSURE_WEIGHT)
                + (queuePressure * QUEUE_PRESSURE_WEIGHT)
                + (latencyPressure * LATENCY_PRESSURE_WEIGHT)
                + (tailPressure * TAIL_PRESSURE_WEIGHT)
                + (state.recentErrorRate() * ERROR_PRESSURE_WEIGHT);
        return weightedScore / effectiveWeight;
    }

    private double effectiveCapacity(ServerStateVector state) {
        if (state.estimatedConcurrencyLimit().isPresent()) {
            return Math.max(MIN_CAPACITY, state.estimatedConcurrencyLimit().getAsDouble());
        }
        if (state.configuredCapacity().isPresent()) {
            return Math.max(MIN_CAPACITY, state.configuredCapacity().getAsDouble());
        }
        return MIN_CAPACITY;
    }

    private double effectiveWeight(double weight) {
        if (weight == 0.0) {
            return DEFAULT_WEIGHT;
        }
        return Math.max(MIN_POSITIVE_WEIGHT, weight);
    }

    private String reasonForChoice(ServerStateVector chosen,
                                   List<ServerStateVector> candidates,
                                   Map<String, Double> scores) {
        if (candidates.size() == 1) {
            return "Chose " + chosen.serverId() + " because it was the only healthy candidate with weighted "
                    + "least-load score " + formatScore(scores.get(chosen.serverId())) + ".";
        }
        return "Chose " + chosen.serverId() + " because its weighted least-load score "
                + formatScore(scores.get(chosen.serverId())) + " was the lowest across "
                + candidates.size() + " healthy candidates.";
    }

    private String formatScore(double score) {
        return String.format("%.3f", score);
    }
}
