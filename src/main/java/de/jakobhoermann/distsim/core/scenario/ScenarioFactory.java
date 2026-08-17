package de.jakobhoermann.distsim.core.scenario;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmRegistry;
import de.jakobhoermann.distsim.core.model.ClusterTopology;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.snapshot.DeadlockStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ElectionStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.MutexStateSnapshot;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ResourceSnapshot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScenarioFactory {
    private ScenarioFactory() {
    }

    public static Scenario scenarioFor(AlgorithmId algorithm) {
        return AlgorithmRegistry.defaultScenarioFor(algorithm);
    }

    public static Scenario starterScenario() {
        List<NodeSnapshot> nodes = List.of(
                node("node-a", "Node A", 1, 0, "node-b", "node-f", 120, 120),
                node("node-b", "Node B", 2, 1, "node-c", "node-a", 300, 80),
                node("node-c", "Node C", 3, 2, "node-d", "node-b", 480, 120),
                node("node-d", "Node D", 4, 3, "node-e", "node-c", 500, 300),
                node("node-e", "Node E", 5, 4, "node-f", "node-d", 300, 350, false),
                node("node-f", "Node F", 6, 5, "node-a", "node-e", 100, 300, false)
        );
        return new Scenario("Six-node Bully starter with failures", new ClusterTopology(nodes, List.of()), 42L);
    }

    public static Scenario mutexScenario() {
        List<NodeSnapshot> nodes = List.of(
                node("node-a", "Process A", 1, 0, "node-b", "node-c", 180, 180),
                node("node-b", "Process B", 2, 1, "node-c", "node-a", 420, 180),
                node("node-c", "Process C", 3, 2, "node-a", "node-b", 300, 340)
        );
        return new Scenario("Three-process mutex starter", new ClusterTopology(nodes, List.of()), 84L);
    }

    public static Scenario ringElectionScenario() {
        List<NodeSnapshot> nodes = List.of(
                node("node-a", "Ring A", 3, 0, "node-b", "node-f", 300, 70),
                node("node-b", "Ring B", 1, 1, "node-c", "node-a", 465, 165),
                node("node-c", "Ring C", 6, 2, "node-d", "node-b", 465, 355),
                node("node-d", "Ring D", 2, 3, "node-e", "node-c", 300, 450),
                node("node-e", "Ring E", 5, 4, "node-f", "node-d", 135, 355),
                node("node-f", "Ring F", 4, 5, "node-a", "node-e", 135, 165)
        );
        return new Scenario("Six-node Ring election", new ClusterTopology(nodes, List.of()), 77L);
    }

    public static Scenario deadlockDetectionScenario() {
        ResourceId resourceA = new ResourceId("resource-a");
        ResourceId resourceB = new ResourceId("resource-b");
        ResourceId resourceC = new ResourceId("resource-c");
        NodeId nodeA = new NodeId("node-a");
        NodeId nodeB = new NodeId("node-b");
        NodeId nodeC = new NodeId("node-c");

        List<NodeSnapshot> nodes = List.of(
                node(
                        "node-a",
                        "Process A",
                        1,
                        0,
                        "node-b",
                        "node-c",
                        160,
                        180,
                        deadlockState(Set.of(resourceA), Set.of(resourceB))
                ),
                node(
                        "node-b",
                        "Process B",
                        2,
                        1,
                        "node-c",
                        "node-a",
                        300,
                        340,
                        deadlockState(Set.of(resourceB), Set.of(resourceC))
                ),
                node(
                        "node-c",
                        "Process C",
                        3,
                        2,
                        "node-a",
                        "node-b",
                        440,
                        180,
                        deadlockState(Set.of(resourceC), Set.of(resourceA))
                )
        );
        List<ResourceSnapshot> resources = List.of(
                new ResourceSnapshot(resourceA, "Resource A", nodeA, false),
                new ResourceSnapshot(resourceB, "Resource B", nodeB, false),
                new ResourceSnapshot(resourceC, "Resource C", nodeC, false)
        );
        return new Scenario("Three-process deadlock", new ClusterTopology(nodes, resources), 91L);
    }

    private static NodeSnapshot node(
            String id,
            String label,
            int electionPriority,
            int ringPosition,
            String ringSuccessor,
            String ringPredecessor,
            double x,
            double y
    ) {
        return node(id, label, electionPriority, ringPosition, ringSuccessor, ringPredecessor, x, y, true);
    }

    private static NodeSnapshot node(
            String id,
            String label,
            int electionPriority,
            int ringPosition,
            String ringSuccessor,
            String ringPredecessor,
            double x,
            double y,
            boolean alive
    ) {
        return new NodeSnapshot(
                new NodeId(id),
                x,
                y,
                label,
                alive,
                electionPriority,
                ringPosition,
                new NodeId(ringSuccessor),
                new NodeId(ringPredecessor),
                0L,
                ElectionStateSnapshot.initial(),
                MutexStateSnapshot.initial(),
                DeadlockStateSnapshot.initial()
        );
    }

    private static NodeSnapshot node(
            String id,
            String label,
            int electionPriority,
            int ringPosition,
            String ringSuccessor,
            String ringPredecessor,
            double x,
            double y,
            DeadlockStateSnapshot deadlockState
    ) {
        return new NodeSnapshot(
                new NodeId(id),
                x,
                y,
                label,
                true,
                electionPriority,
                ringPosition,
                new NodeId(ringSuccessor),
                new NodeId(ringPredecessor),
                0L,
                ElectionStateSnapshot.initial(),
                MutexStateSnapshot.initial(),
                deadlockState
        );
    }

    private static DeadlockStateSnapshot deadlockState(Set<ResourceId> heldResources, Set<ResourceId> awaitedResources) {
        return new DeadlockStateSnapshot(
                new LinkedHashSet<>(heldResources),
                new LinkedHashSet<>(awaitedResources),
                List.of(),
                List.of(),
                false
        );
    }

}
