package de.jakobhoermann.distsim.core.algorithm.election;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessage;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessageType;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.ElectionStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;

import java.util.LinkedHashSet;
import java.util.List;

public final class BullyElectionProtocol {
    public static final BullyElectionProtocol INSTANCE = new BullyElectionProtocol();

    private static final long NETWORK_DELAY = 10L;
    private static final long ANSWER_DELAY = 8L;
    private static final long ELECTION_TIMEOUT = 40L;

    private BullyElectionProtocol() {
    }

    public void startAlgorithm(ProtocolContext context, NodeId initiator, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot starter = state.node(initiator);
        if (starter == null || !starter.alive()) {
            return;
        }

        List<NodeSnapshot> higherPriorityNodes = state.higherPriorityNodes(initiator);
        boolean hasHigherPriorityAliveNode = higherPriorityNodes.stream().anyMatch(NodeSnapshot::alive);
        state.updateElectionState(initiator, ignored -> new ElectionStateSnapshot(
                true,
                false,
                null,
                round,
                new LinkedHashSet<>(),
                initiator,
                hasHigherPriorityAliveNode
        ));
        context.log(initiator + " started Bully election");

        for (NodeSnapshot receiver : higherPriorityNodes) {
            scheduleMessage(context, context.time(), initiator, receiver.nodeId(), round, BullyMessageType.ELECTION);
        }

        if (higherPriorityNodes.isEmpty()) {
            scheduleDeclareCoordinator(context, context.time() + 1, initiator, round);
            return;
        }
        scheduleTimeout(context, context.time() + ELECTION_TIMEOUT, initiator, round);
    }

    private void sendMessage(ProtocolContext context, ProtocolMessage message) {
        context.message(message)
                .deliverOnlyToAliveReceiver()
                .send(receiveContext -> receiveMessage(receiveContext, message));
    }

    private void receiveMessage(ProtocolContext context, ProtocolMessage message) {
        MutableSimulationState state = context.state();
        NodeSnapshot receiver = state.node(message.to());
        if (receiver == null) {
            return;
        }

        context.log(message.to() + " received " + message.payload() + " from " + message.from());
        switch ((BullyMessageType) message.type()) {
            case ELECTION -> handleElectionMessage(context, receiver, message.from(), message.round());
            case ANSWER -> handleAnswerMessage(state, receiver, message.from());
            case COORDINATOR -> handleCoordinatorMessage(context, message.from(), message.to());
        }
    }

    public void handleTimeout(ProtocolContext context, NodeId nodeId, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot node = state.node(nodeId);
        if (node == null || !node.alive()) {
            return;
        }

        ElectionStateSnapshot electionState = node.electionState();
        if (!electionState.electionInProgress() || electionState.activeElectionRound() == null
                || !electionState.activeElectionRound().equals(round)) {
            return;
        }

        if (electionState.participants().isEmpty()) {
            scheduleDeclareCoordinator(context, context.time() + 1, nodeId, round);
            return;
        }

        context.log(nodeId + " received ANSWER and keeps waiting for a coordinator announcement");
    }

    public void declareCoordinator(ProtocolContext context, NodeId coordinator, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot coordinatorNode = state.node(coordinator);
        if (coordinatorNode == null || !coordinatorNode.alive()) {
            return;
        }

        state.updateElectionState(coordinator, current -> new ElectionStateSnapshot(
                false,
                true,
                coordinator,
                null,
                new LinkedHashSet<>(current.participants()),
                coordinator,
                false
        ));
        context.log(coordinator + " became coordinator");
        context.recordResult("Coordinator " + coordinator + " elected");

        for (NodeSnapshot node : state.aliveNodes()) {
            if (!node.nodeId().equals(coordinator)) {
                scheduleMessage(context, context.time(), coordinator, node.nodeId(), round, BullyMessageType.COORDINATOR);
            }
        }
    }

    private void handleElectionMessage(
            ProtocolContext context,
            NodeSnapshot receiver,
            NodeId from,
            long round
    ) {
        scheduleMessage(
                context,
                context.time() + 1,
                receiver.nodeId(),
                from,
                round,
                BullyMessageType.ANSWER
        );

        ElectionStateSnapshot electionState = receiver.electionState();
        if (!electionState.electionInProgress()) {
            scheduleInitiate(context, context.time() + 2, receiver.nodeId(), context.time() + 2);
        }
    }

    private void scheduleInitiate(ProtocolContext context, long scheduledTime, NodeId initiator, long round) {
        context.scheduleAt(
                scheduledTime,
                "Initiate Bully election at " + initiator,
                scheduledContext -> startAlgorithm(scheduledContext, initiator, round)
        );
    }

    private void scheduleMessage(
            ProtocolContext context,
            long scheduledTime,
            NodeId from,
            NodeId to,
            long round,
            BullyMessageType type
    ) {
        ProtocolMessage message = ProtocolMessage.of("bully", "Bully", from, to, type, round);
        context.scheduleAt(
                scheduledTime,
                message.sendDescription(),
                scheduledContext -> sendMessage(scheduledContext, message)
        );
    }

    private void scheduleTimeout(ProtocolContext context, long scheduledTime, NodeId nodeId, long round) {
        context.scheduleAt(
                scheduledTime,
                "Bully election timeout for " + nodeId,
                scheduledContext -> handleTimeout(scheduledContext, nodeId, round)
        );
    }

    private void scheduleDeclareCoordinator(
            ProtocolContext context,
            long scheduledTime,
            NodeId coordinator,
            long round
    ) {
        context.scheduleAt(
                scheduledTime,
                "Declare Bully coordinator " + coordinator,
                scheduledContext -> declareCoordinator(scheduledContext, coordinator, round)
        );
    }

    private void handleAnswerMessage(MutableSimulationState state, NodeSnapshot receiver, NodeId responder) {
        state.updateElectionState(receiver.nodeId(), current -> {
            LinkedHashSet<NodeId> participants = new LinkedHashSet<>(current.participants());
            participants.add(responder);
            NodeId highestCandidate = current.highestCandidateSeen();
            if (highestCandidate == null) {
                highestCandidate = responder;
            } else {
                NodeSnapshot currentHighest = state.node(highestCandidate);
                NodeSnapshot responderNode = state.node(responder);
                if (currentHighest == null || (responderNode != null
                        && responderNode.electionPriority() > currentHighest.electionPriority())) {
                    highestCandidate = responder;
                }
            }
            return new ElectionStateSnapshot(
                    true,
                    false,
                    null,
                    current.activeElectionRound(),
                    participants,
                    highestCandidate,
                    true
            );
        });
    }

    private void handleCoordinatorMessage(ProtocolContext context, NodeId from, NodeId to) {
        context.state().updateElectionState(to, current -> new ElectionStateSnapshot(
                false,
                false,
                from,
                null,
                current.participants(),
                from,
                false
        ));
        context.log(to + " recognizes " + from + " as coordinator");
    }

    private enum BullyMessageType implements ProtocolMessageType {
        ELECTION(MessageKind.ELECTION, NETWORK_DELAY),
        ANSWER(MessageKind.ANSWER, ANSWER_DELAY),
        COORDINATOR(MessageKind.COORDINATOR, NETWORK_DELAY);

        private final MessageKind kind;
        private final long defaultDelay;

        BullyMessageType(MessageKind kind, long defaultDelay) {
            this.kind = kind;
            this.defaultDelay = defaultDelay;
        }

        @Override
        public MessageKind kind() {
            return kind;
        }

        @Override
        public long defaultDelay() {
            return defaultDelay;
        }
    }
}
