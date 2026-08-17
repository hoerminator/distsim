package de.jakobhoermann.distsim.ui.javafx.viewmodel;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmRegistry;
import de.jakobhoermann.distsim.app.service.SimulationApplicationService;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmDescriptor;
import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.model.snapshot.MessageSnapshot;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.AlgorithmRunSummary;
import de.jakobhoermann.distsim.core.model.snapshot.EventLogEntrySnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ResourceSnapshot;
import de.jakobhoermann.distsim.core.scenario.Scenario;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class MainViewModel {
    private final SimulationApplicationService applicationService;
    private final LongProperty simulationTime = new SimpleLongProperty(0L);
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPendingEvents = new SimpleBooleanProperty(false);
    private final LongProperty baseMessageDelay = new SimpleLongProperty(SimulationSettings.DEFAULT_BASE_MESSAGE_DELAY);
    private final LongProperty messageDelayVariance = new SimpleLongProperty(SimulationSettings.DEFAULT_MESSAGE_DELAY_VARIANCE);
    private final IntegerProperty messageLossProbabilityPercent =
            new SimpleIntegerProperty(SimulationSettings.DEFAULT_MESSAGE_LOSS_PROBABILITY_PERCENT);
    private final LongProperty randomSeed = new SimpleLongProperty(SimulationSettings.DEFAULT_RANDOM_SEED);
    private final DoubleProperty playbackSpeed = new SimpleDoubleProperty(1.0);
    private final ObjectProperty<AlgorithmDescriptor> selectedAlgorithm =
            new SimpleObjectProperty<>(AlgorithmRegistry.defaultDescriptor());
    private final ObjectProperty<NodeId> selectedStartNode = new SimpleObjectProperty<>();
    private final ObjectProperty<NodeId> selectedDetailsNode = new SimpleObjectProperty<>();
    private final ObjectProperty<AlgorithmRunSummary> algorithmRunSummary =
            new SimpleObjectProperty<>(AlgorithmRunSummary.empty());
    private final ObservableList<TopologyNodeViewModel> nodes = FXCollections.observableArrayList();
    private final ObservableList<ResourceViewModel> resources = FXCollections.observableArrayList();
    private final ObservableList<MessageSnapshot> messagesInFlight = FXCollections.observableArrayList();
    private final ObservableList<String> eventLog = FXCollections.observableArrayList();
    private final ObservableList<EventLogRowViewModel> eventLogRows = FXCollections.observableArrayList();

    public MainViewModel(SimulationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public void loadScenario(Scenario scenario) {
        applySnapshot(applicationService.loadScenario(scenario));
    }

    public void loadScenarioWithFreshRandomSeed(Scenario scenario) {
        applicationService.updateSettings(settingsWithFreshRandomSeed());
        applySnapshot(applicationService.loadScenario(scenario));
    }

    public void selectAlgorithm(AlgorithmDescriptor algorithm) {
        selectedAlgorithm.set(algorithm);
    }

    public void selectAlgorithm(AlgorithmId algorithm) {
        selectedAlgorithm.set(AlgorithmRegistry.descriptor(algorithm).orElse(null));
    }

    public void selectStartNode(NodeId nodeId) {
        selectedStartNode.set(nodeId);
    }

    public void selectDetailsNode(NodeId nodeId) {
        selectedDetailsNode.set(nodeId);
    }

    public void startAlgorithm(AlgorithmDescriptor algorithm, NodeId initiator) {
        if (algorithm != null) {
            applySnapshot(applicationService.startAlgorithm(algorithm.id(), initiator));
        }
    }

    public void startAlgorithm(AlgorithmId algorithm, NodeId initiator) {
        applySnapshot(applicationService.startAlgorithm(algorithm, initiator));
    }

    public void setNodeActive(NodeId nodeId, boolean active) {
        applySnapshot(applicationService.setNodeActive(nodeId, active));
    }

    public void updateNodePriority(NodeId nodeId, int priority) {
        applySnapshot(applicationService.updateNodePriority(nodeId, priority));
    }

    public void updateNodeLamportClock(NodeId nodeId, long lamportClock) {
        applySnapshot(applicationService.updateNodeLamportClock(nodeId, lamportClock));
    }

    public void setNodeHoldsResource(NodeId nodeId, ResourceId resourceId, boolean holds) {
        applySnapshot(applicationService.setNodeHoldsResource(nodeId, resourceId, holds));
    }

    public void setNodeAwaitsResource(NodeId nodeId, ResourceId resourceId, boolean awaits) {
        applySnapshot(applicationService.setNodeAwaitsResource(nodeId, resourceId, awaits));
    }

    public void addNode(AlgorithmDescriptor algorithm) {
        if (algorithm != null) {
            applySnapshot(applicationService.addNode(algorithm.id()));
        }
    }

    public void addNode(AlgorithmId algorithm) {
        applySnapshot(applicationService.addNode(algorithm));
    }

    public void removeNode(NodeId nodeId) {
        applySnapshot(applicationService.removeNode(nodeId));
    }

    public void addResource() {
        applySnapshot(applicationService.addResource());
    }

    public void removeResource(ResourceId resourceId) {
        applySnapshot(applicationService.removeResource(resourceId));
    }

    public void dropMessage(String messageId) {
        applySnapshot(applicationService.dropMessage(messageId));
    }

    public void updatePlaybackSpeed(double playbackSpeed) {
        this.playbackSpeed.set(Math.max(0.25, Math.min(4.0, playbackSpeed)));
    }

    public void updateSimulationSettings(
            long baseMessageDelay,
            long messageDelayVariance,
            int messageLossProbabilityPercent,
            long randomSeed
    ) {
        applySnapshot(applicationService.updateSettings(new SimulationSettings(
                baseMessageDelay,
                messageDelayVariance,
                messageLossProbabilityPercent,
                randomSeed
        )));
    }

    public void play() {
        applySnapshot(applicationService.play());
    }

    public void pause() {
        applySnapshot(applicationService.pause());
    }

    public void step() {
        applySnapshot(applicationService.step());
    }

    public void reset() {
        applicationService.updateSettings(settingsWithFreshRandomSeed());
        applySnapshot(applicationService.reset());
    }

    private SimulationSettings settingsWithFreshRandomSeed() {
        return new SimulationSettings(
                baseMessageDelay.get(),
                messageDelayVariance.get(),
                messageLossProbabilityPercent.get(),
                ThreadLocalRandom.current().nextLong(2L, 1_000_000L)
        );
    }

    private void applySnapshot(SimulationSnapshot snapshot) {
        simulationTime.set(snapshot.time());
        running.set(snapshot.running());
        hasPendingEvents.set(snapshot.hasPendingEvents());
        baseMessageDelay.set(snapshot.settings().baseMessageDelay());
        messageDelayVariance.set(snapshot.settings().messageDelayVariance());
        messageLossProbabilityPercent.set(snapshot.settings().messageLossProbabilityPercent());
        randomSeed.set(snapshot.settings().randomSeed());
        algorithmRunSummary.set(snapshot.runSummary());
        nodes.setAll(snapshot.nodes().stream().map(this::mapNode).toList());
        resources.setAll(snapshot.resources().stream().map(this::mapResource).toList());
        messagesInFlight.setAll(snapshot.messagesInFlight());
        eventLogRows.setAll(groupEventLog(snapshot));
        eventLog.setAll(eventLogRows.stream().map(EventLogRowViewModel::plainText).toList());
        if (selectedStartNode.get() == null || snapshot.nodes().stream()
                .noneMatch(node -> node.nodeId().equals(selectedStartNode.get()))) {
            selectedStartNode.set(snapshot.nodes().isEmpty() ? null : snapshot.nodes().getFirst().nodeId());
        }
        if (selectedDetailsNode.get() != null && snapshot.nodes().stream()
                .noneMatch(node -> node.nodeId().equals(selectedDetailsNode.get()))) {
            selectedDetailsNode.set(null);
        }
    }

    private TopologyNodeViewModel mapNode(NodeSnapshot node) {
        String displayLabel = compactNodeLabel(node.nodeId());
        return new TopologyNodeViewModel(
                node.nodeId(),
                displayLabel,
                node.alive(),
                node.electionPriority(),
                node.electionState().coordinator(),
                node.lamportClock(),
                node.mutexState().requestingCriticalSection(),
                node.mutexState().inCriticalSection(),
                node.mutexState().acknowledgementsReceived().size(),
                node.mutexState().deferredReplies().size(),
                node.deadlockState().heldResources(),
                node.deadlockState().awaitedResources(),
                node.deadlockState().deadlockDetected(),
                node.ringSuccessor(),
                node.x(),
                node.y()
        );
    }

    private String compactNodeLabel(NodeId nodeId) {
        String value = nodeId.value();
        int separatorIndex = value.lastIndexOf('-');
        if (separatorIndex >= 0 && separatorIndex < value.length() - 1) {
            return value.substring(separatorIndex + 1).toUpperCase();
        }
        return value;
    }

    private ResourceViewModel mapResource(ResourceSnapshot resource) {
        return new ResourceViewModel(
                resource.resourceId(),
                resource.label(),
                resource.owner(),
                resource.available()
        );
    }

    private List<EventLogRowViewModel> groupEventLog(SimulationSnapshot snapshot) {
        if (snapshot.eventLogEntries().isEmpty()) {
            return snapshot.eventLog().stream()
                    .map(entry -> EventLogRowViewModel.entry(0, de.jakobhoermann.distsim.core.model.snapshot.EventLogCategory.GENERAL, entry))
                    .toList();
        }

        Map<Long, List<EventLogEntrySnapshot>> entriesByTime = new LinkedHashMap<>();
        for (EventLogEntrySnapshot entry : snapshot.eventLogEntries()) {
            entriesByTime.computeIfAbsent(entry.time(), ignored -> new ArrayList<>()).add(entry);
        }

        List<EventLogRowViewModel> formattedEntries = new ArrayList<>();
        for (Map.Entry<Long, List<EventLogEntrySnapshot>> group : entriesByTime.entrySet()) {
            formattedEntries.add(EventLogRowViewModel.timeHeader(group.getKey()));
            for (EventLogEntrySnapshot entry : group.getValue()) {
                formattedEntries.add(EventLogRowViewModel.entry(entry.time(), entry.category(), entry.message()));
            }
        }
        return formattedEntries;
    }

    public LongProperty simulationTimeProperty() {
        return simulationTime;
    }

    public BooleanProperty runningProperty() {
        return running;
    }

    public BooleanProperty hasPendingEventsProperty() {
        return hasPendingEvents;
    }

    public LongProperty baseMessageDelayProperty() {
        return baseMessageDelay;
    }

    public LongProperty messageDelayVarianceProperty() {
        return messageDelayVariance;
    }

    public IntegerProperty messageLossProbabilityPercentProperty() {
        return messageLossProbabilityPercent;
    }

    public LongProperty randomSeedProperty() {
        return randomSeed;
    }

    public DoubleProperty playbackSpeedProperty() {
        return playbackSpeed;
    }

    public ObjectProperty<AlgorithmDescriptor> selectedAlgorithmProperty() {
        return selectedAlgorithm;
    }

    public ObjectProperty<NodeId> selectedStartNodeProperty() {
        return selectedStartNode;
    }

    public ObjectProperty<NodeId> selectedDetailsNodeProperty() {
        return selectedDetailsNode;
    }

    public ObjectProperty<AlgorithmRunSummary> algorithmRunSummaryProperty() {
        return algorithmRunSummary;
    }

    public ObservableList<TopologyNodeViewModel> getNodes() {
        return nodes;
    }

    public ObservableList<ResourceViewModel> getResources() {
        return resources;
    }

    public ObservableList<MessageSnapshot> getMessagesInFlight() {
        return messagesInFlight;
    }

    public ObservableList<String> getEventLog() {
        return eventLog;
    }

    public ObservableList<EventLogRowViewModel> getEventLogRows() {
        return eventLogRows;
    }
}
