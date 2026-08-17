package de.jakobhoermann.distsim.core.algorithm.deadlock;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmInfo;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmModule;
import de.jakobhoermann.distsim.core.algorithm.NodeMetadata;
import de.jakobhoermann.distsim.core.algorithm.NodeMetadataDescriptor;
import de.jakobhoermann.distsim.core.algorithm.TopologyProfile;
import de.jakobhoermann.distsim.core.model.AlgorithmCategory;
import de.jakobhoermann.distsim.core.scenario.ScenarioFactory;

import java.util.List;

public final class DeadlockDetectionModule implements AlgorithmModule {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            AlgorithmIds.LAMPORT_DEADLOCK,
            AlgorithmCategory.DEADLOCK_DETECTION,
            "Lamport Deadlock",
            DeadlockDetectionController::new,
            ScenarioFactory::deadlockDetectionScenario,
            TopologyProfile.RESOURCE_GRAPH,
            List.of(new NodeMetadataDescriptor(NodeMetadata.DEADLOCK_RESOURCES, "Resources", true)),
            new AlgorithmInfo(
                    "Detect cycles in the wait-for graph using probe messages.",
                    "Held resources, requested resources, probes and detected deadlock state"
            )
    );

    @Override
    public AlgorithmDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
