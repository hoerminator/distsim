package de.jakobhoermann.distsim.core.model;

import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ResourceSnapshot;

import java.util.List;

public record ClusterTopology(
        List<NodeSnapshot> nodes,
        List<ResourceSnapshot> resources
) {
}
