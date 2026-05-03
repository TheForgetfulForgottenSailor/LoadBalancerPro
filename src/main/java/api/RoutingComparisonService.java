package api;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import core.NetworkAwarenessSignal;
import core.RoutingComparisonEngine;
import core.RoutingComparisonReport;
import core.RoutingComparisonResult;
import core.RoutingDecision;
import core.RoutingDecisionExplanation;
import core.RoutingStrategyId;
import core.RoutingStrategyRegistry;
import core.ServerStateVector;
import org.springframework.stereotype.Service;

@Service
public class RoutingComparisonService {
    private final RoutingStrategyRegistry registry;
    private final RoutingComparisonEngine engine;
    private final Clock clock;

    public RoutingComparisonService() {
        this(RoutingStrategyRegistry.defaultRegistry(), Clock.systemUTC());
    }

    private RoutingComparisonService(RoutingStrategyRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.engine = new RoutingComparisonEngine(registry, clock);
    }

    public RoutingComparisonResponse compare(RoutingComparisonRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Instant timestamp = Instant.now(clock);
        List<RoutingStrategyId> strategyIds = resolveStrategies(request.strategies());
        List<ServerStateVector> candidates = toCandidates(request.servers(), timestamp);
        RoutingComparisonReport report = engine.compare(candidates, strategyIds);
        return toResponse(report);
    }

    private List<RoutingStrategyId> resolveStrategies(List<String> requestedStrategies) {
        if (requestedStrategies == null || requestedStrategies.isEmpty()) {
            return registry.registeredIds();
        }

        Set<RoutingStrategyId> strategyIds = new LinkedHashSet<>();
        for (String requestedStrategy : requestedStrategies) {
            if (requestedStrategy == null || requestedStrategy.isBlank()) {
                throw new IllegalArgumentException("strategies cannot contain blank values");
            }
            RoutingStrategyId strategyId = RoutingStrategyId.fromName(requestedStrategy)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported routing strategy: " + requestedStrategy.trim()));
            if (registry.find(strategyId).isEmpty()) {
                throw new IllegalArgumentException("Routing strategy is not registered: " + strategyId);
            }
            if (!strategyIds.add(strategyId)) {
                throw new IllegalArgumentException("strategies cannot contain duplicate values: " + strategyId);
            }
        }
        return List.copyOf(strategyIds);
    }

    private List<ServerStateVector> toCandidates(List<RoutingServerStateInput> servers, Instant timestamp) {
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("servers must contain at least one server");
        }

