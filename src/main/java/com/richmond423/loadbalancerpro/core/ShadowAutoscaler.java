package com.richmond423.loadbalancerpro.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ShadowAutoscaler {
    public AutoscalingRecommendation recommend(AutoscalingSignal signal, ShadowAutoscalerConfig config) {
        Objects.requireNonNull(signal, "signal cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        if (signal.sampleSize() < config.minSampleSize()) {
            return recommendation(signal, AutoscalingAction.HOLD, signal.currentCapacity(), 0.0,
                    "Holding capacity because insufficient sample size " + signal.sampleSize()
                            + " is below minimum " + config.minSampleSize() + ".");
        }

        List<String> scaleUpReasons = scaleUpReasons(signal, config);
        if (!scaleUpReasons.isEmpty() && signal.observedErrorRate() > config.maxErrorRate()) {
            scaleUpReasons.add("error rate " + format(signal.observedErrorRate())
                    + " exceeded threshold " + format(config.maxErrorRate()));
        }
        if (!scaleUpReasons.isEmpty()) {
            if (signal.currentCapacity() == signal.maxCapacity()) {
                return recommendation(signal, AutoscalingAction.HOLD, signal.currentCapacity(),
                        severity(scaleUpReasons),
                        "Holding capacity at max capacity " + signal.maxCapacity()
                                + " despite scale-up pressure: " + String.join("; ", scaleUpReasons) + ".");
            }
            int recommendedCapacity = Math.min(signal.maxCapacity(),
                    signal.currentCapacity() + config.scaleUpStep());
            return recommendation(signal, AutoscalingAction.SCALE_UP, recommendedCapacity,
                    severity(scaleUpReasons),
                    "Recommending scale up due to scale-up pressure: "
                            + String.join("; ", scaleUpReasons) + ".");
        }

        if (signal.observedErrorRate() > config.maxErrorRate()) {
            return recommendation(signal, AutoscalingAction.INVESTIGATE, signal.currentCapacity(), 0.50,
                    "Investigating error rate " + format(signal.observedErrorRate())
                            + " above threshold " + format(config.maxErrorRate())
                            + " without scaling pressure.");
        }

        if (signal.utilization() <= config.utilizationScaleDownThreshold()
                && signal.queueDepth() > 0) {
            return recommendation(signal, AutoscalingAction.HOLD, signal.currentCapacity(), 0.10,
                    "Holding capacity because queue depth " + signal.queueDepth()
                            + " is non-empty; avoiding scale down despite low utilization "
                            + format(signal.utilization()) + ".");
        }

        if (signal.utilization() <= config.utilizationScaleDownThreshold()
                && signal.queueDepth() == 0
                && signal.observedP95LatencyMillis() <= config.targetP95LatencyMillis()
                && signal.observedP99LatencyMillis() <= config.targetP99LatencyMillis()) {
            if (signal.currentCapacity() == signal.minCapacity()) {
                return recommendation(signal, AutoscalingAction.HOLD, signal.currentCapacity(), 0.0,
                        "Holding capacity at min capacity " + signal.minCapacity()
                                + " despite low utilization " + format(signal.utilization()) + ".");
            }
            int recommendedCapacity = Math.max(signal.minCapacity(),
                    signal.currentCapacity() - config.scaleDownStep());
            return recommendation(signal, AutoscalingAction.SCALE_DOWN, recommendedCapacity, 0.25,
                    "Recommending scale down because low utilization "
                            + format(signal.utilization()) + " is at or below threshold "
                            + format(config.utilizationScaleDownThreshold())
                            + " and latency, error, and queue signals are healthy.");
        }

        return recommendation(signal, AutoscalingAction.HOLD, signal.currentCapacity(), 0.0,
                "Holding capacity because latency, error, utilization, and queue signals are healthy.");
    }

    private List<String> scaleUpReasons(AutoscalingSignal signal, ShadowAutoscalerConfig config) {
        List<String> reasons = new ArrayList<>();
        if (signal.queueDepth() > config.queueScaleUpThreshold()) {
            reasons.add("queue depth " + signal.queueDepth()
                    + " exceeded threshold " + config.queueScaleUpThreshold());
        }
        if (signal.observedP95LatencyMillis() > config.targetP95LatencyMillis()) {
            reasons.add("p95 latency " + format(signal.observedP95LatencyMillis())
                    + "ms exceeded target " + format(config.targetP95LatencyMillis()) + "ms");
        }
        if (signal.observedP99LatencyMillis() > config.targetP99LatencyMillis()) {
            reasons.add("p99 latency " + format(signal.observedP99LatencyMillis())
                    + "ms exceeded target " + format(config.targetP99LatencyMillis()) + "ms");
        }
        if (signal.utilization() >= config.utilizationScaleUpThreshold()) {
            reasons.add("utilization " + format(signal.utilization())
                    + " reached threshold " + format(config.utilizationScaleUpThreshold()));
        }
        return reasons;
    }

    private AutoscalingRecommendation recommendation(AutoscalingSignal signal,
                                                    AutoscalingAction action,
                                                    int recommendedCapacity,
                                                    double severityScore,
                                                    String reason) {
        return new AutoscalingRecommendation(signal.targetId(), action, signal.currentCapacity(),
                recommendedCapacity, signal.minCapacity(), signal.maxCapacity(), severityScore, reason,
                signal.timestamp(), signal.currentInFlightRequestCount(), signal.queueDepth(),
                signal.utilization(), signal.observedP95LatencyMillis(), signal.observedP99LatencyMillis(),
                signal.observedErrorRate(), signal.sampleSize());
    }

    private double severity(List<String> activeSignals) {
        return Math.min(1.0, activeSignals.size() * 0.25);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
