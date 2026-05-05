package com.richmond423.loadbalancerpro.core;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class AdaptiveConcurrencyLimiter {
    private final AdaptiveConcurrencyConfig config;
    private final Clock clock;

    public AdaptiveConcurrencyLimiter(AdaptiveConcurrencyConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ConcurrencyLimitDecision calculateNextLimit(int currentLimit, ConcurrencyFeedback feedback) {
        if (currentLimit < 1) {
            throw new IllegalArgumentException("currentLimit must be at least 1");
        }
        Objects.requireNonNull(feedback, "feedback cannot be null");

        boolean lowSample = feedback.sampleSize() < config.minSampleSize();
        boolean latencyAboveTarget = feedback.observedP95LatencyMillis() > config.targetP95LatencyMillis();
        boolean errorAboveThreshold = feedback.observedErrorRate() > config.maxErrorRate();

        LimitProposal proposal = proposeLimit(currentLimit, feedback, lowSample,
                latencyAboveTarget, errorAboveThreshold);
        int nextLimit = clamp(proposal.desiredLimit());
        ConcurrencyLimitDecision.Action action = proposal.action();
        String reason = proposal.reason();
        if (nextLimit != proposal.desiredLimit()) {
            action = ConcurrencyLimitDecision.Action.CLAMP;
            reason = reason + " Clamped to " + clampBoundary(nextLimit) + " limit " + nextLimit + ".";
        }

        return new ConcurrencyLimitDecision(feedback.serverId(), currentLimit, nextLimit,
                config.minLimit(), config.maxLimit(), action, reason, Instant.now(clock),
                feedback.currentInFlightRequestCount(), feedback.observedAverageLatencyMillis(),
                feedback.observedP95LatencyMillis(), feedback.observedP99LatencyMillis(),
                feedback.observedErrorRate(), feedback.sampleSize());
    }

    private LimitProposal proposeLimit(int currentLimit,
                                       ConcurrencyFeedback feedback,
                                       boolean lowSample,
                                       boolean latencyAboveTarget,
                                       boolean errorAboveThreshold) {
        if (lowSample) {
            return new LimitProposal(currentLimit, ConcurrencyLimitDecision.Action.HOLD,
                    "Holding limit because sample size " + feedback.sampleSize()
                            + " is below minimum " + config.minSampleSize() + ".");
        }
        if (latencyAboveTarget || errorAboveThreshold) {
            int decreaseApplications = latencyAboveTarget && errorAboveThreshold ? 2 : 1;
            int desiredLimit = multiplicativeDecrease(currentLimit, decreaseApplications);
            return new LimitProposal(desiredLimit, ConcurrencyLimitDecision.Action.DECREASE,
                    decreaseReason(feedback, latencyAboveTarget, errorAboveThreshold));
        }
        return new LimitProposal(currentLimit + config.additiveStep(),
                ConcurrencyLimitDecision.Action.INCREASE,
                "Increasing limit because latency and error signals are healthy.");
    }

    private int multiplicativeDecrease(int currentLimit, int applications) {
        double nextLimit = currentLimit;
        for (int i = 0; i < applications; i++) {
            nextLimit *= config.decreaseFactor();
        }
        return Math.max(1, (int) Math.floor(nextLimit));
    }

    private String decreaseReason(ConcurrencyFeedback feedback,
                                  boolean latencyAboveTarget,
                                  boolean errorAboveThreshold) {
        if (latencyAboveTarget && errorAboveThreshold) {
            return "Decreasing limit because p95 latency " + format(feedback.observedP95LatencyMillis())
                    + "ms exceeded target " + format(config.targetP95LatencyMillis())
                    + "ms and error rate " + format(feedback.observedErrorRate())
                    + " exceeded threshold " + format(config.maxErrorRate()) + ".";
        }
        if (latencyAboveTarget) {
            return "Decreasing limit because p95 latency " + format(feedback.observedP95LatencyMillis())
                    + "ms exceeded target " + format(config.targetP95LatencyMillis()) + "ms.";
        }
        return "Decreasing limit because error rate " + format(feedback.observedErrorRate())
                + " exceeded threshold " + format(config.maxErrorRate()) + ".";
    }

    private int clamp(int desiredLimit) {
        return Math.min(config.maxLimit(), Math.max(config.minLimit(), desiredLimit));
    }

    private String clampBoundary(int nextLimit) {
        return nextLimit == config.minLimit() ? "min" : "max";
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record LimitProposal(int desiredLimit, ConcurrencyLimitDecision.Action action, String reason) {
    }
}
