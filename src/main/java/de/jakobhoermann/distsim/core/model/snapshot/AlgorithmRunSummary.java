package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.NodeId;

import java.util.Map;

public record AlgorithmRunSummary(
        AlgorithmId algorithm,
        NodeId initiator,
        Long startedAt,
        Long finishedAt,
        long processedEvents,
        long sentMessages,
        long lostMessages,
        Map<MessageKind, Long> messagesByKind,
        int activeNodesAtStart,
        long randomSeed,
        String result
) {
    public AlgorithmRunSummary {
        messagesByKind = Map.copyOf(messagesByKind);
        result = result == null ? "" : result;
    }

    public static AlgorithmRunSummary empty() {
        return new AlgorithmRunSummary(null, null, null, null, 0, 0, 0, Map.of(), 0, 0, "");
    }

    public boolean present() {
        return algorithm != null;
    }

    public boolean completed() {
        return present() && finishedAt != null;
    }

    public long duration(long currentTime) {
        if (!present() || startedAt == null) {
            return 0L;
        }
        long end = finishedAt == null ? currentTime : finishedAt;
        return Math.max(0L, end - startedAt);
    }
}
