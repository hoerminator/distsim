package de.jakobhoermann.distsim.core.algorithm.election;

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

public final class RingElectionModule implements AlgorithmModule {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            AlgorithmIds.RING_ELECTION,
            AlgorithmCategory.ELECTION,
            "Ring Election",
            RingElectionController::new,
            ScenarioFactory::ringElectionScenario,
            TopologyProfile.RING,
            List.of(new NodeMetadataDescriptor(NodeMetadata.ELECTION_PRIORITY, "Priority", true)),
            new AlgorithmInfo(
                    "Circulate a token through the ring and elect the highest-priority process.",
                    "Ring successor, election priority and coordinator state"
            )
    );

    @Override
    public AlgorithmDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
