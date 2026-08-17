package de.jakobhoermann.distsim.core.algorithm.election;

import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessage;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessageType;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.ElectionStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RingElectionProtocol {
    public static final RingElectionProtocol INSTANCE = new RingElectionProtocol();

    private static final long NETWORK_DELAY = 10L;

    private RingElectionProtocol() {
    }

    public void startAlgorithm(ProtocolContext context, NodeId initiator, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot starter = state.node(initiator);
        if (starter == null || !starter.alive()) {
            return;
        }

        LinkedHashSet<NodeId> participants = new LinkedHashSet<>();
        participants.add(initiator);
        state.updateElectionState(initiator, ignored -> new ElectionStateSnapshot(
                true,
                false,
                null,
                round,
                participants,
                initiator,
                false
        ));
        context.log(initiator + " started Ring election round " + round);
        forwardToken(context, initiator, initiator, initiator, participants, round, RingMessageType.ELECTION);
    }

    private void sendMessage(
            ProtocolContext context,
            ProtocolMessage message,
            NodeId initiator,
            NodeId candidate,
            Set<NodeId> participants
    ) {
        context.message(message)
                .hopCount(participants.size())
                .sentLogLine(message.from() + " sent Ring " + message.payload()
                        + " token to " + message.to() + " with candidate " + candidate)
                .lostLogLine("lost Ring " + message.payload() + " token from "
                        + message.from() + " to " + message.to())
                .receiveDescription("Receive Ring " + message.payload() + " token at " + message.to())
                .deliverOnlyToAliveReceiver()
                .onUnavailableReceiver(failedContext -> abortElection(failedContext.state(), participants, message.round()))
                .send(receiveContext -> receiveMessage(
                        receiveContext,
                        message,
                        initiator,
                        candidate,
                        participants
                ));
    }

    private void receiveMessage(
            ProtocolContext context,
            ProtocolMessage message,
            NodeId initiator,
            NodeId candidate,
            Set<NodeId> participants
    ) {
        NodeSnapshot receiver = context.state().node(message.to());
        if (receiver == null) {
            return;
        }

        switch ((RingMessageType) message.type()) {
            case COORDINATOR -> handleCoordinatorToken(
                    context,
                    receiver,
                    initiator,
                    candidate,
                    participants,
                    message.round()
            );
            case ELECTION -> handleElectionToken(
                    context,
                    receiver,
                    initiator,
                    candidate,
                    participants,
                    message.round()
            );
        }
    }

    private void handleElectionToken(
            ProtocolContext context,
            NodeSnapshot receiver,
            NodeId initiator,
            NodeId candidate,
            Set<NodeId> participants,
            long round
    ) {
        LinkedHashSet<NodeId> updatedParticipants = new LinkedHashSet<>(participants);
        updatedParticipants.add(receiver.nodeId());
        NodeId updatedCandidate = higherPriorityNode(context.state(), candidate, receiver.nodeId());

        context.state().updateElectionState(receiver.nodeId(), ignored -> new ElectionStateSnapshot(
                true,
                false,
                null,
                round,
                updatedParticipants,
                updatedCandidate,
                false
        ));
        context.log(receiver.nodeId() + " received Ring ELECTION token; candidate is " + updatedCandidate);

        if (receiver.nodeId().equals(initiator)) {
            context.state().updateElectionState(initiator, current -> new ElectionStateSnapshot(
                    false,
                    initiator.equals(updatedCandidate),
                    updatedCandidate,
                    null,
                    current.participants(),
                    updatedCandidate,
                    false
            ));
            context.log("Ring election selected " + updatedCandidate + " as coordinator");
            context.recordResult("Coordinator " + updatedCandidate + " elected");
            forwardToken(context, initiator, initiator, updatedCandidate, updatedParticipants, round, RingMessageType.COORDINATOR);
            return;
        }

        forwardToken(context, receiver.nodeId(), initiator, updatedCandidate, updatedParticipants, round, RingMessageType.ELECTION);
    }

    private void handleCoordinatorToken(
            ProtocolContext context,
            NodeSnapshot receiver,
            NodeId initiator,
            NodeId coordinator,
            Set<NodeId> participants,
            long round
    ) {
        context.state().updateElectionState(receiver.nodeId(), current -> new ElectionStateSnapshot(
                false,
                receiver.nodeId().equals(coordinator),
                coordinator,
                null,
                current.participants().isEmpty() ? participants : current.participants(),
                coordinator,
                false
        ));
        context.log(receiver.nodeId() + " recognizes " + coordinator + " as Ring coordinator");

        if (!receiver.nodeId().equals(initiator)) {
            forwardToken(context, receiver.nodeId(), initiator, coordinator, participants, round, RingMessageType.COORDINATOR);
        }
    }

    private void forwardToken(
            ProtocolContext context,
            NodeId from,
            NodeId initiator,
            NodeId candidate,
            Set<NodeId> participants,
            long round,
            RingMessageType type
    ) {
        NodeId next = directAliveSuccessor(context.state(), from);
        if (next == null) {
            context.log("Ring election aborted because the successor of " + from + " is not available");
            context.recordResult("Aborted: successor of " + from + " unavailable");
            abortElection(context.state(), participants, round);
            return;
        }

        ProtocolMessage message = ProtocolMessage.of("ring", "Ring", from, next, type, round)
                .withAttribute("initiator", initiator.toString())
                .withAttribute("candidate", candidate.toString());
        context.scheduleAt(
                context.time(),
                "Send Ring " + type.payload().toLowerCase() + " token from " + from + " to " + next,
                scheduledContext -> sendMessage(scheduledContext, message, initiator, candidate, participants)
        );
    }

    private NodeId directAliveSuccessor(MutableSimulationState state, NodeId from) {
        NodeSnapshot current = state.node(from);
        if (current == null || current.ringSuccessor() == null) {
            return null;
        }

        NodeId next = current.ringSuccessor();
        NodeSnapshot nextNode = state.node(next);
        return nextNode != null && nextNode.alive() ? next : null;
    }

    private void abortElection(MutableSimulationState state, Set<NodeId> participants, long round) {
        for (NodeId participant : participants) {
            state.updateElectionState(participant, current -> {
                if (current.activeElectionRound() == null || !current.activeElectionRound().equals(round)) {
                    return current;
                }
                return new ElectionStateSnapshot(
                        false,
                        false,
                        current.knownCoordinator(),
                        null,
                        current.participants(),
                        current.highestCandidateSeen(),
                        false
                );
            });
        }
    }

    private NodeId higherPriorityNode(MutableSimulationState state, NodeId first, NodeId second) {
        NodeSnapshot firstNode = state.node(first);
        NodeSnapshot secondNode = state.node(second);
        if (firstNode == null) {
            return second;
        }
        if (secondNode == null) {
            return first;
        }
        return secondNode.electionPriority() > firstNode.electionPriority() ? second : first;
    }

    private enum RingMessageType implements ProtocolMessageType {
        ELECTION(MessageKind.RING_TOKEN),
        COORDINATOR(MessageKind.COORDINATOR);

        private final MessageKind kind;

        RingMessageType(MessageKind kind) {
            this.kind = kind;
        }

        @Override
        public MessageKind kind() {
            return kind;
        }

        @Override
        public long defaultDelay() {
            return NETWORK_DELAY;
        }
    }
}
