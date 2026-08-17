package de.jakobhoermann.distsim.core.algorithm.election;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmController;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.NodeId;

public final class RingElectionController implements AlgorithmController {
    @Override
    public AlgorithmId id() {
        return AlgorithmIds.RING_ELECTION;
    }

    @Override
    public String displayName() {
        return "Ring election";
    }

    @Override
    public void start(MutableSimulationState state, long scheduledTime, NodeId initiator, long round) {
        ProtocolContext.schedule(
                state,
                id(),
                scheduledTime,
                "Initiate ring election at " + initiator,
                context -> RingElectionProtocol.INSTANCE.startAlgorithm(context, initiator, round)
        );
    }
}
