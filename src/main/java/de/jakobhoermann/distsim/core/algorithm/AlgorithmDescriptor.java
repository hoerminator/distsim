package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.model.AlgorithmCategory;
import de.jakobhoermann.distsim.core.scenario.Scenario;

import java.util.List;
import java.util.function.Supplier;

public record AlgorithmDescriptor(
        AlgorithmId id,
        AlgorithmCategory category,
        String displayName,
        Supplier<AlgorithmController> controllerFactory,
        Supplier<Scenario> defaultScenario,
        TopologyProfile topologyProfile,
        List<NodeMetadataDescriptor> nodeMetadata,
        AlgorithmInfo info
) {
    public AlgorithmDescriptor {
        nodeMetadata = List.copyOf(nodeMetadata);
        if (info == null) {
            info = AlgorithmInfo.empty();
        }
    }

    public boolean isElection() {
        return category == AlgorithmCategory.ELECTION;
    }

    public boolean isMutex() {
        return category == AlgorithmCategory.MUTEX;
    }

    public boolean isDeadlockDetection() {
        return category == AlgorithmCategory.DEADLOCK_DETECTION;
    }

    public boolean usesMetadata(NodeMetadata metadata) {
        return nodeMetadata.stream()
                .anyMatch(descriptor -> descriptor.metadata() == metadata);
    }
}
