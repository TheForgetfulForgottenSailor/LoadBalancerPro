package com.richmond423.loadbalancerpro.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinRoutingStrategyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RoundRobinRoutingStrategy strategy = new RoundRobinRoutingStrategy(FIXED_CLOCK);

    @Test
    void rotatesThroughHealthyCandidatesInRequestOrder() {
        List<ServerStateVector> candidates = List.of(
                state("green", true),
                state("blue", true),
                state("orange", true));

        assertEquals("green", strategy.choose(candidates).chosenServer().orElseThrow().serverId());
        assertEquals("blue", strategy.choose(candidates).chosenServer().orElseThrow().serverId());
        assertEquals("orange", strategy.choose(candidates).chosenServer().orElseThrow().serverId());
        assertEquals("green", strategy.choose(candidates).chosenServer().orElseThrow().serverId());
    }

    @Test
    void skipsUnhealthyCandidates() {
        List<ServerStateVector> candidates = List.of(
                state("green", true),
                state("red", false),
                state("blue", true));

        RoutingDecision first = strategy.choose(candidates);
        RoutingDecision second = strategy.choose(candidates);
        RoutingDecision third = strategy.choose(candidates);

        assertEquals("green", first.chosenServer().orElseThrow().serverId());
        assertEquals("blue", second.chosenServer().orElseThrow().serverId());
        assertEquals("green", third.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("green", "blue"), first.explanation().candidateServersConsidered());
        assertFalse(first.explanation().candidateServersConsidered().contains("red"));
    }

    @Test
    void emptyCandidateListReturnsSafeNoDecision() {
        RoutingDecision decision = strategy.choose(List.of());

        assertTrue(decision.chosenServer().isEmpty());
        assertEquals(RoundRobinRoutingStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertTrue(decision.explanation().candidateServersConsidered().isEmpty());
        assertTrue(decision.explanation().chosenServerId().isEmpty());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("No healthy eligible servers"));
        assertEquals(NOW, decision.explanation().timestamp());
    }

    @Test
    void allUnhealthyCandidatesReturnSafeNoDecision() {
        RoutingDecision decision = strategy.choose(List.of(
                state("red", false),
                state("yellow", false)));

        assertTrue(decision.chosenServer().isEmpty());
        assertEquals(RoundRobinRoutingStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertTrue(decision.explanation().candidateServersConsidered().isEmpty());
        assertTrue(decision.explanation().chosenServerId().isEmpty());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("No healthy eligible servers"));
    }

    @Test
    void singleHealthyCandidateIsSelected() {
        RoutingDecision decision = strategy.choose(List.of(state("green", true)));

        assertEquals("green", decision.chosenServer().orElseThrow().serverId());
        assertEquals(List.of("green"), decision.explanation().candidateServersConsidered());
        assertEquals("green", decision.explanation().chosenServerId().orElseThrow());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("only healthy candidate"));
    }

    @Test
    void singleHealthyCandidateDoesNotConsumeRotationCursor() {
        assertEquals("solo", strategy.choose(List.of(state("solo", true)))
                .chosenServer().orElseThrow().serverId());

        RoutingDecision next = strategy.choose(List.of(state("green", true), state("blue", true)));

        assertEquals("green", next.chosenServer().orElseThrow().serverId());
    }

    @Test
    void explanationContainsStrategyIdAndChosenServerInformation() {
        RoutingDecision decision = strategy.choose(List.of(
                state("green", true),
                state("blue", true)));

        assertEquals(RoundRobinRoutingStrategy.STRATEGY_NAME, decision.explanation().strategyUsed());
        assertEquals(List.of("green", "blue"), decision.explanation().candidateServersConsidered());
        assertEquals("green", decision.explanation().chosenServerId().orElseThrow());
        assertTrue(decision.explanation().scores().isEmpty());
        assertTrue(decision.explanation().reason().contains("round-robin position 1 of 2"));
        assertEquals(NOW, decision.explanation().timestamp());
    }

    @Test
    void sequentialCursorRemainsDeterministicAcrossCandidateSetChanges() {
        assertEquals("green", strategy.choose(List.of(state("green", true), state("blue", true)))
                .chosenServer().orElseThrow().serverId());
        assertEquals("blue", strategy.choose(List.of(state("green", true), state("blue", true)))
                .chosenServer().orElseThrow().serverId());
        assertEquals("orange", strategy.choose(List.of(
                state("green", true),
                state("blue", true),
                state("orange", true))).chosenServer().orElseThrow().serverId());
    }

    private ServerStateVector state(String id, boolean healthy) {
        return new ServerStateVector(
                id,
                healthy,
                0,
                100.0,
                100.0,
                10.0,
                20.0,
                40.0,
                0.0,
                0,
                NOW);
    }
}
