package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import de.jakobhoermann.distsim.core.scenario.ScenarioFactory;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.drain;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RicartAgrawalaMutexEngineTest {
    @Test
    void entersAndReleasesCriticalSectionAfterReceivingReplies() {
        DefaultSimulationEngine engine = initializedMutexEngine();

        engine.startAlgorithm(AlgorithmIds.RICART_AGRAWALA_MUTEX, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        assertFalse(node(snapshot, "node-a").mutexState().inCriticalSection());
        assertFalse(node(snapshot, "node-a").mutexState().requestingCriticalSection());
        assertTrue(snapshot.eventLog().stream()
                .anyMatch(line -> line.contains("A entered the critical section")));
        assertTrue(snapshot.eventLog().stream()
                .anyMatch(line -> line.contains("A released the critical section")));
        assertEquals(4, snapshot.runSummary().sentMessages());
        assertEquals(2, snapshot.runSummary().messagesByKind().get(MessageKind.MUTEX_REQUEST));
        assertEquals(2, snapshot.runSummary().messagesByKind().get(MessageKind.MUTEX_REPLY));
        assertTrue(snapshot.nodes().stream()
                .allMatch(node -> node.mutexState().requestQueue().isEmpty()));
    }

    @Test
    void exposesRequestMessagesWhileTheyAreInFlight() {
        DefaultSimulationEngine engine = initializedMutexEngine();

        engine.startAlgorithm(AlgorithmIds.RICART_AGRAWALA_MUTEX, node("node-a"));
        SimulationSnapshot snapshot = engine.step();
        snapshot = engine.step();

        assertTrue(snapshot.messagesInFlight().stream()
                .anyMatch(message -> message.kind() == MessageKind.MUTEX_REQUEST
                        && "1".equals(message.attributes().get("timestamp"))));
    }

    @Test
    void usesEditableLamportClockForNextRequestTimestamp() {
        DefaultSimulationEngine engine = initializedMutexEngine();

        engine.updateNodeLamportClock(node("node-a"), 7);
        engine.startAlgorithm(AlgorithmIds.RICART_AGRAWALA_MUTEX, node("node-a"));
        SimulationSnapshot snapshot = engine.step();

        assertEquals(8, node(snapshot, "node-a").mutexState().ownRequestTimestamp());
        assertEquals(8, node(snapshot, "node-a").lamportClock());
    }

    private DefaultSimulationEngine initializedMutexEngine() {
        DefaultSimulationEngine engine = new DefaultSimulationEngine();
        engine.initialize(ScenarioFactory.scenarioFor(AlgorithmIds.RICART_AGRAWALA_MUTEX));
        return engine;
    }
}
