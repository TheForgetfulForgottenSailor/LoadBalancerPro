package com.richmond423.loadbalancerpro.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LaseDemoScenarioTest {
    private static final Instant NOW = Instant.parse("2026-04-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final LaseDemoScenarioFactory factory = new LaseDemoScenarioFactory(CLOCK);

    @Test
    void factoryCreatesAllRequiredScenarioTypes() {
        List<LaseDemoScenario> scenarios = factory.createAll();

        Set<LaseDemoScenarioType> scenarioTypes = EnumSet.noneOf(LaseDemoScenarioType.class);
        for (LaseDemoScenario scenario : scenarios) {
            scenarioTypes.add(scenario.type());
        }

        assertEquals(EnumSet.allOf(LaseDemoScenarioType.class), scenarioTypes);
    }

    @Test
    void eachScenarioHasDescriptionInputConfigAndTimestamp() {
        for (LaseDemoScenario scenario : factory.createAll()) {
            assertFalse(scenario.scenarioId().isBlank());
            assertFalse(scenario.description().isBlank());
            assertNotNull(scenario.input());
            assertNotNull(scenario.config());
            assertEquals(NOW, scenario.timestamp());
        }
    }

    @Test
    void scenarioValidationRejectsMissingFields() {
        LaseDemoScenario valid = factory.create(LaseDemoScenarioType.HEALTHY);

        assertAll("invalid scenario",
                () -> assertInvalid(() -> new LaseDemoScenario(null, valid.scenarioId(), valid.description(),
                        valid.input(), valid.config(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseDemoScenario(valid.type(), " ", valid.description(),
                        valid.input(), valid.config(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseDemoScenario(valid.type(), valid.scenarioId(), " ",
                        valid.input(), valid.config(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseDemoScenario(valid.type(), valid.scenarioId(), valid.description(),
                        null, valid.config(), valid.timestamp())),
                () -> assertInvalid(() -> new LaseDemoScenario(valid.type(), valid.scenarioId(), valid.description(),
                        valid.input(), null, valid.timestamp())),
                () -> assertInvalid(() -> new LaseDemoScenario(valid.type(), valid.scenarioId(), valid.description(),
                        valid.input(), valid.config(), null))
        );
    }

    @Test
    void factoryOutputIsDeterministic() {
        LaseDemoScenarioFactory anotherFactory = new LaseDemoScenarioFactory(CLOCK);

        assertEquals(factory.createAll(), anotherFactory.createAll());
        assertEquals(factory.create(LaseDemoScenarioType.OVERLOADED),
                anotherFactory.create(LaseDemoScenarioType.OVERLOADED));
    }

    @Test
    void healthyScenarioProducesNormalLowRiskReport() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.HEALTHY), 7);

        assertTrue(report.routingDecision().chosenServer().isPresent());
        assertEquals(LoadSheddingDecision.Action.ALLOW, report.loadSheddingDecision().action());
        assertEquals(AutoscalingAction.HOLD, report.autoscalingRecommendation().action());
        assertEquals(FailureSeverity.LOW, report.failureScenarioResult().severity());
        assertEquals(List.of(MitigationAction.HOLD), report.failureScenarioResult().recommendations());
    }

    @Test
    void overloadedScenarioProducesSheddingScaleUpAndFailurePressure() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.OVERLOADED), 7);

        assertTrue(report.concurrencyDecision().nextLimit() <= report.concurrencyDecision().previousLimit());
        assertEquals(LoadSheddingDecision.Action.SHED, report.loadSheddingDecision().action());
        assertEquals(AutoscalingAction.SCALE_UP, report.autoscalingRecommendation().action());
        assertTrue(report.failureScenarioResult().severity() == FailureSeverity.HIGH
                || report.failureScenarioResult().severity() == FailureSeverity.CRITICAL);
        assertTrue(report.failureScenarioResult().recommendations().contains(MitigationAction.SHED_LOW_PRIORITY));
    }

    @Test
    void errorStormScenarioInvestigatesInsteadOfBlindScaleUp() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.ERROR_STORM), 7);

        assertEquals(AutoscalingAction.INVESTIGATE, report.autoscalingRecommendation().action());
        assertEquals(report.autoscalingRecommendation().currentCapacity(),
                report.autoscalingRecommendation().recommendedCapacity());
        assertTrue(report.failureScenarioResult().recommendations().contains(MitigationAction.INVESTIGATE));
    }

    @Test
    void partialOutageScenarioProducesCriticalFailureMitigation() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.PARTIAL_OUTAGE), 7);

        assertEquals(FailureSeverity.CRITICAL, report.failureScenarioResult().severity());
        assertTrue(report.failureScenarioResult().recommendations().contains(MitigationAction.ROUTE_AROUND));
        assertTrue(report.failureScenarioResult().recommendations().contains(MitigationAction.INVESTIGATE));
    }

    @Test
    void lowSampleScenarioProducesConservativeHoldAndLowSeverity() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.LOW_SAMPLE), 7);

        assertEquals(ConcurrencyLimitDecision.Action.HOLD, report.concurrencyDecision().action());
        assertEquals(AutoscalingAction.HOLD, report.autoscalingRecommendation().action());
        assertEquals(FailureSeverity.LOW, report.failureScenarioResult().severity());
        assertEquals(List.of(MitigationAction.HOLD), report.failureScenarioResult().recommendations());
    }

    @Test
    void formatterOutputIncludesRequiredSections() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.OVERLOADED), 7);

        String formatted = new LaseEvaluationReportFormatter().format(report);

        assertFalse(formatted.isBlank());
        assertTrue(formatted.contains("Evaluation ID:"));
        assertTrue(formatted.contains("Routing Decision:"));
        assertTrue(formatted.contains("Adaptive Concurrency:"));
        assertTrue(formatted.contains("Load Shedding:"));
        assertTrue(formatted.contains("Shadow Autoscaling:"));
        assertTrue(formatted.contains("Failure Scenario:"));
        assertTrue(formatted.contains("Summary:"));
        assertTrue(formatted.contains("SHED_LOW_PRIORITY"));
    }

    @Test
    void formatterOutputAvoidsObviousSecretTermsAndObjectDumps() {
        LaseEvaluationReport report = evaluate(factory.create(LaseDemoScenarioType.ERROR_STORM), 7);

        String formatted = new LaseEvaluationReportFormatter().format(report);
        String lower = formatted.toLowerCase();

        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("secret"));
        assertFalse(lower.contains("credential"));
        assertFalse(lower.contains("accesskey"));
        assertFalse(lower.contains("token"));
        assertFalse(formatted.contains("LaseEvaluationReport["));
        assertFalse(formatted.contains("AutoscalingRecommendation["));
    }

    @Test
    void formatterRejectsNullReport() {
        assertInvalid(() -> new LaseEvaluationReportFormatter().format(null));
    }

    private LaseEvaluationReport evaluate(LaseDemoScenario scenario, long seed) {
        LaseEvaluationEngine engine = new LaseEvaluationEngine(
                new TailLatencyPowerOfTwoStrategy(new ServerScoreCalculator(), new Random(seed), CLOCK),
                new LoadSheddingPolicy(),
                new ShadowAutoscaler(),
                new FailureScenarioRunner(),
                CLOCK);
        return engine.evaluate(scenario.input(), scenario.config());
    }

    private void assertInvalid(Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable);
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }
}
