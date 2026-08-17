package de.jakobhoermann.distsim.core.scenario;

import de.jakobhoermann.distsim.core.model.ClusterTopology;

public record Scenario(
        String name,
        ClusterTopology topology,
        long seed
) {
}
