package de.jakobhoermann.distsim.app.service;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.engine.SimulationEngine;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.scenario.Scenario;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;

public final class SimulationApplicationService {
    private final SimulationEngine engine;

    public SimulationApplicationService(SimulationEngine engine) {
        this.engine = engine;
    }

    public SimulationSnapshot loadScenario(Scenario scenario) {
        engine.initialize(scenario);
        return engine.currentSnapshot();
    }

    public SimulationSnapshot startAlgorithm(AlgorithmId algorithm, NodeId initiator) {
        return engine.startAlgorithm(algorithm, initiator);
    }

    public SimulationSnapshot setNodeActive(NodeId nodeId, boolean active) {
        return engine.setNodeActive(nodeId, active);
    }

    public SimulationSnapshot updateNodePriority(NodeId nodeId, int priority) {
        return engine.updateNodePriority(nodeId, priority);
    }

    public SimulationSnapshot updateNodeLamportClock(NodeId nodeId, long lamportClock) {
        return engine.updateNodeLamportClock(nodeId, lamportClock);
    }

    public SimulationSnapshot setNodeHoldsResource(NodeId nodeId, ResourceId resourceId, boolean holds) {
        return engine.setNodeHoldsResource(nodeId, resourceId, holds);
    }

    public SimulationSnapshot setNodeAwaitsResource(NodeId nodeId, ResourceId resourceId, boolean awaits) {
        return engine.setNodeAwaitsResource(nodeId, resourceId, awaits);
    }

    public SimulationSnapshot addNode(AlgorithmId algorithm) {
        return engine.addNode(algorithm);
    }

    public SimulationSnapshot removeNode(NodeId nodeId) {
        return engine.removeNode(nodeId);
    }

    public SimulationSnapshot addResource() {
        return engine.addResource();
    }

    public SimulationSnapshot removeResource(ResourceId resourceId) {
        return engine.removeResource(resourceId);
    }

    public SimulationSnapshot dropMessage(String messageId) {
        return engine.dropMessage(messageId);
    }

    public SimulationSnapshot updateSettings(SimulationSettings settings) {
        return engine.updateSettings(settings);
    }

    public SimulationSnapshot play() {
        return engine.start();
    }

    public SimulationSnapshot pause() {
        return engine.pause();
    }

    public SimulationSnapshot step() {
        return engine.step();
    }

    public SimulationSnapshot reset() {
        return engine.reset();
    }

    public SimulationSnapshot currentSnapshot() {
        return engine.currentSnapshot();
    }
}
