package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.assertCoordinator;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.drain;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedBullyEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BullyElectionEngineTest {
    @Test
    void electsHighestAlivePriorityNode() {
        DefaultSimulationEngine engine = initializedBullyEngine();

        engine.startAlgorithm(AlgorithmIds.BULLY_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        assertCoordinator(snapshot, "node-d");
        assertFalse(node(snapshot, "node-e").alive());
        assertFalse(node(snapshot, "node-f").alive());
    }

    @Test
    void reportsPendingEventsBetweenInitializationAndDrain() {
        DefaultSimulationEngine engine = initializedBullyEngine();

        SimulationSnapshot scheduled = engine.startAlgorithm(AlgorithmIds.BULLY_ELECTION, node("node-a"));
        SimulationSnapshot firstStep = engine.step();
        SimulationSnapshot drained = drain(engine);

        assertTrue(scheduled.hasPendingEvents());
        assertTrue(firstStep.hasPendingEvents());
        assertFalse(drained.hasPendingEvents());
    }

    @Test
    void ignoresInactiveHighestPriorityNode() {
        DefaultSimulationEngine engine = initializedBullyEngine();
        engine.setNodeActive(node("node-d"), false);

        engine.startAlgorithm(AlgorithmIds.BULLY_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        assertCoordinator(snapshot, "node-c");
        assertFalse(node(snapshot, "node-d").alive());
    }

    @Test
    void usesRuntimePriorityChanges() {
        DefaultSimulationEngine engine = initializedBullyEngine();
        engine.updateNodePriority(node("node-a"), 10);

        engine.startAlgorithm(AlgorithmIds.BULLY_ELECTION, node("node-b"));
        SimulationSnapshot snapshot = drain(engine);

        assertCoordinator(snapshot, "node-a");
        assertEquals(10, node(snapshot, "node-a").electionPriority());
    }
}
