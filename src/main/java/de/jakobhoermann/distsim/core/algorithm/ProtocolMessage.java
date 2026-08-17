package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.model.NodeId;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProtocolMessage(
        String namespace,
        String displayName,
        NodeId from,
        NodeId to,
        ProtocolMessageType type,
        long round,
        long delay,
        Map<String, String> attributes,
        String idSuffix
) {
    public ProtocolMessage {
        attributes = Map.copyOf(attributes);
    }

    public static ProtocolMessage of(
            String namespace,
            String displayName,
            NodeId from,
            NodeId to,
            ProtocolMessageType type,
            long round
    ) {
        return new ProtocolMessage(
                namespace,
                displayName,
                from,
                to,
                type,
                round,
                type.defaultDelay(),
                Map.of(),
                ""
        );
    }

    public String payload() {
        return type.payload();
    }

    public ProtocolMessage withDelay(long delay) {
        return new ProtocolMessage(namespace, displayName, from, to, type, round, delay, attributes, idSuffix);
    }

    public ProtocolMessage withAttribute(String key, String value) {
        Map<String, String> updatedAttributes = new LinkedHashMap<>(attributes);
        updatedAttributes.put(key, value);
        return new ProtocolMessage(namespace, displayName, from, to, type, round, delay, updatedAttributes, idSuffix);
    }

    public ProtocolMessage withIdSuffix(String idSuffix) {
        return new ProtocolMessage(namespace, displayName, from, to, type, round, delay, attributes, idSuffix);
    }

    public String messageId(long scheduledTime) {
        String suffix = idSuffix == null || idSuffix.isBlank() ? "" : "-" + sanitize(idSuffix);
        return namespace + "-" + sanitize(payload()) + "-" + round + "-" + from + "-to-" + to + "-t" + scheduledTime + suffix;
    }

    public String correlationId() {
        return namespace + "-round-" + round;
    }

    public Map<String, String> messageAttributes() {
        Map<String, String> updatedAttributes = new LinkedHashMap<>();
        updatedAttributes.put("round", Long.toString(round));
        updatedAttributes.putAll(attributes);
        return updatedAttributes;
    }

    public String sendDescription() {
        return "Send " + displayName + " " + payload() + " from " + from + " to " + to;
    }

    private String sanitize(String value) {
        return value.toLowerCase().replace('_', '-').replaceAll("[^a-z0-9-]+", "-");
    }
}
