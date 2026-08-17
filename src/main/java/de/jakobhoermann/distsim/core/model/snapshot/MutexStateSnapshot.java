package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.NodeId;

import java.util.List;
import java.util.Set;

public record MutexStateSnapshot(
        boolean requestingCriticalSection,
        boolean inCriticalSection,
        Long ownRequestTimestamp,
        Set<NodeId> acknowledgementsReceived,
        Set<NodeId> deferredReplies,
        List<MutexRequestSnapshot> requestQueue
) {
    public static MutexStateSnapshot initial() {
        return new MutexStateSnapshot(false, false, null, Set.of(), Set.of(), List.of());
    }
}
