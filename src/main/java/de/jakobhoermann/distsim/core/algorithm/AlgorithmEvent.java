package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.engine.MutableSimulationState;

import java.util.function.Consumer;

public record AlgorithmEvent(
        long scheduledTime,
        String description,
        Consumer<MutableSimulationState> action
) implements SimulationEvent {
    @Override
    public void apply(MutableSimulationState state) {
        action.accept(state);
    }
}
