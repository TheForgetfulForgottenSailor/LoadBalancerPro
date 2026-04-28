package api;

import java.util.Map;

public record AllocationResponse(
        Map<String, Double> allocations,
        double unallocatedLoad,
        int recommendedAdditionalServers) {
}
