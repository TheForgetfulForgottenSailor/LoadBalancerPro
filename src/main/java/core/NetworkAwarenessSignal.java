package core;

import java.time.Instant;
import java.util.Objects;

public record NetworkAwarenessSignal(
        String targetId,
        double timeoutRate,
        double retryRate,
        double connectionFailureRate,
        double latencyJitterMillis,
        boolean recentErrorBurst,
        int requestTimeoutCount,
        int sampleSize,
        Instant timestamp) {

    public NetworkAwarenessSignal {
        targetId = requireNonBlank(targetId, "targetId");
        requireRate(timeoutRate, "timeoutRate");
        requireRate(retryRate, "retryRate");
        requireRate(connectionFailureRate, "connectionFailureRate");
        requireFiniteNonNegative(latencyJitterMillis, "latencyJitterMillis");
        requireNonNegative(requestTimeoutCount, "requestTimeoutCount");
        requireNonNegative(sampleSize, "sampleSize");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public static NetworkAwarenessSignal neutral(String targetId, Instant timestamp) {
        return new NetworkAwarenessSignal(targetId, 0.0, 0.0, 0.0, 0.0, false, 0, 0, timestamp);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static void requireRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void requireFiniteNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }
}