        Set<String> serverIds = new LinkedHashSet<>();
        List<ServerStateVector> candidates = new ArrayList<>();
        for (RoutingServerStateInput input : servers) {
            if (input == null) {
                throw new IllegalArgumentException("server input cannot be null");
            }
            String serverId = requireNonBlank(input.serverId(), "serverId");
            if (!serverIds.add(serverId)) {
                throw new IllegalArgumentException("serverId must be unique: " + serverId);
            }

            int inFlightRequestCount = requireNonNegativeInteger(
                    input.inFlightRequestCount(), "inFlightRequestCount");
            double averageLatencyMillis = requireFiniteNonNegative(
                    input.averageLatencyMillis(), "averageLatencyMillis");
            double p95LatencyMillis = requireFiniteNonNegative(input.p95LatencyMillis(), "p95LatencyMillis");
            double p99LatencyMillis = requireFiniteNonNegative(input.p99LatencyMillis(), "p99LatencyMillis");
            validateLatencyOrdering(averageLatencyMillis, p95LatencyMillis, p99LatencyMillis);

            candidates.add(new ServerStateVector(
                    serverId,
                    requirePresent(input.healthy(), "healthy"),
                    inFlightRequestCount,
                    optionalFiniteNonNegative(input.configuredCapacity(), "configuredCapacity"),
                    optionalFinitePositive(input.estimatedConcurrencyLimit(), "estimatedConcurrencyLimit"),
                    optionalWeight(input.weight(), "weight"),
                    averageLatencyMillis,
                    p95LatencyMillis,
                    p99LatencyMillis,
                    requireRate(input.recentErrorRate(), "recentErrorRate"),
                    optionalNonNegativeInteger(input.queueDepth(), "queueDepth"),
                    toNetworkAwarenessSignal(serverId, input.networkAwareness(), timestamp),
                    timestamp));
        }
        return List.copyOf(candidates);
    }

    private NetworkAwarenessSignal toNetworkAwarenessSignal(String serverId,
                                                            NetworkAwarenessInput input,
                                                            Instant timestamp) {
        if (input == null) {
            return NetworkAwarenessSignal.neutral(serverId, timestamp);
        }
        return new NetworkAwarenessSignal(
                serverId,
                optionalRate(input.timeoutRate(), "networkAwareness.timeoutRate"),
                optionalRate(input.retryRate(), "networkAwareness.retryRate"),
                optionalRate(input.connectionFailureRate(), "networkAwareness.connectionFailureRate"),
                optionalFiniteNonNegativeValue(
                        input.latencyJitterMillis(), "networkAwareness.latencyJitterMillis"),
                input.recentErrorBurst() != null && input.recentErrorBurst(),
                optionalNonNegativeIntegerValue(
                        input.requestTimeoutCount(), "networkAwareness.requestTimeoutCount"),
                optionalNonNegativeIntegerValue(input.sampleSize(), "networkAwareness.sampleSize"),
                timestamp);
    }

    private RoutingComparisonResponse toResponse(RoutingComparisonReport report) {
        return new RoutingComparisonResponse(
                report.requestedStrategies().stream().map(RoutingStrategyId::externalName).toList(),
                report.candidateCount(),
                report.timestamp(),
                report.results().stream().map(this::toResultResponse).toList());
    }

    private RoutingComparisonResultResponse toResultResponse(RoutingComparisonResult result) {
        return result.decision()
                .map(decision -> successfulResultResponse(result, decision))
                .orElseGet(() -> new RoutingComparisonResultResponse(
                        result.strategyId().externalName(),
                        result.status().name(),
                        null,
                        result.reason(),
                        List.of(),
                        Map.of()));
    }

    private RoutingComparisonResultResponse successfulResultResponse(
            RoutingComparisonResult result, RoutingDecision decision) {
        RoutingDecisionExplanation explanation = decision.explanation();
        return new RoutingComparisonResultResponse(
                result.strategyId().externalName(),
                result.status().name(),
                explanation.chosenServerId().orElse(null),
                result.reason(),
                explanation.candidateServersConsidered(),
                explanation.scores());
    }

    private static void validateLatencyOrdering(double averageLatencyMillis,
                                                double p95LatencyMillis,
                                                double p99LatencyMillis) {
        if (averageLatencyMillis > p95LatencyMillis) {
            throw new IllegalArgumentException(
                    "averageLatencyMillis must be less than or equal to p95LatencyMillis");
        }
        if (p95LatencyMillis > p99LatencyMillis) {
            throw new IllegalArgumentException(
                    "p95LatencyMillis must be less than or equal to p99LatencyMillis");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    private static <T> T requirePresent(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static int requireNonNegativeInteger(Integer value, String fieldName) {
        return validateNonNegativeInteger(requirePresent(value, fieldName), fieldName);
    }

    private static OptionalInt optionalNonNegativeInteger(Integer value, String fieldName) {
        if (value == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(validateNonNegativeInteger(value, fieldName));
    }

    private static int optionalNonNegativeIntegerValue(Integer value, String fieldName) {
        if (value == null) {
            return 0;
        }
        return validateNonNegativeInteger(value, fieldName);
    }

    private static int validateNonNegativeInteger(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        return value;
    }

    private static double requireFiniteNonNegative(Double value, String fieldName) {
        return validateFiniteNonNegative(requirePresent(value, fieldName), fieldName);
    }

    private static OptionalDouble optionalFiniteNonNegative(Double value, String fieldName) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(validateFiniteNonNegative(value, fieldName));
    }

    private static double optionalFiniteNonNegativeValue(Double value, String fieldName) {
        if (value == null) {
            return 0.0;
        }
        return validateFiniteNonNegative(value, fieldName);
    }

    private static double validateFiniteNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
        return value;
    }

    private static OptionalDouble optionalFinitePositive(Double value, String fieldName) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and positive");
        }
        return OptionalDouble.of(value);
    }

    private static double optionalWeight(Double value, String fieldName) {
        if (value == null || value == 0.0) {
            return 1.0;
        }
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
        return value;
    }

    private static double requireRate(Double value, String fieldName) {
        return validateRate(requirePresent(value, fieldName), fieldName);
    }

    private static double optionalRate(Double value, String fieldName) {
        if (value == null) {
            return 0.0;
        }
        return validateRate(value, fieldName);
    }

    private static double validateRate(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return value;
    }
}
