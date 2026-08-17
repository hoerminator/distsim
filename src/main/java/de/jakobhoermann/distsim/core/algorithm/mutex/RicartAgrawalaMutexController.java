package de.jakobhoermann.distsim.core.algorithm.mutex;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmController;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmIds;
import de.jakobhoermann.distsim.core.algorithm.ProtocolContext;
import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.NodeId;

public final class RicartAgrawalaMutexController implements AlgorithmController {
    @Override
    public AlgorithmId id() {
        return AlgorithmIds.RICART_AGRAWALA_MUTEX;
    }

    @Override
    public String displayName() {
        return "Ricart-Agrawala mutex";
    }

    @Override
    public void start(MutableSimulationState state, long scheduledTime, NodeId initiator, long round) {
        ProtocolContext.schedule(
                state,
                id(),
                scheduledTime,
                "Initiate mutex request at " + initiator,
                context -> RicartAgrawalaMutexProtocol.INSTANCE.initiateRequest(context, initiator, round)
        );
    }
}
