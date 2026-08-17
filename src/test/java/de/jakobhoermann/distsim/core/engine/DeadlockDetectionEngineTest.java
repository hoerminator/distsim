package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import de.jakobhoermann.distsim.core.scenario.ScenarioFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.drain;
import static de.jakobhoermann.distsim.core.engine.EngineTestHelper.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlockDetectionEngineTest {
    @Test
    void detectsCycleInWaitForGraph() {
        DefaultSimulationEngine engine = new DefaultSimulationEngine();
        engine.initialize(ScenarioFactory.deadlockDetectionScenario());

        engine.startAlgorithm(AlgorithmIds.LAMPORT_DEADLOCK, node("node-a"));
        SimulationSnapshot snapshot = drain(engine);

        assertTrue(node(snapshot, "node-a").deadlockState().deadlockDetected());
        assertEquals(3, snapshot.resources().size());
        assertEquals(1, node(snapshot, "node-a").deadlockState().awaitedResources().size());
        assertEquals(
                Set.of(new NodeId("node-b")),
                node(snapshot, "node-a").deadlockState().knownWaitForGraph().stream()
                        .filter(edge -> edge.waitingProcess().equals(new NodeId("node-a")))
                        .map(edge -> edge.blockingProcess())
                        .collect(Collectors.toSet())
        );
        assertTrue(snapshot.eventLog().stream()
                .anyMatch(line -> line.contains("A detected a deadlock")));
    }

    @Test
    void canEditHeldAndAwaitedResourcesForDeadlockSandbox() {
        DefaultSimulationEngine engine = new DefaultSimulationEngine();
        engine.initialize(ScenarioFactory.deadlockDetectionScenario());

        ResourceId resourceB = new ResourceId("resource-b");
        ResourceId resourceC = new ResourceId("resource-c");
        SimulationSnapshot snapshot = engine.setNodeHoldsResource(node("node-a"), resourceB, true);

        assertEquals(node("node-a"), snapshot.resources().stream()
                .filter(resource -> resource.resourceId().equals(resourceB))
                .findFirst()
                .orElseThrow()
                .owner());
        assertTrue(node(snapshot, "node-a").deadlockState().heldResources().contains(resourceB));
        assertFalse(node(snapshot, "node-a").deadlockState().awaitedResources().contains(resourceB));
        assertFalse(node(snapshot, "node-b").deadlockState().heldResources().contains(resourceB));

        snapshot = engine.setNodeAwaitsResource(node("node-c"), resourceB, true);
        assertTrue(node(snapshot, "node-c").deadlockState().awaitedResources().contains(resourceB));

        snapshot = engine.setNodeAwaitsResource(node("node-c"), resourceC, true);
        assertFalse(node(snapshot, "node-c").deadlockState().awaitedResources().contains(resourceC));
    }

    @Test
    void canAddAndRemoveResourcesForDeadlockSandbox() {
        DefaultSimulationEngine engine = new DefaultSimulationEngine();
        engine.initialize(ScenarioFactory.deadlockDetectionScenario());

        SimulationSnapshot snapshot = engine.addResource();
        ResourceId resourceD = new ResourceId("resource-d");

        assertEquals(4, snapshot.resources().size());
        assertTrue(snapshot.resources().stream().anyMatch(resource -> resource.resourceId().equals(resourceD)));

        snapshot = engine.setNodeAwaitsResource(node("node-a"), resourceD, true);
        assertTrue(node(snapshot, "node-a").deadlockState().awaitedResources().contains(resourceD));

        snapshot = engine.removeResource(resourceD);
        assertEquals(3, snapshot.resources().size());
        assertFalse(node(snapshot, "node-a").deadlockState().awaitedResources().contains(resourceD));
        assertFalse(snapshot.resources().stream().anyMatch(resource -> resource.resourceId().equals(resourceD)));
    }
}
