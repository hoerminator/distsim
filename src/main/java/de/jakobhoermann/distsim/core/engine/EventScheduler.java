package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.SimulationEvent;

public interface EventScheduler {
    void schedule(SimulationEvent event);

    SimulationEvent pollNext();

    boolean hasNext();

    void clear();
}
