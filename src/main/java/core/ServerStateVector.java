package core;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public record ServerStateVector(
        String serverId,
        boolean healthy,
        int inFlightRequestCount,
        OptionalDouble configuredCapacity,
        OptionalDouble estimatedConcurrencyLimit,
        double weight,
        double averageLatencyMillis,
        double p95LatencyMillis,
        double p99LatencyMillis,
        double recentErrorRate,
        OptionalInt queueDepth,
        NetworkAwarenessSignal networkAwarenessSignal,
        Instant timestamp) {

    public ServerStateVector {
        serverId = requireNonBlank(serverId, "serverId");
        Objects.requireNonNull(configuredCapacity, "configuredCapacity cannot be null");
        Objects.requireNonNull(estimatedConcurrencyLimit, "estimatedConcurrencyLimit cannot be null");
        Objects.requireNonNull(queueDepth, "queueDepth cannot be null");
        Objects.requireNonNull(networkAwarenessSignal, "networkAwarenessSignal cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        requireNonNegative(inFlightRequestCount, "inFlightRequestCount");
        configuredCapacity.ifPresent(value -> requireNonNegative(value, "configuredCapacity"));
        estimatedConcurrencyLimit.ifPresent(value -> requirePositive(value, "estimatedConcurrencyLimit"));
        requireNonNegative(weight, "weight");
        requireNonNegative(averageLatencyMillis, "averageLatencyMillis");
        requireNonNegative(p95LatencyMillis, "p95LatencyMillis");
        requireNonNegative(p99LatencyMillis, "p99LatencyMillis");
        requireRate(recentErrorRate, "recentErrorRate");
        queueDepth.ifPresent(value -> requireNonNegative(value, "queueDepth"));
    }

    public ServerStateVector(String serverId,
                             boolean healthy,
                             int inFlightRequestCount,
                             OptionalDouble configuredCapacity,
                             OptionalDouble estimatedConcurrencyLimit,
                             double averageLatencyMillis,
                             double p95LatencyMillis,
                             double p99LatencyMillis,
                             double recentErrorRate,
                             OptionalInt queueDepth,
                             NetworkAwarenessSignal networkAwarenessSignal,
                             Instant timestamp) {
        this(serverId, healthy, inFlightRequestCount, configuredCapacity, estimatedConcurrencyLimit, 1.0,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth,
                networkAwarenessSignal, timestamp);
    }

    public ServerStateVector(String serverId,
                             boolean healthy,
                             int inFlightRequestCount,
                             OptionalDouble configuredCapacity,
                             OptionalDouble estimatedConcurrencyLimit,
                             double weight,
                             double averageLatencyMillis,
                             double p95LatencyMillis,
                             double p99LatencyMillis,
                             double recentErrorRate,
                             OptionalInt queueDepth,
                             Instant timestamp) {
        this(serverId, healthy, inFlightRequestCount, configuredCapacity, estimatedConcurrencyLimit, weight,
                averageLatencyMillis, p95LatencyMillis, p99LatencyMillis, recentErrorRate, queueDepth,
                NetworkAwarenessSignal.neutral(serverId, timestamp), timestamp);
    }

    public ServerStateVector(String serverId,
                             boolean healthy,
                             int inFlightRequestCount,
                             double configuredCapacity,
                             double estimatedConcurrencyLimit,
                             double averageLatencyMillis,
                             double p95LatencyMillis,
                             double p99LatencyMillis,
                             double recentErrorRate,
                             int queueDepth,
                             Instant timestamp) {
        this(serverId, healthy, inFlightRequestCount, OptionalDouble.of(configuredCapacity),
                OptionalDouble.of(estimatedConcurrencyLimit), 1.0, averageLatencyMillis, p95LatencyMillis,
                p99LatencyMillis, recentErrorRate, OptionalInt.of(queueDepth),
                NetworkAwarenessSignal.neutral(serverId, timestamp), timestamp);
    }

    public ServerStateVector(String serverId,
                             boolean healthy,
                             int inFlightRequestCount,
                             double configuredCapacity,
                             double estimatedConcurrencyLimit,
                             double averageLatencyMillis,
                             double p95LatencyMillis,
                             double p99LatencyMillis,
                             double recentErrorRate,
                             int queueDepth,
                             NetworkAwarenessSignal networkAwarenessSignal,
                             Instant timestamp) {
        this(serverId, healthy, inFlightRequestCount, OptionalDouble.of(configuredCapacity),
                OptionalDouble.of(estimatedConcurrencyLimit), 1.0, averageLatencyMillis, p95LatencyMillis,
                p99LatencyMillis, recentErrorRate, OptionalInt.of(queueDepth), networkAwarenessSignal, timestamp);
    }

    public static ServerStateVector fromServer(Server server,
                                               int inFlightRequestCount,
                                               double averageLatencyMillis,
                                               double p95LatencyMillis,
                                               double p99LatencyMillis,
                                               double recentErrorRate,
                                               int queueDepth,
                                               Instant timestamp) {
        Objects.requireNonNull(server, "server cannot be null");
        return new ServerStateVector(server.getServerId(), server.isHealthy(), inFlightRequestCount,
                OptionalDouble.of(server.getCapacity()), OptionalDouble.empty(), server.getWeight(), averageLatencyMillis,
                p95LatencyMillis, p99LatencyMillis, recentErrorRate, OptionalInt.of(queueDepth),
                NetworkAwarenessSignal.neutral(server.getServerId(), timestamp), timestamp);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static void requireNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }

    private static void requirePositive(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and positive");
        }
    }

    private static void requireRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
