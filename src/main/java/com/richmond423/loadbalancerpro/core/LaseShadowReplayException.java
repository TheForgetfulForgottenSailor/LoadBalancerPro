package com.richmond423.loadbalancerpro.core;

public class LaseShadowReplayException extends RuntimeException {
    public LaseShadowReplayException(String message) {
        super(message);
    }

    public LaseShadowReplayException(String message, Throwable cause) {
        super(message, cause);
    }
}
