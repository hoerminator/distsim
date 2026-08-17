package de.jakobhoermann.distsim.core.algorithm.deadlock;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmController;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.NodeId;

public final class DeadlockDetectionController implements AlgorithmController {
    @Override
    public AlgorithmId id() {
        return AlgorithmIds.LAMPORT_DEADLOCK;
    }

    @Override
    public String displayName() {
        return "Deadlock detection";
    }

    @Override
    public void start(MutableSimulationState state, long scheduledTime, NodeId initiator, long round) {
        ProtocolContext.schedule(
                state,
                id(),
                scheduledTime,
                "Initiate deadlock detection at " + initiator,
                context -> DeadlockDetectionProtocol.INSTANCE.initiateDetection(context, initiator, round)
        );
    }
}
