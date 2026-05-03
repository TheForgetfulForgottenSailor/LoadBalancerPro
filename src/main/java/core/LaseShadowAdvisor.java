package core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public final class LaseShadowAdvisor {
    public static final String ENABLED_PROPERTY = "loadbalancerpro.lase.shadow.enabled";
    public static final String ENABLED_ENVIRONMENT_VARIABLE = "LOADBALANCERPRO_LASE_SHADOW_ENABLED";

    private static final Logger logger = LogManager.getLogger(LaseShadowAdvisor.class);
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\r\\n\\t]+");
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+\\-/]+=*");
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)\\b(api[-_ ]?key|access[-_ ]?key|secret|token|password|credential|bearer[-_ ]?secret)\\b\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern SENSITIVE_WORD_VALUE = Pattern.compile(
            "(?i)\\b(api[-_ ]?key|access[-_ ]?key|secret|token|password|credential|bearer[-_ ]?secret)[-_][A-Za-z0-9._~+\\-/=]+");

    private final boolean enabled;
    private final BiFunction<LaseEvaluationInput, LaseEvaluationConfig, LaseEvaluationReport> evaluator;
    private final Clock clock;
    private final LaseShadowEventLog eventLog;
    private volatile LaseEvaluationReport lastReport;

    public LaseShadowAdvisor(boolean enabled) {
        this(enabled, defaultEngine(Clock.systemUTC()), Clock.systemUTC(), new LaseShadowEventLog());
    }

    public LaseShadowAdvisor(boolean enabled, LaseShadowEventLog eventLog) {
        this(enabled, defaultEngine(Clock.systemUTC()), Clock.systemUTC(), eventLog);
    }

    public LaseShadowAdvisor(boolean enabled, LaseEvaluationEngine engine, Clock clock) {
        this(enabled, engine, clock, new LaseShadowEventLog());
    }

    public LaseShadowAdvisor(boolean enabled,
                             LaseEvaluationEngine engine,
                             Clock clock,
                             LaseShadowEventLog eventLog) {
        this(enabled, Objects.requireNonNull(engine, "engine cannot be null")::evaluate, clock, eventLog);
    }

    LaseShadowAdvisor(boolean enabled,
                      BiFunction<LaseEvaluationInput, LaseEvaluationConfig, LaseEvaluationReport> evaluator,
                      Clock clock) {
        this(enabled, evaluator, clock, new LaseShadowEventLog());
    }

    LaseShadowAdvisor(boolean enabled,
                      BiFunction<LaseEvaluationInput, LaseEvaluationConfig, LaseEvaluationReport> evaluator,
                      Clock clock,
                      LaseShadowEventLog eventLog) {
        this.enabled = enabled;
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog cannot be null");
    }

    public static LaseShadowAdvisor disabled() {
        return new LaseShadowAdvisor(false);
    }

    public static LaseShadowAdvisor fromSystemProperties() {
        String configured = System.getProperty(ENABLED_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENABLED_ENVIRONMENT_VARIABLE);
        }
        return new LaseShadowAdvisor(Boolean.parseBoolean(configured));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<LaseEvaluationReport> lastReport() {
        return Optional.ofNullable(lastReport);
    }

    public LaseShadowObservabilitySnapshot observabilitySnapshot() {
        return eventLog.snapshot();
    }

    public Optional<LaseEvaluationReport> observe(String strategyName,
                                                  List<Server> currentServers,
                                                  double requestedLoad,
                                                  LoadDistributionResult distributionResult) {
        if (!enabled) {
            return Optional.empty();
        }
        if (currentServers == null || currentServers.isEmpty()) {
            logger.debug("LASE shadow advisor skipped evaluation because no servers were available.");
            return Optional.empty();
        }
        if (distributionResult == null) {
            logger.debug("LASE shadow advisor skipped evaluation because no distribution result was available.");
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        try {
            LaseEvaluationInput input = buildInput(strategyName, currentServers, requestedLoad,
                    distributionResult, now);
            LaseEvaluationReport report = evaluator.apply(input, defaultConfig());
            lastReport = report;
            recordSuccess(strategyName, requestedLoad, distributionResult, report);
            logger.debug("LASE shadow report {}: {}", report.evaluationId(), report.summary());
            return Optional.of(report);
        } catch (RuntimeException e) {
            String failureReason = safeFailureReason(e);
            recordFailSafe(strategyName, requestedLoad, distributionResult, now, failureReason);
            logger.warn("LASE shadow advisor skipped evaluation: {}", failureReason);
            return Optional.empty();
        }
    }

    private void recordSuccess(String strategyName,
                               double requestedLoad,
                               LoadDistributionResult distributionResult,
                               LaseEvaluationReport report) {
        String actualServerId = actualSelectedServerId(distributionResult);
        String recommendedServerId = recommendedServerId(report);
        Boolean agreed = actualServerId != null && recommendedServerId != null
                ? actualServerId.equals(recommendedServerId)
                : null;
        Double decisionScore = recommendedServerId == null
                ? null
                : report.routingDecision().explanation().scores().get(recommendedServerId);
        NetworkAwarenessSignal networkAwarenessSignal = recommendedNetworkSignal(report, recommendedServerId);

        eventLog.record(new LaseShadowEvent(
                report.evaluationId(),
                report.timestamp(),
                safeStrategyName(strategyName),
                sanitizeNonNegative(requestedLoad),
                distributionResult.unallocatedLoad(),
                actualServerId,
                recommendedServerId,
                report.autoscalingRecommendation().action().name(),
                decisionScore,
                networkAwarenessSignal,
                new ServerScoreCalculator().networkRiskScore(networkAwarenessSignal),
                report.summary(),
                agreed,
                false,
                null));
    }

    private void recordFailSafe(String strategyName,
                                double requestedLoad,
                                LoadDistributionResult distributionResult,
                                Instant timestamp,
                                String failureReason) {
        eventLog.record(new LaseShadowEvent(
                evaluationId(strategyName),
                timestamp,
                safeStrategyName(strategyName),
                sanitizeNonNegative(requestedLoad),
                distributionResult == null ? 0.0 : distributionResult.unallocatedLoad(),
                distributionResult == null ? null : actualSelectedServerId(distributionResult),
                null,
                "FAIL_SAFE",
                null,
                NetworkAwarenessSignal.neutral(evaluationId(strategyName), timestamp),
                0.0,
                "LASE shadow evaluation failed safely",
                null,
                true,
                failureReason));
    }

    private String actualSelectedServerId(LoadDistributionResult distributionResult) {
        return distributionResult.allocations().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String recommendedServerId(LaseEvaluationReport report) {
        return report.routingDecision().explanation().chosenServerId()
                .or(() -> report.routingDecision().chosenServer().map(ServerStateVector::serverId))
                .orElse(null);
    }

    private NetworkAwarenessSignal recommendedNetworkSignal(LaseEvaluationReport report, String recommendedServerId) {
        if (recommendedServerId == null) {
            return NetworkAwarenessSignal.neutral(report.evaluationId(), report.timestamp());
        }
        return report.routingDecision().chosenServer()
                .filter(server -> recommendedServerId.equals(server.serverId()))
                .map(ServerStateVector::networkAwarenessSignal)
                .orElseGet(() -> NetworkAwarenessSignal.neutral(recommendedServerId, report.timestamp()));
    }

    private String safeStrategyName(String strategyName) {
        return strategyName == null || strategyName.isBlank() ? "UNKNOWN" : strategyName.trim();
    }

    private double sanitizeNonNegative(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    static String safeFailureReason(Throwable exception) {
        if (exception == null) {
            return "shadow evaluation failed safely";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "shadow evaluation failed safely";
        }
        String sanitized = CONTROL_CHARACTERS.matcher(message).replaceAll(" ").trim();
        sanitized = BEARER_VALUE.matcher(sanitized).replaceAll("Bearer [redacted]");
        sanitized = SENSITIVE_KEY_VALUE.matcher(sanitized).replaceAll("$1=[redacted]");
        sanitized = SENSITIVE_WORD_VALUE.matcher(sanitized).replaceAll("$1-[redacted]");
        return sanitized.isBlank() ? "shadow evaluation failed safely" : sanitized;
    }

    private LaseEvaluationInput buildInput(String strategyName,
                                           List<Server> currentServers,
                                           double requestedLoad,
                                           LoadDistributionResult distributionResult,
                                           Instant now) {
        List<Server> serverSnapshot = List.copyOf(currentServers);
        int queueDepth = toCount(distributionResult.unallocatedLoad());
        List<ServerStateVector> stateVectors = serverSnapshot.stream()
                .filter(Objects::nonNull)
                .map(server -> toStateVector(server, distributionResult.allocations(), queueDepth, now))
                .toList();
        if (stateVectors.isEmpty()) {
            throw new IllegalArgumentException("currentServers must include at least one non-null server");
        }

        int currentConcurrencyLimit = currentConcurrencyLimit(serverSnapshot);
        int currentInFlight = toCount(distributionResult.allocations().values().stream()
                .mapToDouble(Double::doubleValue)
                .sum());
        int sampleSize = Math.max(1, toCount(requestedLoad));
        double p95Latency = stateVectors.stream().mapToDouble(ServerStateVector::p95LatencyMillis)
                .average().orElse(100.0);
        double p99Latency = stateVectors.stream().mapToDouble(ServerStateVector::p99LatencyMillis)
                .average().orElse(150.0);
        double averageLatency = stateVectors.stream().mapToDouble(ServerStateVector::averageLatencyMillis)
                .average().orElse(80.0);
        double errorRate = stateVectors.stream().mapToDouble(ServerStateVector::recentErrorRate)
                .average().orElse(0.0);
        String targetId = "loadbalancer-shadow";

        return new LaseEvaluationInput(
                evaluationId(strategyName),
                RequestPriority.USER,
                stateVectors,
                currentConcurrencyLimit,
                new ConcurrencyFeedback(targetId, currentInFlight, averageLatency, p95Latency, p99Latency,
                        errorRate, sampleSize, now),
                new LoadSheddingSignal(targetId, currentInFlight, currentConcurrencyLimit, queueDepth, p95Latency,
                        errorRate, now),
                new AutoscalingSignal(targetId, stateVectors.size(), 1, Math.max(stateVectors.size() + 3, 2),
                        currentInFlight, queueDepth, p95Latency, p99Latency, errorRate, sampleSize, now),
                new FailureScenarioSignal(evaluationId(strategyName) + "-scenario",
                        scenarioType(stateVectors, currentInFlight, currentConcurrencyLimit, queueDepth),
                        targetId, stateVectors.size(), healthyCount(stateVectors), currentInFlight,
                        currentConcurrencyLimit, queueDepth, p95Latency, p99Latency, errorRate, sampleSize, now),
                now);
    }

    private ServerStateVector toStateVector(Server server,
                                            Map<String, Double> allocations,
                                            int queueDepth,
                                            Instant now) {
        double loadScore = Math.max(0.0, server.getLoadScore());
        double averageLatency = 50.0 + loadScore;
        double p95Latency = averageLatency + 40.0 + queueDepth * 0.5;
        double p99Latency = p95Latency + 60.0;
        double errorRate = server.isHealthy() ? Math.min(0.10, loadScore / 1000.0) : 0.30;
        return ServerStateVector.fromServer(server, toCount(allocations.getOrDefault(server.getServerId(), 0.0)),
                averageLatency, p95Latency, p99Latency, errorRate, queueDepth, now);
    }

    private int currentConcurrencyLimit(List<Server> servers) {
        double configuredCapacity = servers.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Server::getCapacity)
                .sum();
        double fallbackCapacity = Math.max(1, servers.size()) * 10.0;
        return Math.max(1, Math.min(100, toCount(configuredCapacity > 0.0 ? configuredCapacity : fallbackCapacity)));
    }

    private int healthyCount(List<ServerStateVector> stateVectors) {
        return (int) stateVectors.stream().filter(ServerStateVector::healthy).count();
    }

    private FailureScenarioType scenarioType(List<ServerStateVector> stateVectors,
                                             int currentInFlight,
                                             int currentConcurrencyLimit,
                                             int queueDepth) {
        double healthyRatio = healthyCount(stateVectors) / (double) stateVectors.size();
        double utilization = currentInFlight / (double) currentConcurrencyLimit;
        if (healthyRatio < 0.60) {
            return FailureScenarioType.PARTIAL_OUTAGE;
        }
        if (queueDepth > 20) {
            return FailureScenarioType.QUEUE_BACKLOG;
        }
        if (utilization >= 0.85) {
            return FailureScenarioType.CAPACITY_SATURATION;
        }
        return FailureScenarioType.TRAFFIC_SPIKE;
    }

    private String evaluationId(String strategyName) {
        String normalized = strategyName == null || strategyName.isBlank()
                ? "unknown"
                : strategyName.trim().toLowerCase(Locale.ROOT)
                        .replace('_', '-')
                        .replaceAll("[^a-z0-9-]", "-")
                        .replaceAll("-+", "-")
                        .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "unknown";
        }
        return "lase-shadow-" + normalized;
    }

    private int toCount(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(value));
    }

    private LaseEvaluationConfig defaultConfig() {
        return new LaseEvaluationConfig(
                new AdaptiveConcurrencyConfig(1, 100, 2, 0.5, 200.0, 0.10, 10),
                new LoadSheddingConfig(0.70, 0.90, 20, 250.0, 0.10, true, true),
                new ShadowAutoscalerConfig(200.0, 350.0, 0.10, 20, 0.85, 0.25, 2, 1, 10),
                new FailureScenarioConfig(20, 200.0, 350.0, 0.10, 0.85, 0.60, 10)
        );
    }

    private static LaseEvaluationEngine defaultEngine(Clock clock) {
        return new LaseEvaluationEngine(
                new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(), clock),
                new LoadSheddingPolicy(),
                new ShadowAutoscaler(),
                new FailureScenarioRunner(),
                clock);
    }
}
