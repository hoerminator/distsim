package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;

public record EventLogEntrySnapshot(
        long time,
        EventLogCategory category,
        EventLogSeverity severity,
        AlgorithmId algorithm,
        String message
) {
}
