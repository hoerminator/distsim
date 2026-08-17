package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import de.jakobhoermann.distsim.core.scenario.ScenarioFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EngineTestHelper {
    private EngineTestHelper() {
    }

    static DefaultSimulationEngine initializedBullyEngine() {
        DefaultSimulationEngine engine = new DefaultSimulationEngine();
        engine.initialize(ScenarioFactory.starterScenario());
        return engine;
    }

    static DefaultSimulationEngine initializedRingEngine() {
        DefaultSimulationEngine engine = new DefaultSimulationEngine();
        engine.initialize(ScenarioFactory.ringElectionScenario());
        return engine;
    }

    static SimulationSnapshot drain(DefaultSimulationEngine engine) {
        SimulationSnapshot snapshot = engine.currentSnapshot();
        for (int i = 0; i < 200; i++) {
            snapshot = engine.step();
            if (!snapshot.running()) {
                return snapshot;
            }
        }
        return snapshot;
    }

    static void assertCoordinator(SimulationSnapshot snapshot, String coordinatorId) {
        NodeId coordinator = node(coordinatorId);
        NodeSnapshot coordinatorNode = node(snapshot, coordinatorId);
        assertTrue(coordinatorNode.electionState().coordinator());
        assertEquals(coordinator, coordinatorNode.electionState().knownCoordinator());
        snapshot.nodes().stream()
                .filter(NodeSnapshot::alive)
                .filter(node -> !node.nodeId().equals(coordinator))
                .forEach(node -> assertEquals(coordinator, node.electionState().knownCoordinator()));
    }

    static void assertRingLinksClosed(List<NodeSnapshot> nodes) {
        Map<NodeId, NodeSnapshot> byId = nodes.stream()
                .collect(Collectors.toMap(NodeSnapshot::nodeId, Function.identity()));
        for (NodeSnapshot node : nodes) {
            assertTrue(byId.containsKey(node.ringSuccessor()));
            assertTrue(byId.containsKey(node.ringPredecessor()));
            assertEquals(node.nodeId(), byId.get(node.ringSuccessor()).ringPredecessor());
            assertEquals(node.nodeId(), byId.get(node.ringPredecessor()).ringSuccessor());
            assertNotEquals(node.ringSuccessor(), node.nodeId(), "multi-node rings should not point to themselves");
        }
    }

    static NodeSnapshot node(SimulationSnapshot snapshot, String nodeId) {
        return snapshot.nodes().stream()
                .filter(node -> node.nodeId().equals(node(nodeId)))
                .findFirst()
                .orElseThrow();
    }

    static NodeId node(String nodeId) {
        return new NodeId(nodeId);
    }
}
