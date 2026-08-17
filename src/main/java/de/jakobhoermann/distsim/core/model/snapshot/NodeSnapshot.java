package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.NodeId;

public record NodeSnapshot(
        NodeId nodeId,
        double x,
        double y,
        String label,
        boolean alive,
        int electionPriority,
        Integer ringPosition,
        NodeId ringSuccessor,
        NodeId ringPredecessor,
        long lamportClock,
        ElectionStateSnapshot electionState,
        MutexStateSnapshot mutexState,
        DeadlockStateSnapshot deadlockState
) {
}
