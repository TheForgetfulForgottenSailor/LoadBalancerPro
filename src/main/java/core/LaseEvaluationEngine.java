package core;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LaseEvaluationEngine {
    private final TailLatencyPowerOfTwoStrategy routingStrategy;
    private final LoadSheddingPolicy loadSheddingPolicy;
    private final ShadowAutoscaler shadowAutoscaler;
    private final FailureScenarioRunner failureScenarioRunner;
    private final Clock clock;

    public LaseEvaluationEngine() {
        this(new TailLatencyPowerOfTwoStrategy(), new LoadSheddingPolicy(), new ShadowAutoscaler(),
                new FailureScenarioRunner(), Clock.systemUTC());
    }

    public LaseEvaluationEngine(TailLatencyPowerOfTwoStrategy routingStrategy,
                                LoadSheddingPolicy loadSheddingPolicy,
                                ShadowAutoscaler shadowAutoscaler,
                                FailureScenarioRunner failureScenarioRunner,
                                Clock clock) {
        this.routingStrategy = Objects.requireNonNull(routingStrategy, "routingStrategy cannot be null");
        this.loadSheddingPolicy = Objects.requireNonNull(loadSheddingPolicy, "loadSheddingPolicy cannot be null");
        this.shadowAutoscaler = Objects.requireNonNull(shadowAutoscaler, "shadowAutoscaler cannot be null");
        this.failureScenarioRunner = Objects.requireNonNull(failureScenarioRunner, "failureScenarioRunner cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public LaseEvaluationReport evaluate(LaseEvaluationInput input, LaseEvaluationConfig config) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        RoutingDecision routingDecision = routingStrategy.choose(input.serverCandidates());
        ConcurrencyLimitDecision concurrencyDecision = new AdaptiveConcurrencyLimiter(
                config.adaptiveConcurrencyConfig(), clock)
                .calculateNextLimit(input.currentConcurrencyLimit(), input.concurrencyFeedback());
        LoadSheddingDecision loadSheddingDecision = loadSheddingPolicy.decide(input.requestPriority(),
                input.loadSheddingSignal(), config.loadSheddingConfig());
        AutoscalingRecommendation autoscalingRecommendation = shadowAutoscaler.recommend(
                input.autoscalingSignal(), config.shadowAutoscalerConfig());
        FailureScenarioResult failureScenarioResult = failureScenarioRunner.evaluate(
                input.failureScenarioSignal(), config.failureScenarioConfig());

        return new LaseEvaluationReport(input.evaluationId(), routingDecision, concurrencyDecision,
                loadSheddingDecision, autoscalingRecommendation, failureScenarioResult,
                summary(input, routingDecision, concurrencyDecision, loadSheddingDecision,
                        autoscalingRecommendation, failureScenarioResult),
                Instant.now(clock));
    }

    private String summary(LaseEvaluationInput input,
                           RoutingDecision routingDecision,
                           ConcurrencyLimitDecision concurrencyDecision,
                           LoadSheddingDecision loadSheddingDecision,
                           AutoscalingRecommendation autoscalingRecommendation,
                           FailureScenarioResult failureScenarioResult) {
        String chosenServer = routingDecision.chosenServer()
                .map(ServerStateVector::serverId)
                .orElse("no eligible server");
        String failureActions = failureScenarioResult.recommendations().stream()
                .map(MitigationAction::name)
                .collect(Collectors.joining(", "));
        return "Evaluation " + input.evaluationId()
                + ": chosen server " + chosenServer
                + " (" + routingDecision.explanation().reason() + ")"
                + "; concurrency " + concurrencyDecision.action()
                + " " + concurrencyDecision.previousLimit() + "->" + concurrencyDecision.nextLimit()
                + " (" + concurrencyDecision.reason() + ")"
                + "; load shedding " + loadSheddingDecision.action() + " for " + input.requestPriority()
                + " (" + loadSheddingDecision.reason() + ")"
                + "; autoscaling " + autoscalingRecommendation.action()
                + " " + autoscalingRecommendation.currentCapacity()
                + "->" + autoscalingRecommendation.recommendedCapacity()
                + " (" + autoscalingRecommendation.reason() + ")"
                + "; failure severity " + failureScenarioResult.severity()
                + " with actions [" + failureActions + "]"
                + " (" + failureScenarioResult.reason() + ").";
    }
}
