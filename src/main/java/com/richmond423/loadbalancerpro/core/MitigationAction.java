package com.richmond423.loadbalancerpro.core;

public enum MitigationAction {
    HOLD,
    ROUTE_AROUND,
    REDUCE_CONCURRENCY,
    SHED_LOW_PRIORITY,
    SCALE_UP_SHADOW,
    INVESTIGATE,
    FAIL_CLOSED
}
