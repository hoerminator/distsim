package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.assertCoordinator;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.drain;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedRingEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingElectionEngineTest {
    @Test
    void electsHighestPriorityNodeAfterTokenCirculates() {
        DefaultSimulationEngine engine = initializedRingEngine();

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        assertCoordinator(snapshot, "node-c");
    }

    @Test
    void abortsWhenDirectSuccessorIsInactive() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.setNodeActive(node("node-b"), false);

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        assertFalse(node(snapshot, "node-a").electionState().electionInProgress());
        assertFalse(node(snapshot, "node-a").electionState().coordinator());
        assertTrue(snapshot.eventLog().stream()
                .anyMatch(line -> line.contains("Ring election aborted")
                        && line.contains("successor of A")));
    }
}
