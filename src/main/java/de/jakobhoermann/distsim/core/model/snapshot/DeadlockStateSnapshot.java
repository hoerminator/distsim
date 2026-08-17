package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.ResourceId;

import java.util.List;
import java.util.Set;

public record DeadlockStateSnapshot(
        Set<ResourceId> heldResources,
        Set<ResourceId> awaitedResources,
        List<WaitForEdgeSnapshot> knownWaitForGraph,
        List<ProbeSnapshot> activeProbes,
        boolean deadlockDetected
) {
    public static DeadlockStateSnapshot initial() {
        return new DeadlockStateSnapshot(Set.of(), Set.of(), List.of(), List.of(), false);
    }
}
