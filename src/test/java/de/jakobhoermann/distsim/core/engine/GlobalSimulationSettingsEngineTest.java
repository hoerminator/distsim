package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import de.jakobhoermann.distsim.core.scenario.ScenarioFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedRingEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalSimulationSettingsEngineTest {
    @Test
    void usesConfiguredBaseMessageDelayForMessagesInFlight() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.updateSettings(new SimulationSettings(5, 0, 0, 1));

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        engine.step();
        SimulationSnapshot snapshot = engine.step();

        assertEquals(1, snapshot.time());
        assertEquals(1, snapshot.messagesInFlight().size());
        assertEquals(6, snapshot.messagesInFlight().getFirst().scheduledDeliveryTime());
    }

    @Test
    void keepsConfiguredVarianceInsideExpectedBounds() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.updateSettings(new SimulationSettings(10, 3, 0, 7));

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        engine.step();
        SimulationSnapshot snapshot = engine.step();

        long effectiveDelay = snapshot.messagesInFlight().getFirst().scheduledDeliveryTime() - snapshot.time();
        assertTrue(effectiveDelay >= 7);
        assertTrue(effectiveDelay <= 13);
    }

    @Test
    void canLoseMessagesCompletely() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.updateSettings(new SimulationSettings(10, 0, 100, 1));

        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        engine.step();
        SimulationSnapshot snapshot = engine.step();

        assertTrue(snapshot.messagesInFlight().isEmpty());
        assertFalse(snapshot.hasPendingEvents());
        assertTrue(snapshot.eventLog().stream()
                .anyMatch(line -> line.contains("lost Ring ELECTION token from A to B")));
    }

    @Test
    void repeatsRandomSequenceForNewRunWithSameSeed() {
        DefaultSimulationEngine engine = initializedRingEngine();
        engine.updateSettings(new SimulationSettings(10, 0, 30, 1));

        List<String> firstRunLog = runRingElection(engine);
        engine.initialize(ScenarioFactory.ringElectionScenario());
        List<String> secondRunLog = runRingElection(engine);

        assertEquals(firstRunLog, secondRunLog);
    }

    private List<String> runRingElection(DefaultSimulationEngine engine) {
        engine.startAlgorithm(AlgorithmIds.RING_ELECTION, node("node-a"));
        return EngineTestHelper.drain(engine).eventLog();
    }
}
