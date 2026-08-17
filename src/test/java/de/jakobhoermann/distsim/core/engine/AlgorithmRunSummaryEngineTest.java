package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.model.snapshot.AlgorithmRunSummary;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.drain;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedRingEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmRunSummaryEngineTest {
    @Test
    void recordsCompletedRunMetricsAndResult() {
        DefaultSimulationEngine engine = initializedRingEngine();

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        AlgorithmRunSummary summary = snapshot.runSummary();
        assertTrue(summary.completed());
        assertEquals(AlgorithmIds.RING_ELECTION, summary.algorithm());
        assertEquals(node("node-a"), summary.initiator());
        assertEquals(6, summary.activeNodesAtStart());
        assertEquals(SimulationSettings.DEFAULT_RANDOM_SEED, summary.randomSeed());
        assertTrue(summary.duration(snapshot.time()) > 0);
        assertTrue(summary.processedEvents() > 0);
        assertTrue(summary.sentMessages() > 0);
        assertEquals(0, summary.lostMessages());
        assertTrue(summary.messagesByKind().getOrDefault(MessageKind.RING_TOKEN, 0L) > 0);
        assertTrue(summary.result().contains("node-c"));
    }

    @Test
    void recordsLostMessagesSeparately() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.updateSettings(new SimulationSettings(10, 0, 100, 1));

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        AlgorithmRunSummary summary = snapshot.runSummary();
        assertTrue(summary.completed());
        assertEquals(1, summary.randomSeed());
        assertEquals(0, summary.sentMessages());
        assertEquals(1, summary.lostMessages());
        assertEquals("No result", summary.result());
    }
}
