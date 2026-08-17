package de.jakobhoermann.distsim.core.algorithm.mutex;

import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessage;
import de.jakobhoermann.distsim.core.algorithm.ProtocolMessageType;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.MutexRequestSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.MutexStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RicartAgrawalaMutexProtocol {
    public static final RicartAgrawalaMutexProtocol INSTANCE = new RicartAgrawalaMutexProtocol();

    private static final long NETWORK_DELAY = 10L;
    private static final long CRITICAL_SECTION_DURATION = 25L;

    private RicartAgrawalaMutexProtocol() {
    }

    public void initiateRequest(ProtocolContext context, NodeId initiator, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot node = state.node(initiator);
        if (node == null || !node.alive()) {
            return;
        }
        if (node.mutexState().requestingCriticalSection() || node.mutexState().inCriticalSection()) {
            context.log(initiator + " already has an active mutex request.");
            return;
        }

        long requestTimestamp = node.lamportClock() + 1;
        state.updateLamportClock(initiator, requestTimestamp);
        state.updateMutexState(initiator, ignored -> new MutexStateSnapshot(
                true,
                false,
                requestTimestamp,
                Set.of(),
                Set.of(),
                sortedRequests(List.of(new MutexRequestSnapshot(initiator, requestTimestamp)))
        ));
        context.log(initiator + " requested the critical section with timestamp " + requestTimestamp);

        List<NodeSnapshot> receivers = state.aliveNodes().stream()
                .filter(candidate -> !candidate.nodeId().equals(initiator))
                .toList();
        if (receivers.isEmpty()) {
            scheduleEnterCriticalSection(context, context.time() + 1, initiator, round);
            return;
        }

        for (NodeSnapshot receiver : receivers) {
            scheduleMessage(context, context.time(), initiator, receiver.nodeId(), round, MutexMessageType.REQUEST, requestTimestamp);
        }
    }

    private void sendMessage(ProtocolContext context, ProtocolMessage message, long requestTimestamp) {
        NodeSnapshot sender = context.state().node(message.from());
        if (sender == null || !sender.alive()) {
            context.message(message).send(ignored -> {
            });
            return;
        }

        if (message.type() == MutexMessageType.REPLY) {
            context.state().updateMutexState(message.from(), current -> new MutexStateSnapshot(
                    current.requestingCriticalSection(),
                    current.inCriticalSection(),
                    current.ownRequestTimestamp(),
                    current.acknowledgementsReceived(),
                    current.deferredReplies(),
                    withoutRequest(current.requestQueue(), message.to())
            ));
        }

        long sendTimestamp = Math.max(sender.lamportClock(), requestTimestamp) + 1;
        context.state().updateLamportClock(message.from(), sendTimestamp);
        context.message(message)
                .lamportTimestamp(sendTimestamp)
                .attribute("timestamp", Long.toString(requestTimestamp))
                .deliverOnlyToAliveReceiver()
                .sentLogLine(message.from() + " sent mutex " + message.payload() + " to " + message.to())
                .lostLogLine("lost mutex " + message.payload() + " from " + message.from() + " to " + message.to())
                .receiveDescription("Receive mutex " + message.payload() + " at " + message.to())
                .send(receiveContext -> receiveMessage(receiveContext, message, requestTimestamp));
    }

    private void receiveMessage(ProtocolContext context, ProtocolMessage message, long requestTimestamp) {
        MutableSimulationState state = context.state();
        NodeSnapshot receiver = state.node(message.to());
        if (receiver == null || !receiver.alive()) {
            return;
        }

        long receivedClock = Math.max(receiver.lamportClock(), requestTimestamp) + 1;
        state.updateLamportClock(message.to(), receivedClock);
        context.log(message.to() + " received mutex " + message.payload() + " from " + message.from());

        switch ((MutexMessageType) message.type()) {
            case REQUEST -> receiveRequest(context, message.from(), message.to(), message.round(), requestTimestamp);
            case REPLY -> receiveReply(context, message.from(), message.to(), message.round());
        }
    }

    public void enterCriticalSection(ProtocolContext context, NodeId nodeId, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot node = state.node(nodeId);
        if (node == null || !node.alive()) {
            return;
        }
        if (!hasAllReplies(state, node)) {
            return;
        }

        state.updateMutexState(nodeId, current -> new MutexStateSnapshot(
                false,
                true,
                current.ownRequestTimestamp(),
                current.acknowledgementsReceived(),
                current.deferredReplies(),
                current.requestQueue()
        ));
        context.log(nodeId + " entered the critical section.");
        context.recordResult(nodeId + " entered the critical section");
        scheduleReleaseCriticalSection(context, context.time() + CRITICAL_SECTION_DURATION, nodeId, round);
    }

    public void releaseCriticalSection(ProtocolContext context, NodeId nodeId, long round) {
        MutableSimulationState state = context.state();
        NodeSnapshot node = state.node(nodeId);
        if (node == null || !node.alive() || !node.mutexState().inCriticalSection()) {
            return;
        }

        long requestTimestamp = node.mutexState().ownRequestTimestamp() == null
                ? node.lamportClock()
                : node.mutexState().ownRequestTimestamp();
        Set<NodeId> deferredReplies = new LinkedHashSet<>(node.mutexState().deferredReplies());
        state.updateMutexState(nodeId, current -> new MutexStateSnapshot(
                false,
                false,
                null,
                Set.of(),
                Set.of(),
                withoutRequest(current.requestQueue(), nodeId)
        ));
        context.log(nodeId + " released the critical section.");

        for (NodeId receiver : deferredReplies) {
            scheduleMessage(context, context.time() + 1, nodeId, receiver, round, MutexMessageType.REPLY, requestTimestamp);
        }
    }

    private void receiveRequest(
            ProtocolContext context,
            NodeId requester,
            NodeId receiver,
            long round,
            long requestTimestamp
    ) {
        MutableSimulationState state = context.state();
        NodeSnapshot receiverNode = state.node(receiver);
        if (receiverNode == null) {
            return;
        }

        state.updateMutexState(receiver, current -> new MutexStateSnapshot(
                current.requestingCriticalSection(),
                current.inCriticalSection(),
                current.ownRequestTimestamp(),
                current.acknowledgementsReceived(),
                current.deferredReplies(),
                withRequest(current.requestQueue(), new MutexRequestSnapshot(requester, requestTimestamp))
        ));

        NodeSnapshot updatedReceiver = state.node(receiver);
        if (shouldDeferReply(updatedReceiver, requester, requestTimestamp)) {
            state.updateMutexState(receiver, current -> {
                LinkedHashSet<NodeId> deferred = new LinkedHashSet<>(current.deferredReplies());
                deferred.add(requester);
                return new MutexStateSnapshot(
                        current.requestingCriticalSection(),
                        current.inCriticalSection(),
                        current.ownRequestTimestamp(),
                        current.acknowledgementsReceived(),
                        Set.copyOf(deferred),
                        current.requestQueue()
                );
            });
            context.log(receiver + " deferred mutex REPLY to " + requester);
            return;
        }

        scheduleMessage(context, context.time() + 1, receiver, requester, round, MutexMessageType.REPLY, requestTimestamp);
    }

    private void receiveReply(
            ProtocolContext context,
            NodeId replier,
            NodeId receiver,
            long round
    ) {
        MutableSimulationState state = context.state();
        state.updateMutexState(receiver, current -> {
            LinkedHashSet<NodeId> acknowledgements = new LinkedHashSet<>(current.acknowledgementsReceived());
            acknowledgements.add(replier);
            return new MutexStateSnapshot(
                    current.requestingCriticalSection(),
                    current.inCriticalSection(),
                    current.ownRequestTimestamp(),
                    Set.copyOf(acknowledgements),
                    current.deferredReplies(),
                    current.requestQueue()
            );
        });

        NodeSnapshot receiverNode = state.node(receiver);
        if (receiverNode != null && receiverNode.mutexState().requestingCriticalSection()
                && hasAllReplies(state, receiverNode)) {
            scheduleEnterCriticalSection(context, context.time() + 1, receiver, round);
        }
    }

    private boolean shouldDeferReply(NodeSnapshot receiver, NodeId requester, long requestTimestamp) {
        if (receiver.mutexState().inCriticalSection()) {
            return true;
        }
        if (!receiver.mutexState().requestingCriticalSection()
                || receiver.mutexState().ownRequestTimestamp() == null) {
            return false;
        }
        return requestHasPriority(
                receiver.nodeId(),
                receiver.mutexState().ownRequestTimestamp(),
                requester,
                requestTimestamp
        );
    }

    private boolean requestHasPriority(NodeId firstNode, long firstTimestamp, NodeId secondNode, long secondTimestamp) {
        if (firstTimestamp != secondTimestamp) {
            return firstTimestamp < secondTimestamp;
        }
        return firstNode.value().compareTo(secondNode.value()) < 0;
    }

    private boolean hasAllReplies(MutableSimulationState state, NodeSnapshot node) {
        Set<NodeId> requiredReplies = new LinkedHashSet<>();
        for (NodeSnapshot candidate : state.aliveNodes()) {
            if (!candidate.nodeId().equals(node.nodeId())) {
                requiredReplies.add(candidate.nodeId());
            }
        }
        return node.mutexState().acknowledgementsReceived().containsAll(requiredReplies);
    }

    private void scheduleMessage(
            ProtocolContext context,
            long scheduledTime,
            NodeId from,
            NodeId to,
            long round,
            MutexMessageType type,
            long requestTimestamp
    ) {
        ProtocolMessage message = ProtocolMessage.of("mutex", "mutex", from, to, type, round)
                .withAttribute("timestamp", Long.toString(requestTimestamp));
        context.scheduleAt(
                scheduledTime,
                message.sendDescription(),
                scheduledContext -> sendMessage(scheduledContext, message, requestTimestamp)
        );
    }

    private void scheduleEnterCriticalSection(
            ProtocolContext context,
            long scheduledTime,
            NodeId nodeId,
            long round
    ) {
        context.scheduleAt(
                scheduledTime,
                "Enter critical section at " + nodeId,
                scheduledContext -> enterCriticalSection(scheduledContext, nodeId, round)
        );
    }

    private void scheduleReleaseCriticalSection(
            ProtocolContext context,
            long scheduledTime,
            NodeId nodeId,
            long round
    ) {
        context.scheduleAt(
                scheduledTime,
                "Release critical section at " + nodeId,
                scheduledContext -> releaseCriticalSection(scheduledContext, nodeId, round)
        );
    }

    private List<MutexRequestSnapshot> withRequest(
            List<MutexRequestSnapshot> requests,
            MutexRequestSnapshot request
    ) {
        List<MutexRequestSnapshot> updated = new ArrayList<>(withoutRequest(requests, request.requester()));
        updated.add(request);
        return sortedRequests(updated);
    }

    private List<MutexRequestSnapshot> withoutRequest(List<MutexRequestSnapshot> requests, NodeId requester) {
        return requests.stream()
                .filter(request -> !request.requester().equals(requester))
                .toList();
    }

    private List<MutexRequestSnapshot> sortedRequests(List<MutexRequestSnapshot> requests) {
        return requests.stream()
                .sorted(Comparator
                        .comparingLong(MutexRequestSnapshot::requestTimestamp)
                        .thenComparing(request -> request.requester().value()))
                .toList();
    }

    private enum MutexMessageType implements ProtocolMessageType {
        REQUEST(MessageKind.MUTEX_REQUEST),
        REPLY(MessageKind.MUTEX_REPLY);

        private final MessageKind kind;

        MutexMessageType(MessageKind kind) {
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
