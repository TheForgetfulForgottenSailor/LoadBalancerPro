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
    private static final double TIMEOUT_RATE_WEIGHT = 800.0;
    private static final double RETRY_RATE_WEIGHT = 350.0;
    private static final double CONNECTION_FAILURE_RATE_WEIGHT = 900.0;
    private static final double LATENCY_JITTER_WEIGHT = 0.50;
    private static final double RECENT_ERROR_BURST_PENALTY = 250.0;
    private static final double REQUEST_TIMEOUT_COUNT_WEIGHT = 20.0;

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
                + (state.recentErrorRate() * ERROR_RATE_WEIGHT)
                + networkRiskScore(state.networkAwarenessSignal());
        return state.healthy() ? score : score + UNHEALTHY_PENALTY;
    }

    public double networkRiskScore(NetworkAwarenessSignal signal) {
        Objects.requireNonNull(signal, "signal cannot be null");
        return (signal.timeoutRate() * TIMEOUT_RATE_WEIGHT)
                + (signal.retryRate() * RETRY_RATE_WEIGHT)
                + (signal.connectionFailureRate() * CONNECTION_FAILURE_RATE_WEIGHT)
                + (signal.latencyJitterMillis() * LATENCY_JITTER_WEIGHT)
                + (signal.recentErrorBurst() ? RECENT_ERROR_BURST_PENALTY : 0.0)
                + (signal.requestTimeoutCount() * REQUEST_TIMEOUT_COUNT_WEIGHT);
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
