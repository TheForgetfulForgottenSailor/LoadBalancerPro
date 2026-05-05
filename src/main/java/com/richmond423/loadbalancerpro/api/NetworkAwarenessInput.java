package com.richmond423.loadbalancerpro.api;

public record NetworkAwarenessInput(
        Double timeoutRate,
        Double retryRate,
        Double connectionFailureRate,
        Double latencyJitterMillis,
        Boolean recentErrorBurst,
        Integer requestTimeoutCount,
        Integer sampleSize) {
}
