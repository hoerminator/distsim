package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.snapshot.EventLogCategory;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.initializedBullyEngine;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogEngineTest {
    @Test
    void storesStructuredLogEntriesWithSimulationTime() {
        DefaultSimulationEngine engine = initializedBullyEngine();

        engine.startAlgorithm(AlgorithmIds.BULLY_ELECTION, node("node-a"));
        SimulationSnapshot snapshot = engine.step();

        assertEquals(0, engine.currentSnapshot().eventLogEntries().stream()
                .filter(entry -> entry.message().contains("Loaded Scenario"))
                .count());
        assertTrue(snapshot.eventLogEntries().stream()
                .anyMatch(entry -> entry.time() == 1
                        && entry.category() == EventLogCategory.STATE
                        && entry.message().contains("started Bully election")));
    }
}
