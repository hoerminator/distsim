package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.scenario.Scenario;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;

public interface SimulationEngine {
    void initialize(Scenario scenario);

    SimulationSnapshot startAlgorithm(AlgorithmId algorithm, NodeId initiator);

    SimulationSnapshot setNodeActive(NodeId nodeId, boolean active);

    SimulationSnapshot updateNodePriority(NodeId nodeId, int priority);

    SimulationSnapshot updateNodeLamportClock(NodeId nodeId, long lamportClock);

    SimulationSnapshot setNodeHoldsResource(NodeId nodeId, ResourceId resourceId, boolean holds);

    SimulationSnapshot setNodeAwaitsResource(NodeId nodeId, ResourceId resourceId, boolean awaits);

    SimulationSnapshot addNode(AlgorithmId algorithm);

    SimulationSnapshot removeNode(NodeId nodeId);

    SimulationSnapshot addResource();

    SimulationSnapshot removeResource(ResourceId resourceId);

    SimulationSnapshot dropMessage(String messageId);

    SimulationSnapshot updateSettings(SimulationSettings settings);

    SimulationSnapshot start();

    SimulationSnapshot pause();

    SimulationSnapshot step();

    SimulationSnapshot reset();

    SimulationSnapshot currentSnapshot();
}
