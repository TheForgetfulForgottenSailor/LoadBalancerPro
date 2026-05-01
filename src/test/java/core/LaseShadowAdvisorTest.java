package core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LaseShadowAdvisorTest {
    private static final Instant NOW = Instant.parse("2026-04-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @AfterEach
    void clearFeatureFlag() {
        System.clearProperty(LaseShadowAdvisor.ENABLED_PROPERTY);
    }

    @Test
    void disabledAdvisorDoesNotEvaluateOrStoreReport() {
        AtomicInteger evaluations = new AtomicInteger();
        LaseShadowAdvisor advisor = new LaseShadowAdvisor(false, (input, config) -> {
            evaluations.incrementAndGet();
            throw new AssertionError("Disabled advisor must not evaluate LASE components.");
        }, CLOCK);

        Optional<LaseEvaluationReport> report = advisor.observe(
                "CAPACITY_AWARE", servers(), 40.0, new LoadDistributionResult(Map.of("S1", 20.0), 0.0));

        assertTrue(report.isEmpty());
        assertTrue(advisor.lastReport().isEmpty());
        assertEquals(0, evaluations.get());
    }

    @Test
    void enabledAdvisorProducesReportFromCurrentLoadBalancerState() {
        LaseShadowAdvisor advisor = deterministicAdvisor(true);

        Optional<LaseEvaluationReport> report = advisor.observe(
                "CAPACITY_AWARE", servers(), 60.0,
                new LoadDistributionResult(Map.of("S1", 25.0, "S2", 35.0), 0.0));

        assertTrue(report.isPresent());
        assertEquals("lase-shadow-capacity-aware", report.orElseThrow().evaluationId());
        assertNotNull(report.orElseThrow().routingDecision());
        assertNotNull(report.orElseThrow().concurrencyDecision());
        assertNotNull(report.orElseThrow().loadSheddingDecision());
        assertNotNull(report.orElseThrow().autoscalingRecommendation());
        assertNotNull(report.orElseThrow().failureScenarioResult());
        assertTrue(report.orElseThrow().summary().contains("Evaluation lase-shadow-capacity-aware"));
        assertEquals(report, advisor.lastReport());
    }

    @Test
    void enabledAdvisorRecordsSuccessfulShadowEvent() {
        LaseShadowEventLog eventLog = new LaseShadowEventLog(10);
        LaseShadowAdvisor advisor = deterministicAdvisor(true, eventLog);

        advisor.observe("CAPACITY_AWARE", servers(), 60.0,
                new LoadDistributionResult(Map.of("S1", 25.0, "S2", 35.0), 0.0));

        LaseShadowObservabilitySnapshot snapshot = eventLog.snapshot();

        assertEquals(1, snapshot.summary().totalEvaluations());
        assertEquals(0, snapshot.summary().failSafeCount());
        assertEquals(NOW, snapshot.summary().latestEventTimestamp());
        assertEquals(1, snapshot.recentEvents().size());
        LaseShadowEvent event = snapshot.recentEvents().get(0);
        assertEquals("lase-shadow-capacity-aware", event.evaluationId());
        assertEquals("CAPACITY_AWARE", event.strategy());
        assertEquals("S2", event.actualSelectedServerId());
        assertTrue(event.recommendedServerId() == null || !event.recommendedServerId().isBlank());
        assertTrue(event.decisionScore() == null || event.decisionScore() >= 0.0);
        assertEquals(0.0, event.networkAwarenessSignal().timeoutRate(), 0.0);
        assertEquals(0.0, event.networkAwarenessSignal().retryRate(), 0.0);
        assertEquals(0.0, event.networkAwarenessSignal().connectionFailureRate(), 0.0);
        assertEquals(0.0, event.networkAwarenessSignal().latencyJitterMillis(), 0.0);
        assertFalse(event.networkAwarenessSignal().recentErrorBurst());
        assertEquals(0, event.networkAwarenessSignal().requestTimeoutCount());
        assertEquals(0.0, event.networkRiskScore(), 0.0);
        if (event.agreedWithRouting() != null) {
            assertNotNull(event.actualSelectedServerId());
            assertNotNull(event.recommendedServerId());
        }
        assertFalse(event.failSafe());
        assertTrue(event.reason().contains("Evaluation lase-shadow-capacity-aware"));
    }

    @Test
    void advisorFailsSafelyWhenEvaluationThrows() {
        LaseShadowEventLog eventLog = new LaseShadowEventLog(10);
        LaseShadowAdvisor advisor = new LaseShadowAdvisor(true, (input, config) -> {
            throw new IllegalStateException("synthetic failure");
        }, CLOCK, eventLog);

        assertDoesNotThrow(() -> {
            Optional<LaseEvaluationReport> report = advisor.observe(
                    "CAPACITY_AWARE", servers(), 60.0,
                    new LoadDistributionResult(Map.of("S1", 25.0), 0.0));
            assertTrue(report.isEmpty());
        });
        assertTrue(advisor.lastReport().isEmpty());
        LaseShadowObservabilitySnapshot snapshot = eventLog.snapshot();
        assertEquals(1, snapshot.summary().totalEvaluations());
        assertEquals(1, snapshot.summary().failSafeCount());
        assertEquals("FAIL_SAFE", snapshot.recentEvents().get(0).recommendedAction());
        assertEquals(0.0, snapshot.recentEvents().get(0).networkAwarenessSignal().timeoutRate(), 0.0);
        assertEquals(0.0, snapshot.recentEvents().get(0).networkRiskScore(), 0.0);
        assertTrue(snapshot.recentEvents().get(0).failureReason().contains("synthetic failure"));
    }

    @Test
    void advisorRedactsSensitiveFailureReasonBeforeStoringShadowEvent() {
        LaseShadowEventLog eventLog = new LaseShadowEventLog(10);
        LaseShadowAdvisor advisor = new LaseShadowAdvisor(true, (input, config) -> {
            throw new IllegalStateException(
                    "token=raw-token api-key=raw-api-key Bearer raw-bearer-secret credential:raw-credential\nnext");
        }, CLOCK, eventLog);

        Optional<LaseEvaluationReport> report = advisor.observe(
                "CAPACITY_AWARE", servers(), 60.0,
                new LoadDistributionResult(Map.of("S1", 25.0), 0.0));

        assertTrue(report.isEmpty());
        String failureReason = eventLog.snapshot().recentEvents().get(0).failureReason();
        assertTrue(failureReason.contains("[redacted]"));
        assertFalse(failureReason.contains("raw-token"));
        assertFalse(failureReason.contains("raw-api-key"));
        assertFalse(failureReason.contains("raw-bearer-secret"));
        assertFalse(failureReason.contains("raw-credential"));
        assertFalse(failureReason.contains("\n"));
    }

    @Test
    void advisorFailsSafelyWhenDistributionResultIsMissing() {
        Optional<LaseEvaluationReport> report = deterministicAdvisor(true)
                .observe("CAPACITY_AWARE", servers(), 60.0, null);

        assertTrue(report.isEmpty());
    }

    @Test
    void advisorDoesNotMutateServersOrDistributionResult() {
        List<Server> servers = servers();
        LoadDistributionResult result = new LoadDistributionResult(Map.of("S1", 25.0, "S2", 35.0), 0.0);
        double originalCapacity = servers.get(0).getCapacity();
        boolean originalHealth = servers.get(1).isHealthy();

        deterministicAdvisor(true).observe("CAPACITY_AWARE", servers, 60.0, result);

        assertEquals(2, servers.size());
        assertEquals(originalCapacity, servers.get(0).getCapacity(), 0.01);
        assertEquals(originalHealth, servers.get(1).isHealthy());
        assertEquals(Map.of("S1", 25.0, "S2", 35.0), result.allocations());
        assertEquals(0.0, result.unallocatedLoad(), 0.01);
    }

    @Test
    void advisorIsDeterministicWithFixedClockAndSeededRoutingSampler() {
        LoadDistributionResult result = new LoadDistributionResult(Map.of("S1", 25.0, "S2", 35.0), 0.0);

        LaseEvaluationReport first = deterministicAdvisor(true).observe("CAPACITY_AWARE", servers(), 60.0, result)
                .orElseThrow();
        LaseEvaluationReport second = deterministicAdvisor(true).observe("CAPACITY_AWARE", servers(), 60.0, result)
                .orElseThrow();

        assertEquals(first, second);
    }

    @Test
    void featureFlagDefaultsToDisabledAndCanEnableAdvisor() {
        assertFalse(LaseShadowAdvisor.fromSystemProperties().isEnabled());

        System.setProperty(LaseShadowAdvisor.ENABLED_PROPERTY, "true");

        assertTrue(LaseShadowAdvisor.fromSystemProperties().isEnabled());
    }

    @Test
    void loadBalancerShadowObservationPreservesCapacityAwareRoutingAndDoesNotConstructCloudManager() {
        LoadBalancer baseline = balancerWithServers();
        LoadBalancer observed = balancerWithServers();
        observed.setLaseShadowAdvisorForTesting(deterministicAdvisor(true));
        try (MockedConstruction<CloudManager> mockedCloudManager = Mockito.mockConstruction(CloudManager.class)) {
            LoadDistributionResult expected = baseline.capacityAwareWithResult(60.0);

            LoadDistributionResult actual = observed.capacityAwareWithResult(60.0);

            assertEquals(expected.allocations(), actual.allocations());
            assertEquals(expected.unallocatedLoad(), actual.unallocatedLoad(), 0.01);
            assertTrue(observed.getLastLaseShadowReportForTesting().isPresent());
            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Shadow LASE observation must not construct CloudManager or call cloud paths.");
        } finally {
            baseline.shutdown();
            observed.shutdown();
        }
    }

    @Test
    void disabledLoadBalancerAdvisorLeavesNoShadowReport() {
        LoadBalancer balancer = balancerWithServers();
        balancer.setLaseShadowAdvisorForTesting(LaseShadowAdvisor.disabled());
        try {
            LoadDistributionResult result = balancer.capacityAwareWithResult(60.0);

            assertFalse(result.allocations().isEmpty());
            assertTrue(balancer.getLastLaseShadowReportForTesting().isEmpty());
        } finally {
            balancer.shutdown();
        }
    }

    private static LaseShadowAdvisor deterministicAdvisor(boolean enabled) {
        return deterministicAdvisor(enabled, new LaseShadowEventLog(10));
    }

    private static LaseShadowAdvisor deterministicAdvisor(boolean enabled, LaseShadowEventLog eventLog) {
        LaseEvaluationEngine engine = new LaseEvaluationEngine(
                new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(7), CLOCK),
                new LoadSheddingPolicy(),
                new ShadowAutoscaler(),
                new FailureScenarioRunner(),
                CLOCK);
        return new LaseShadowAdvisor(enabled, engine, CLOCK, eventLog);
    }

    private static LoadBalancer balancerWithServers() {
        LoadBalancer balancer = new LoadBalancer();
        for (Server server : servers()) {
            balancer.addServer(server);
        }
        return balancer;
    }

    private static List<Server> servers() {
        Server first = new Server("S1", 20.0, 20.0, 20.0);
        first.setCapacity(80.0);
        Server second = new Server("S2", 10.0, 10.0, 10.0);
        second.setCapacity(100.0);
        return List.of(first, second);
    }
}
