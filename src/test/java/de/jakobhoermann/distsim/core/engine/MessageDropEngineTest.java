package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.snapshot.MessageSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.drain;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedRingEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDropEngineTest {
    @Test
    void manuallyDroppedMessageIsNotDelivered() {
        DefaultSimulationEngine engine = initializedRingEngine();

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        engine.step();
        SimulationSnapshot snapshot = engine.step();
        MessageSnapshot message = snapshot.messagesInFlight().getFirst();

        snapshot = engine.dropMessage(message.id());

        assertTrue(snapshot.messagesInFlight().isEmpty());
        assertEquals(1, snapshot.runSummary().lostMessages());
        assertTrue(snapshot.eventLog().stream()
                .anyMatch(line -> line.contains("manually dropped ELECTION from A to B")));

        SimulationSnapshot completed = drain(engine);
        assertFalse(completed.eventLog().stream()
                .anyMatch(line -> line.contains("received Ring ELECTION token")));
    }
}
