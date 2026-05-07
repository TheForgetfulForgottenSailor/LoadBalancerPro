package com.richmond423.loadbalancerpro.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class WeightedRoundRobinRoutingStrategyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final WeightedRoundRobinRoutingStrategy strategy =
            new WeightedRoundRobinRoutingStrategy(FIXED_CLOCK);

    @Test
    void emptyCandidateListReturnsSafeNoDecision() {
        RoutingDecision decision = strategy.choose(List.of());

        assertTrue(decision.chosenServer().isEmpty());
        assertEquals(WeightedRoundRobinRoutingStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertTrue(decision.explanation().candidateServersConsidered().isEmpty());
        assertTrue(decision.explanation().chosenServerId().isEmpty());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("No healthy eligible servers"));
        assertEquals(NOW, decision.explanation().timestamp());
    }

    @Test
    void allUnhealthyCandidatesReturnSafeNoDecision() {
        RoutingDecision decision = strategy.choose(List.of(
                state("red", false, 4.0),
                state("yellow", false, 1.0)));

        assertTrue(decision.chosenServer().isEmpty());
        assertEquals(WeightedRoundRobinRoutingStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertTrue(decision.explanation().candidateServersConsidered().isEmpty());
        assertTrue(decision.explanation().chosenServerId().isEmpty());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("No healthy eligible servers"));
    }

    @Test
    void singleHealthyCandidateIsSelected() {
        RoutingDecision decision = strategy.choose(List.of(state("green", true, 3.0)));

        assertEquals("green", decision.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("green"), decision.explanation().candidateServersConsidered());
        assertEquals("green", decision.explanation().chosenServerId().orElseThrow());
        assertEquals(3.0, decision.explanation().scores().get("green"), 0.0);
        assertTrue(decision.explanation().reason().contains("only healthy candidate"));
        assertEquals(NOW, decision.explanation().timestamp());
    }

    @Test
    void smoothWeightedRoundRobinPreservesExpectedRatio() {
        List<ServerStateVector> candidates = List.of(
                state("primary", true, 3.0),
                state("secondary", true, 1.0));

        List<String> selections = chooseMany(candidates, 8);

        assertEquals(List.of(
                "primary", "primary", "secondary", "primary",
                "primary", "primary", "secondary", "primary"), selections);
        assertEquals(6, selections.stream().filter("primary"::equals).count());
        assertEquals(2, selections.stream().filter("secondary"::equals).count());
    }

    @Test
    void zeroAndMissingWeightDefaultSafely() {
        ServerStateVector zeroWeight = state("zero", true, 0.0);
        ServerStateVector missingWeight = stateWithoutWeight("missing", true);

        RoutingDecision first = strategy.choose(List.of(zeroWeight, missingWeight));
        RoutingDecision second = strategy.choose(List.of(zeroWeight, missingWeight));

        assertEquals("zero", first.chosenServer().orElseThrow().serverId());
        assertEquals("missing", second.chosenServer().orElseThrow().serverId());
        assertEquals(1.0, first.explanation().scores().get("zero"), 0.0);
        assertEquals(1.0, first.explanation().scores().get("missing"), 0.0);
        assertEquals(1.0, missingWeight.weight(), 0.0);
    }

    @Test
    void verySmallPositiveWeightClampsSafely() {
        RoutingDecision decision = strategy.choose(List.of(
                state("min", true, 0.1),
                state("tiny", true, 0.01)));

        assertEquals(0.1, decision.explanation().scores().get("min"), 0.0);
        assertEquals(0.1, decision.explanation().scores().get("tiny"), 0.0);
    }

    @Test
    void skipsUnhealthyCandidates() {
        List<ServerStateVector> candidates = List.of(
                state("unhealthy-heavy", false, 100.0),
                state("healthy", true, 1.0));

        RoutingDecision decision = strategy.choose(candidates);

        assertEquals("healthy", decision.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("healthy"), decision.explanation().candidateServersConsidered());
        assertFalse(decision.explanation().scores().containsKey("unhealthy-heavy"));
    }

    @Test
    void disappearedCandidatesAreRemovedFromObservableState() {
        assertEquals("primary", strategy.choose(List.of(
                state("primary", true, 3.0),
                state("secondary", true, 1.0))).chosenServer().orElseThrow().serverId());

        RoutingDecision afterPrimaryDisappears = strategy.choose(List.of(
                state("secondary", true, 1.0)));

        assertEquals("secondary", afterPrimaryDisappears.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("secondary"), afterPrimaryDisappears.explanation().candidateServersConsidered());
        assertEquals(List.of("secondary"), new ArrayList<>(afterPrimaryDisappears.explanation().scores().keySet()));

        RoutingDecision afterPrimaryReturns = strategy.choose(List.of(
                state("primary", true, 3.0),
                state("secondary", true, 1.0)));

        assertEquals("primary", afterPrimaryReturns.chosenServer().orElseThrow().serverId());
    }

    @Test
    void equalWeightTiesUseRequestOrderDeterministically() {
        List<ServerStateVector> candidates = List.of(
                state("first", true, 1.0),
                state("second", true, 1.0));

        RoutingDecision first = strategy.choose(candidates);
        RoutingDecision second = strategy.choose(candidates);
        RoutingDecision third = strategy.choose(candidates);

        assertEquals("first", first.chosenServer().orElseThrow().serverId());
        assertEquals("second", second.chosenServer().orElseThrow().serverId());
        assertEquals("first", third.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("first", "second"), first.explanation().candidateServersConsidered());
    }

    @Test
    void repeatedCallsRemainStableAndFair() {
        List<ServerStateVector> candidates = List.of(
                state("primary", true, 5.0),
                state("backup", true, 1.0));

        List<String> selections = chooseMany(candidates, 60);

        assertEquals(50, selections.stream().filter("primary"::equals).count());
        assertEquals(10, selections.stream().filter("backup"::equals).count());
    }

    @Test
    void doesNotMutateInputState() {
        List<ServerStateVector> candidates = new ArrayList<>(List.of(
                state("primary", true, 3.0),
                state("secondary", true, 1.0)));
        List<ServerStateVector> before = List.copyOf(candidates);

        strategy.choose(candidates);

        assertEquals(before, candidates);
    }

    private List<String> chooseMany(List<ServerStateVector> candidates, int times) {
        List<String> selections = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            selections.add(strategy.choose(candidates).chosenServer().orElseThrow().serverId());
        }
        return selections;
    }

    private ServerStateVector state(String id, boolean healthy, double weight) {
        return new ServerStateVector(
                id,
                healthy,
                0,
                OptionalDouble.of(100.0),
                OptionalDouble.of(100.0),
                weight,
                10.0,
                20.0,
                40.0,
                0.0,
                OptionalInt.of(0),
                NetworkAwarenessSignal.neutral(id, NOW),
                NOW);
    }

    private ServerStateVector stateWithoutWeight(String id, boolean healthy) {
        return new ServerStateVector(
                id,
                healthy,
                0,
                OptionalDouble.of(100.0),
                OptionalDouble.of(100.0),
                10.0,
                20.0,
                40.0,
                0.0,
                OptionalInt.of(0),
                NetworkAwarenessSignal.neutral(id, NOW),
                NOW);
    }
}
