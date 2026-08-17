package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.assertRingLinksClosed;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedRingEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicTopologyEngineTest {
    @Test
    void addNodeAssignsUniqueIdPriorityAndClosesRingLinks() {
        DefaultSimulationEngine engine = initializedRingEngine();

        SimulationSnapshot snapshot = engine.addNode(AlgorithmIds.RING_ELECTION);

        assertEquals(7, snapshot.nodes().size());
        assertTrue(snapshot.nodes().stream().anyMatch(node -> node.nodeId().equals(node("node-g"))));
        assertEquals(7, EngineTestHelper.node(snapshot, "node-g").electionPriority());
        assertRingLinksClosed(snapshot.nodes());
    }

    @Test
    void removeNodeClosesRingLinksAndDropsAffectedMessages() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        engine.step();
        engine.step();
        assertFalse(engine.currentSnapshot().messagesInFlight().isEmpty());

        SimulationSnapshot snapshot = engine.removeNode(node("node-b"));

        assertEquals(5, snapshot.nodes().size());
        assertTrue(snapshot.nodes().stream().noneMatch(node -> node.nodeId().equals(node("node-b"))));
        assertTrue(snapshot.messagesInFlight().stream()
                .noneMatch(message -> message.from().equals(node("node-b"))
                        || message.to().equals(node("node-b"))));
        assertRingLinksClosed(snapshot.nodes());
    }
}
