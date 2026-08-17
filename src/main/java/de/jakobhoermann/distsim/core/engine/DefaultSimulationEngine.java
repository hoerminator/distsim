package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmController;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmModule;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmRegistry;
import de.jakobhoermann.distsim.core.algorithm.TopologyProfile;
import de.jakobhoermann.distsim.core.algorithm.SimulationEvent;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.model.snapshot.DeadlockStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ElectionStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.MutexStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ResourceSnapshot;
import de.jakobhoermann.distsim.core.scenario.Scenario;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultSimulationEngine implements SimulationEngine {
    private final EventScheduler scheduler;
    private final SimulationClock clock;
    private final MutableSimulationState state;
    private final Map<AlgorithmId, AlgorithmController> algorithms = new LinkedHashMap<>();
    private int nextDynamicNodeNumber = 1;
    private int nextDynamicResourceNumber = 1;

    public DefaultSimulationEngine() {
        this.scheduler = new PriorityQueueEventScheduler();
        this.clock = new SimulationClock();
        this.state = new MutableSimulationState(scheduler);
        for (AlgorithmModule module : AlgorithmRegistry.modules()) {
            register(module.controller());
        }
    }

    @Override
    public void initialize(Scenario scenario) {
        scheduler.clear();
        clock.reset();
        state.reset();
        state.replaceNodes(scenario.topology().nodes());
        state.replaceResources(scenario.topology().resources());
        nextDynamicNodeNumber = maxNodeNumber(state.nodes()) + 1;
        nextDynamicResourceNumber = maxResourceNumber(state.resources()) + 1;
    }

    @Override
    public SimulationSnapshot startAlgorithm(AlgorithmId algorithm, NodeId initiator) {
        NodeSnapshot node = state.node(initiator);
        if (node == null || !node.alive()) {
            state.appendLog("Cannot start " + displayName(algorithm) + ": " + initiator + " is not available.");
            return currentSnapshot();
        }

        AlgorithmController controller = algorithms.get(algorithm);
        if (controller == null) {
            state.appendLog(displayName(algorithm) + " cannot be started from the UI yet.");
            return currentSnapshot();
        }

        long scheduledTime = clock.now() + 1;
        state.startRun(algorithm, initiator, scheduledTime, state.aliveNodes().size());
        controller.start(state, scheduledTime, initiator, scheduledTime);
        state.appendLog("Scheduled " + controller.displayName() + " from " + initiator + " at t=" + scheduledTime);
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot setNodeActive(NodeId nodeId, boolean active) {
        NodeSnapshot node = state.node(nodeId);
        if (node == null) {
            state.appendLog("Cannot update node power: " + nodeId + " does not exist.");
            return currentSnapshot();
        }

        state.updateNodeAvailability(nodeId, active);
        state.appendLog(nodeId + (active ? " turned on." : " turned off."));
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot updateNodePriority(NodeId nodeId, int priority) {
        NodeSnapshot node = state.node(nodeId);
        if (node == null) {
            state.appendLog("Cannot update priority: " + nodeId + " does not exist.");
            return currentSnapshot();
        }
        int normalizedPriority = Math.max(1, priority);
        state.updateNodePriority(nodeId, normalizedPriority);
        state.appendLog(nodeId + " priority set to " + normalizedPriority + ".");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot updateNodeLamportClock(NodeId nodeId, long lamportClock) {
        NodeSnapshot node = state.node(nodeId);
        if (node == null) {
            state.appendLog("Cannot update Lamport clock: " + nodeId + " does not exist.");
            return currentSnapshot();
        }
        long normalizedLamportClock = Math.max(0L, lamportClock);
        state.updateLamportClock(nodeId, normalizedLamportClock);
        state.appendLog(nodeId + " Lamport clock set to " + normalizedLamportClock + ".");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot setNodeHoldsResource(NodeId nodeId, ResourceId resourceId, boolean holds) {
        NodeSnapshot node = state.node(nodeId);
        if (node == null || state.resource(resourceId) == null) {
            state.appendLog("Cannot update held resource: node or resource does not exist.");
            return currentSnapshot();
        }

        if (holds) {
            for (NodeSnapshot candidate : state.nodes()) {
                if (!candidate.nodeId().equals(nodeId)) {
                    state.setHeldResource(candidate.nodeId(), resourceId, false);
                }
            }
            state.updateResourceOwner(resourceId, nodeId);
            state.setAwaitedResource(nodeId, resourceId, false);
        } else if (node.deadlockState().heldResources().contains(resourceId)) {
            state.updateResourceOwner(resourceId, null);
        }
        state.setHeldResource(nodeId, resourceId, holds);
        state.clearDeadlockAnalysis();
        state.appendLog(nodeId + (holds ? " now holds " : " released ") + resourceId + ".");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot setNodeAwaitsResource(NodeId nodeId, ResourceId resourceId, boolean awaits) {
        NodeSnapshot node = state.node(nodeId);
        if (node == null || state.resource(resourceId) == null) {
            state.appendLog("Cannot update requested resource: node or resource does not exist.");
            return currentSnapshot();
        }

        if (awaits && node.deadlockState().heldResources().contains(resourceId)) {
            state.appendLog(nodeId + " already holds " + resourceId + " and cannot wait for it.");
            return currentSnapshot();
        }

        state.setAwaitedResource(nodeId, resourceId, awaits);
        state.clearDeadlockAnalysis();
        state.appendLog(nodeId + (awaits ? " now waits for " : " no longer waits for ") + resourceId + ".");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot addNode(AlgorithmId algorithm) {
        NodeSnapshot node = createNode(algorithm);
        state.addNode(node);
        rebalanceTopology();
        state.appendLog(node.nodeId() + " added to the topology.");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot removeNode(NodeId nodeId) {
        NodeSnapshot node = state.node(nodeId);
        if (node == null) {
            state.appendLog("Cannot remove node: " + nodeId + " does not exist.");
            return currentSnapshot();
        }

        state.removeNode(nodeId);
        rebalanceTopology();
        state.appendLog(nodeId + " removed from the topology.");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot addResource() {
        int number = nextDynamicResourceNumber++;
        ResourceId resourceId = new ResourceId("resource-" + nodeSuffix(number).toLowerCase());
        state.addResource(new ResourceSnapshot(
                resourceId,
                "Resource " + nodeSuffix(number),
                null,
                true
        ));
        state.clearDeadlockAnalysis();
        state.appendLog(resourceId + " added.");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot removeResource(ResourceId resourceId) {
        if (state.resource(resourceId) == null) {
            state.appendLog("Cannot remove resource: " + resourceId + " does not exist.");
            return currentSnapshot();
        }
        state.removeResource(resourceId);
        state.clearDeadlockAnalysis();
        state.appendLog(resourceId + " removed.");
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot dropMessage(String messageId) {
        if (!state.dropMessage(messageId)) {
            state.appendLog("Cannot drop message: " + messageId + " is not in flight.");
        }
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot updateSettings(SimulationSettings settings) {
        state.updateSettings(settings);
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot start() {
        state.setRunning(true);
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot pause() {
        state.setRunning(false);
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot step() {
        state.setRunning(true);
        if (!scheduler.hasNext()) {
            state.setRunning(false);
            state.appendLog("No more events to process.");
            return currentSnapshot();
        }

        SimulationEvent event = scheduler.pollNext();
        clock.advanceTo(event.scheduledTime());
        state.setTime(clock.now());
        state.recordProcessedEvent();
        event.apply(state);

        if (!scheduler.hasNext()) {
            state.setRunning(false);
            state.finishRun(clock.now());
        }
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot reset() {
        scheduler.clear();
        clock.reset();
        state.reset();
        return currentSnapshot();
    }

    @Override
    public SimulationSnapshot currentSnapshot() {
        state.setTime(clock.now());
        return state.snapshot();
    }

    private String displayName(AlgorithmId algorithm) {
        return AlgorithmRegistry.descriptor(algorithm)
                .map(AlgorithmDescriptor::displayName)
                .orElseGet(algorithm::toString);
    }

    private void register(AlgorithmController controller) {
        algorithms.put(controller.id(), controller);
    }

    private NodeSnapshot createNode(AlgorithmId algorithm) {
        int nextNumber = nextDynamicNodeNumber++;
        String suffix = nodeSuffix(nextNumber);
        NodeId nodeId = new NodeId("node-" + suffix.toLowerCase());
        int priority = state.nodes().stream()
                .mapToInt(NodeSnapshot::electionPriority)
                .max()
                .orElse(0) + 1;
        TopologyProfile topologyProfile = AlgorithmRegistry.descriptor(algorithm)
                .map(AlgorithmDescriptor::topologyProfile)
                .orElse(TopologyProfile.COMPLETE_GRAPH);
        String prefix = topologyProfile == TopologyProfile.RING ? "Ring " : "Node ";

        return new NodeSnapshot(
                nodeId,
                300,
                260,
                prefix + suffix,
                true,
                priority,
                state.nodes().size(),
                nodeId,
                nodeId,
                0L,
                ElectionStateSnapshot.initial(),
                MutexStateSnapshot.initial(),
                DeadlockStateSnapshot.initial()
        );
    }

    private void rebalanceTopology() {
        List<NodeSnapshot> nodes = state.nodes().stream()
                .sorted(Comparator.comparingInt(NodeSnapshot::electionPriority))
                .toList();
        if (nodes.isEmpty()) {
            return;
        }

        List<NodeSnapshot> updatedNodes = new ArrayList<>();
        double centerX = 300;
        double centerY = 260;
        double radius = nodes.size() == 1 ? 0 : 210;
        for (int index = 0; index < nodes.size(); index++) {
            NodeSnapshot node = nodes.get(index);
            NodeId successor = nodes.get((index + 1) % nodes.size()).nodeId();
            NodeId predecessor = nodes.get((index - 1 + nodes.size()) % nodes.size()).nodeId();
            double angle = -Math.PI / 2 + (2 * Math.PI * index / nodes.size());
            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + Math.sin(angle) * radius;
            updatedNodes.add(state.withTopology(node, x, y, index, successor, predecessor));
        }
        state.replaceNodes(updatedNodes);
    }

    private int nodeNumber(String suffix) {
        int number = 0;
        for (int index = 0; index < suffix.length(); index++) {
            char character = suffix.charAt(index);
            if (character < 'a' || character > 'z') {
                return 0;
            }
            number = number * 26 + (character - 'a' + 1);
        }
        return number;
    }

    private int maxNodeNumber(List<NodeSnapshot> nodes) {
        return nodes.stream()
                .map(NodeSnapshot::nodeId)
                .map(NodeId::value)
                .filter(value -> value.startsWith("node-"))
                .map(value -> value.substring("node-".length()))
                .mapToInt(this::nodeNumber)
                .max()
                .orElse(0);
    }

    private int maxResourceNumber(List<ResourceSnapshot> resources) {
        return resources.stream()
                .map(resource -> resource.resourceId().value())
                .filter(value -> value.startsWith("resource-"))
                .map(value -> value.substring("resource-".length()))
                .mapToInt(this::nodeNumber)
                .max()
                .orElse(0);
    }

    private String nodeSuffix(int number) {
        StringBuilder builder = new StringBuilder();
        int current = number;
        while (current > 0) {
            current--;
            builder.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return builder.toString();
    }
}
