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

public final class BullyElectionModule implements AlgorithmModule {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            AlgorithmIds.BULLY_ELECTION,
            AlgorithmCategory.ELECTION,
            "Bully Election",
            BullyElectionController::new,
            ScenarioFactory::starterScenario,
            TopologyProfile.COMPLETE_GRAPH,
            List.of(new NodeMetadataDescriptor(NodeMetadata.ELECTION_PRIORITY, "Priority", true)),
            new AlgorithmInfo(
                    "Elect the process with the highest priority as coordinator.",
                    "Election priority and coordinator state"
            )
    );

    @Override
    public AlgorithmDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
