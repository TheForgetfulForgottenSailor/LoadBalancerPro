package core;

import java.util.Objects;

public final class ServerScoreCalculator {
    private static final double UNHEALTHY_PENALTY = 1_000_000.0;
    private static final double P95_WEIGHT = 0.45;
    private static final double P99_WEIGHT = 0.35;
    private static final double AVERAGE_LATENCY_WEIGHT = 0.10;
    private static final double IN_FLIGHT_RATIO_WEIGHT = 100.0;
    private static final double QUEUE_RATIO_WEIGHT = 100.0;
    private static final double ERROR_RATE_WEIGHT = 1_000.0;

    public double score(ServerStateVector state) {
        Objects.requireNonNull(state, "state cannot be null");
        double capacityBasis = capacityBasis(state);
        double inFlightRatio = state.inFlightRequestCount() / capacityBasis;
        double queueRatio = state.queueDepth().orElse(0) / capacityBasis;
        double score = (state.p95LatencyMillis() * P95_WEIGHT)
                + (state.p99LatencyMillis() * P99_WEIGHT)
                + (state.averageLatencyMillis() * AVERAGE_LATENCY_WEIGHT)
                + (inFlightRatio * IN_FLIGHT_RATIO_WEIGHT)
                + (queueRatio * QUEUE_RATIO_WEIGHT)
                + (state.recentErrorRate() * ERROR_RATE_WEIGHT);
        return state.healthy() ? score : score + UNHEALTHY_PENALTY;
    }

    private double capacityBasis(ServerStateVector state) {
        if (state.estimatedConcurrencyLimit().isPresent()) {
            return Math.max(1.0, state.estimatedConcurrencyLimit().getAsDouble());
        }
        if (state.configuredCapacity().isPresent()) {
            return Math.max(1.0, state.configuredCapacity().getAsDouble());
        }
        return 1.0;
    }
}
