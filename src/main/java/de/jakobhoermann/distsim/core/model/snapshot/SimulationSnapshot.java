package de.jakobhoermann.distsim.core.model.snapshot;

import de.jakobhoermann.distsim.core.model.SimulationSettings;

import java.util.List;

public record SimulationSnapshot(
        long time,
        boolean running,
        boolean hasPendingEvents,
        List<NodeSnapshot> nodes,
        List<ResourceSnapshot> resources,
        List<MessageSnapshot> messagesInFlight,
        List<EventLogEntrySnapshot> eventLogEntries,
        List<String> eventLog,
        SimulationSettings settings,
        AlgorithmRunSummary runSummary
) {
    public static SimulationSnapshot empty() {
        return new SimulationSnapshot(
                0L,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SimulationSettings.defaults(),
                AlgorithmRunSummary.empty()
        );
    }
}
