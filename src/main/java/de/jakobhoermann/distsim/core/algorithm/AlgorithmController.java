package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.engine.MutableSimulationState;
import de.jakobhoermann.distsim.core.model.NodeId;

public interface AlgorithmController {
    AlgorithmId id();

    String displayName();

    void start(MutableSimulationState state, long scheduledTime, NodeId initiator, long round);
}
