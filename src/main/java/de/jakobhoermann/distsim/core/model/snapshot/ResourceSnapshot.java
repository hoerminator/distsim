package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;

public record ResourceSnapshot(
        ResourceId resourceId,
        String label,
        NodeId owner,
        boolean available
) {
}
