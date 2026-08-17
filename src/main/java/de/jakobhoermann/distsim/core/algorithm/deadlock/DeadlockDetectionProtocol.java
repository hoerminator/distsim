package de.jakobhoermann.distsim.core.algorithm.deadlock;

import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessage;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessageType;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.snapshot.DeadlockStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ProbeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ResourceSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.WaitForEdgeSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DeadlockDetectionProtocol {
    public static final DeadlockDetectionProtocol INSTANCE = new DeadlockDetectionProtocol();

    private static final long NETWORK_DELAY = 10L;

    private DeadlockDetectionProtocol() {
    }

    public void initiateDetection(ProtocolContext context, NodeId initiator, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot node = state.node(initiator);
        if (node == null || !node.alive()) {
            return;
        }

        List<WaitForEdgeSnapshot> outgoingEdges = outgoingWaitForEdges(state, node, context.time());
        if (outgoingEdges.isEmpty()) {
            context.log(initiator + " has no wait-for edge to probe.");
            context.recordResult("No deadlock path from " + initiator);
            return;
        }

        state.updateDeadlockState(initiator, current -> withKnownGraph(current, outgoingEdges, false));
        context.log(initiator + " started deadlock detection round " + round);

        for (WaitForEdgeSnapshot edge : outgoingEdges) {
            scheduleProbe(
                    context,
                    initiator,
                    initiator,
                    edge.blockingProcess(),
                    edge.resourceId(),
                    round
            );
        }
    }

    private void sendProbe(
            ProtocolContext context,
            ProtocolMessage message,
            NodeId initiator,
            NodeId sender,
            NodeId receiver,
            ResourceId waitingFor
    ) {
        context.message(message)
                .requireAliveReceiver()
                .deliverOnlyToAliveReceiver()
                .sentLogLine(sender + " sent deadlock probe to " + receiver)
                .lostLogLine("lost deadlock probe from " + sender + " to " + receiver)
                .receiveDescription("Receive deadlock probe at " + receiver)
                .send(receiveContext -> receiveProbe(
                        receiveContext,
                        initiator,
                        sender,
                        receiver,
                        waitingFor,
                        message.round()
                ));
    }

    private void receiveProbe(
            ProtocolContext context,
            NodeId initiator,
            NodeId sender,
            NodeId receiver,
            ResourceId waitingFor,
            long round
    ) {
        MutableSimulationState state = context.state();
        NodeSnapshot receiverNode = state.node(receiver);
        if (receiverNode == null || !receiverNode.alive()) {
            return;
        }

        ProbeSnapshot probe = new ProbeSnapshot(initiator, sender, receiver, waitingFor, context.time());
        state.updateDeadlockState(receiver, current -> withProbe(current, probe));
        context.log(receiver + " received deadlock probe from " + sender);

        if (receiver.equals(initiator)) {
            markDeadlock(context, initiator);
            return;
        }

        List<WaitForEdgeSnapshot> outgoingEdges = outgoingWaitForEdges(state, receiverNode, context.time());
        if (outgoingEdges.isEmpty()) {
            context.log(receiver + " is not waiting; probe path ended.");
            context.recordResult("No deadlock detected");
            return;
        }

        state.updateDeadlockState(receiver, current -> withKnownGraph(current, outgoingEdges, false));
        for (WaitForEdgeSnapshot edge : outgoingEdges) {
            scheduleProbe(
                    context,
                    initiator,
                    receiver,
                    edge.blockingProcess(),
                    edge.resourceId(),
                    round
            );
        }
    }

    private void markDeadlock(ProtocolContext context, NodeId initiator) {
        context.state().updateDeadlockState(initiator, current -> new DeadlockStateSnapshot(
                current.heldResources(),
                current.awaitedResources(),
                current.knownWaitForGraph(),
                current.activeProbes(),
                true
        ));
        context.log(initiator + " detected a deadlock.");
        context.recordResult("Deadlock detected by " + initiator);
    }

    private void scheduleProbe(
            ProtocolContext context,
            NodeId initiator,
            NodeId sender,
            NodeId receiver,
            ResourceId waitingFor,
            long round
    ) {
        ProtocolMessage message = ProtocolMessage.of("deadlock", "deadlock", sender, receiver, DeadlockMessageType.PROBE, round)
                .withAttribute("initiator", initiator.toString())
                .withAttribute("resource", waitingFor.toString())
                .withIdSuffix("for-" + waitingFor);
        context.scheduleAt(
                context.time(),
                "Send deadlock probe from " + sender + " to " + receiver,
                scheduledContext -> sendProbe(scheduledContext, message, initiator, sender, receiver, waitingFor)
        );
    }

    private List<WaitForEdgeSnapshot> outgoingWaitForEdges(
            MutableSimulationState state,
            NodeSnapshot node,
            long scheduledTime
    ) {
        List<WaitForEdgeSnapshot> edges = new ArrayList<>();
        for (ResourceId resourceId : node.deadlockState().awaitedResources()) {
            ResourceSnapshot resource = state.resource(resourceId);
            if (resource != null && resource.owner() != null && !resource.owner().equals(node.nodeId())) {
                edges.add(new WaitForEdgeSnapshot(node.nodeId(), resource.owner(), resourceId, scheduledTime));
            }
        }
        return edges;
    }

    private DeadlockStateSnapshot withKnownGraph(
            DeadlockStateSnapshot current,
            List<WaitForEdgeSnapshot> edges,
            boolean deadlockDetected
    ) {
        List<WaitForEdgeSnapshot> graph = new ArrayList<>(current.knownWaitForGraph());
        graph.addAll(edges);
        return new DeadlockStateSnapshot(
                current.heldResources(),
                current.awaitedResources(),
                List.copyOf(graph),
                current.activeProbes(),
                current.deadlockDetected() || deadlockDetected
        );
    }

    private DeadlockStateSnapshot withProbe(DeadlockStateSnapshot current, ProbeSnapshot probe) {
        Set<ProbeSnapshot> probes = new LinkedHashSet<>(current.activeProbes());
        probes.add(probe);
        return new DeadlockStateSnapshot(
                current.heldResources(),
                current.awaitedResources(),
                current.knownWaitForGraph(),
                List.copyOf(probes),
                current.deadlockDetected()
        );
    }

    private enum DeadlockMessageType implements ProtocolMessageType {
        PROBE;

        @Override
        public MessageKind kind() {
            return MessageKind.DEADLOCK_PROBE;
        }

        @Override
        public long defaultDelay() {
            return NETWORK_DELAY;
        }
    }
}
