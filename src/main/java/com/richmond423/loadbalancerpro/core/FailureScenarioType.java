package com.richmond423.loadbalancerpro.core;

public enum FailureScenarioType {
    TRAFFIC_SPIKE,
    SLOW_SERVER,
    QUEUE_BACKLOG,
    ERROR_STORM,
    FLAPPING_SERVER,
    PARTIAL_OUTAGE,
    CAPACITY_SATURATION
}
