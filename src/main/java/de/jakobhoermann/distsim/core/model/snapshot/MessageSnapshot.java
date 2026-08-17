package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;

import java.util.Map;

public record MessageSnapshot(
        String id,
        NodeId from,
        NodeId to,
        AlgorithmId algorithm,
        MessageKind kind,
        String payload,
        long scheduledDeliveryTime,
        long lamportTimestamp,
        String correlationId,
        int hopCount,
        Map<String, String> attributes
) {
}
