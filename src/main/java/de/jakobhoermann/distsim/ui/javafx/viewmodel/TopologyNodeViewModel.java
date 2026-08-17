package de.jakobhoermann.distsim.ui.javafx.viewmodel;

import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;

import java.util.Set;

public record TopologyNodeViewModel(
        NodeId nodeId,
        String label,
        boolean alive,
        int electionPriority,
        boolean coordinator,
        long lamportClock,
        boolean requestingCriticalSection,
        boolean inCriticalSection,
        int acknowledgementsReceived,
        int deferredReplies,
        Set<ResourceId> heldResources,
        Set<ResourceId> awaitedResources,
        boolean deadlockDetected,
        NodeId ringSuccessor,
        double x,
        double y
) {
}
