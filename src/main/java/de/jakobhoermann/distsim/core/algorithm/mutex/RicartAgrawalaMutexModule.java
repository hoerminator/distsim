package de.jakobhoermann.distsim.core.algorithm.mutex;

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

public final class RicartAgrawalaMutexModule implements AlgorithmModule {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            AlgorithmIds.RICART_AGRAWALA_MUTEX,
            AlgorithmCategory.MUTEX,
            "Ricart-Agrawala",
            RicartAgrawalaMutexController::new,
            ScenarioFactory::mutexScenario,
            TopologyProfile.COMPLETE_GRAPH,
            List.of(
                    new NodeMetadataDescriptor(NodeMetadata.LAMPORT_CLOCK, "Lamport clock", true),
                    new NodeMetadataDescriptor(NodeMetadata.MUTEX_STATE, "Mutex state", false)
            ),
            new AlgorithmInfo(
                    "Grant mutually exclusive access to the critical section using timestamped requests.",
                    "Lamport clock, request state, replies and deferred replies"
            )
    );

    @Override
    public AlgorithmDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
