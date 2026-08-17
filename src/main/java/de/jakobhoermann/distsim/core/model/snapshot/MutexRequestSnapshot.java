package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.NodeId;

public record MutexRequestSnapshot(
        NodeId requester,
        long requestTimestamp
) {
}
