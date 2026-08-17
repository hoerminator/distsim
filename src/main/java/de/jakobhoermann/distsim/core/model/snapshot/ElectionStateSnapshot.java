package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.NodeId;

import java.util.Set;

public record ElectionStateSnapshot(
        boolean electionInProgress,
        boolean coordinator,
        NodeId knownCoordinator,
        Long activeElectionRound,
        Set<NodeId> participants,
        NodeId highestCandidateSeen,
        boolean awaitingCoordinatorAnnouncement
) {
    public static ElectionStateSnapshot initial() {
        return new ElectionStateSnapshot(false, false, null, null, Set.of(), null, false);
    }
}
