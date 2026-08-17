package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.MessageSnapshot;

import java.util.Map;
import java.util.function.Consumer;

public final class MessageBus {
    private MessageBus() {
    }

    public static boolean send(
            MutableSimulationState state,
            long scheduledTime,
            MessageEnvelope envelope,
            long fallbackDelay,
            String lostLogLine,
            String sentLogLine,
            String receiveDescription,
            Consumer<MutableSimulationState> receiveAction
    ) {
        if (state.shouldLoseMessage()) {
            state.recordLostMessage(envelope.kind());
            state.appendLog("t=" + scheduledTime + " " + lostLogLine);
            return false;
        }

        long deliveryTime = state.messageDeliveryTime(scheduledTime, fallbackDelay);
        state.recordSentMessage(envelope.kind());
        state.addMessage(new MessageSnapshot(
                envelope.messageId(),
                envelope.from(),
                envelope.to(),
                envelope.algorithm(),
                envelope.kind(),
                envelope.payload(),
                deliveryTime,
                envelope.lamportTimestamp(),
                envelope.correlationId(),
                envelope.hopCount(),
                envelope.attributes()
        ));
        state.appendLog("t=" + scheduledTime + " " + sentLogLine);
        state.schedule(new AlgorithmEvent(deliveryTime, receiveDescription, receiveAction));
        return true;
    }

    public record MessageEnvelope(
            String messageId,
            NodeId from,
            NodeId to,
            AlgorithmId algorithm,
            MessageKind kind,
            String payload,
            long lamportTimestamp,
            String correlationId,
            int hopCount,
            Map<String, String> attributes
    ) {
        public MessageEnvelope {
            attributes = Map.copyOf(attributes);
        }
    }
}
