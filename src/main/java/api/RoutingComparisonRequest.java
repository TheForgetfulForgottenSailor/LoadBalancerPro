package api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoutingComparisonRequest(
        List<String> strategies,

        @Valid
        @NotNull(message = "servers is required")
        @Size(min = 1, message = "servers must contain at least one server")
        List<@NotNull(message = "server input cannot be null") @Valid RoutingServerStateInput> servers) {
}
