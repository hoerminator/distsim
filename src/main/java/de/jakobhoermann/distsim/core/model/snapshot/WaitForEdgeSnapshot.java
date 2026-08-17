package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;

public record WaitForEdgeSnapshot(
        NodeId waitingProcess,
        NodeId blockingProcess,
        ResourceId resourceId,
        long sinceTimestamp
) {
}
