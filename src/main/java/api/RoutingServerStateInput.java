package api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoutingServerStateInput(
        @NotBlank(message = "serverId is required")
        String serverId,

        @NotNull(message = "healthy is required")
        Boolean healthy,

        @NotNull(message = "inFlightRequestCount is required")
        Integer inFlightRequestCount,

        Double configuredCapacity,

        Double estimatedConcurrencyLimit,

        @NotNull(message = "averageLatencyMillis is required")
        Double averageLatencyMillis,

        @NotNull(message = "p95LatencyMillis is required")
        Double p95LatencyMillis,

        @NotNull(message = "p99LatencyMillis is required")
        Double p99LatencyMillis,

        @NotNull(message = "recentErrorRate is required")
        Double recentErrorRate,

        Integer queueDepth,

        @Valid
        NetworkAwarenessInput networkAwareness) {
}
