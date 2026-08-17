package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ProtocolContext {
    private final MutableSimulationState state;
    private final AlgorithmId algorithmId;
    private final long time;

    public ProtocolContext(MutableSimulationState state, AlgorithmId algorithmId, long time) {
        this.state = state;
        this.algorithmId = algorithmId;
        this.time = time;
    }

    public static void schedule(
            MutableSimulationState state,
            AlgorithmId algorithmId,
            long scheduledTime,
            String description,
            Consumer<ProtocolContext> action
    ) {
        state.schedule(new AlgorithmEvent(
                scheduledTime,
                description,
                current -> action.accept(new ProtocolContext(current, algorithmId, current.time()))
        ));
    }

    public MutableSimulationState state() {
        return state;
    }

    public AlgorithmId algorithmId() {
        return algorithmId;
    }

    public long time() {
        return time;
    }

    public void log(String message) {
        state.appendLog("t=" + time + " " + message);
    }

    public void recordResult(String result) {
        state.recordRunResult(result);
    }

    public void scheduleAt(long scheduledTime, String description, Consumer<ProtocolContext> action) {
        schedule(state, algorithmId, scheduledTime, description, action);
    }

    public void scheduleAfter(long delay, String description, Consumer<ProtocolContext> action) {
        scheduleAt(time + delay, description, action);
    }

    public MessageBuilder message(String messageId, NodeId from, NodeId to, String payload) {
        return new MessageBuilder(messageId, from, to, payload);
    }

    public MessageBuilder message(ProtocolMessage message) {
        return message(message.messageId(time), message.from(), message.to(), message.payload())
                .kind(message.type().kind())
                .fallbackDelay(message.delay())
                .correlationId(message.correlationId())
                .attributes(message.messageAttributes())
                .lostLogLine("lost " + message.displayName() + " " + message.payload()
                        + " from " + message.from() + " to " + message.to())
                .sentLogLine(message.from() + " sent " + message.displayName() + " "
                        + message.payload() + " to " + message.to())
                .receiveDescription("Receive " + message.displayName() + " " + message.payload()
                        + " at " + message.to());
    }

    public final class MessageBuilder {
        private final String messageId;
        private final NodeId from;
        private final NodeId to;
        private final String payload;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private MessageKind kind = MessageKind.USER_PAYLOAD;
        private long fallbackDelay = 10L;
        private long lamportTimestamp = time;
        private String correlationId = algorithmId + "-" + time;
        private int hopCount = 1;
        private String lostLogLine;
        private String sentLogLine;
        private String receiveDescription;
        private boolean requireAliveReceiver;
        private boolean deliverOnlyToAliveReceiver;
        private Consumer<ProtocolContext> unavailableReceiverAction;

        private MessageBuilder(String messageId, NodeId from, NodeId to, String payload) {
            this.messageId = messageId;
            this.from = from;
            this.to = to;
            this.payload = payload;
        }

        public MessageBuilder kind(MessageKind kind) {
            this.kind = kind;
            return this;
        }

        public MessageBuilder fallbackDelay(long fallbackDelay) {
            this.fallbackDelay = fallbackDelay;
            return this;
        }

        public MessageBuilder lamportTimestamp(long lamportTimestamp) {
            this.lamportTimestamp = lamportTimestamp;
            return this;
        }

        public MessageBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public MessageBuilder hopCount(int hopCount) {
            this.hopCount = hopCount;
            return this;
        }

        public MessageBuilder attribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public MessageBuilder attributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public MessageBuilder lostLogLine(String lostLogLine) {
            this.lostLogLine = lostLogLine;
            return this;
        }

        public MessageBuilder sentLogLine(String sentLogLine) {
            this.sentLogLine = sentLogLine;
            return this;
        }

        public MessageBuilder receiveDescription(String receiveDescription) {
            this.receiveDescription = receiveDescription;
            return this;
        }

        public MessageBuilder requireAliveReceiver() {
            this.requireAliveReceiver = true;
            return this;
        }

        public MessageBuilder deliverOnlyToAliveReceiver() {
            this.deliverOnlyToAliveReceiver = true;
            return this;
        }

        public MessageBuilder onUnavailableReceiver(Consumer<ProtocolContext> unavailableReceiverAction) {
            this.unavailableReceiverAction = unavailableReceiverAction;
            return this;
        }

        public boolean send(Consumer<ProtocolContext> receiveAction) {
            if (!isAlive(from)) {
                log("dropped " + payload + " because sender " + from + " is off");
                return false;
            }
            if (state.node(to) == null) {
                log("dropped " + payload + " because receiver " + to + " does not exist");
                return false;
            }
            if (requireAliveReceiver && !isAlive(to)) {
                log("dropped " + payload + " because receiver " + to + " is unavailable");
                return false;
            }

            return MessageBus.send(
                    state,
                    time,
                    new MessageBus.MessageEnvelope(
                            messageId,
                            from,
                            to,
                            algorithmId,
                            kind,
                            payload,
                            lamportTimestamp,
                            correlationId,
                            hopCount,
                            attributes
                    ),
                    fallbackDelay,
                    lostLogLine == null ? "lost " + payload + " from " + from + " to " + to : lostLogLine,
                    sentLogLine == null ? from + " sent " + payload + " to " + to : sentLogLine,
                    receiveDescription == null ? "Receive " + payload + " at " + to : receiveDescription,
                    current -> {
                        if (!current.hasMessage(messageId)) {
                            return;
                        }
                        current.removeMessage(messageId);
                        ProtocolContext receiveContext = new ProtocolContext(current, algorithmId, current.time());
                        if (deliverOnlyToAliveReceiver && !receiveContext.isAlive(to)) {
                            receiveContext.log(payload + " could not be delivered to " + to);
                            if (unavailableReceiverAction != null) {
                                unavailableReceiverAction.accept(receiveContext);
                            }
                            return;
                        }
                        receiveAction.accept(receiveContext);
                    }
            );
        }
    }

    public boolean isAlive(NodeId nodeId) {
        return state.node(nodeId) != null && state.node(nodeId).alive();
    }
}
