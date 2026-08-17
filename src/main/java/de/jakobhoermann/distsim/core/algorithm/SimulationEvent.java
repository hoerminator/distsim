package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.engine.MutableSimulationState;

public interface SimulationEvent {
    long scheduledTime();

    String description();

    void apply(MutableSimulationState state);
}
