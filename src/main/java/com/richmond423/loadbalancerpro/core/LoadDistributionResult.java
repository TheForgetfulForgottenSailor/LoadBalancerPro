package com.richmond423.loadbalancerpro.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LoadDistributionResult {
    private final Map<String, Double> allocations;
    private final double unallocatedLoad;

    public LoadDistributionResult(Map<String, Double> allocations, double unallocatedLoad) {
        this.allocations = Collections.unmodifiableMap(new LinkedHashMap<>(allocations));
        this.unallocatedLoad = Math.max(0.0, unallocatedLoad);
    }

    public Map<String, Double> allocations() {
        return allocations;
    }

    public double unallocatedLoad() {
        return unallocatedLoad;
    }
}
