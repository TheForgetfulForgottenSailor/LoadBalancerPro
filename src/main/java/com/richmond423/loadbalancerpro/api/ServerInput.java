package com.richmond423.loadbalancerpro.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record ServerInput(
        @NotBlank(message = "server id is required")
        String id,

        @DecimalMin(value = "0.0", inclusive = true, message = "cpuUsage must be at least 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "cpuUsage must be at most 100")
        double cpuUsage,

        @DecimalMin(value = "0.0", inclusive = true, message = "memoryUsage must be at least 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "memoryUsage must be at most 100")
        double memoryUsage,

        @DecimalMin(value = "0.0", inclusive = true, message = "diskUsage must be at least 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "diskUsage must be at most 100")
        double diskUsage,

        @DecimalMin(value = "0.0", inclusive = true, message = "capacity must be non-negative")
        double capacity,

        @DecimalMin(value = "0.0", inclusive = true, message = "weight must be non-negative")
        double weight,

        boolean healthy) {
}
