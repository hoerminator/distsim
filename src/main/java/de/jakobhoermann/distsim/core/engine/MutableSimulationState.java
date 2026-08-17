package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmId;
import de.jakobhoermann.distsim.core.algorithm.SimulationEvent;
import de.jakobhoermann.distsim.core.model.MessageKind;
import de.jakobhoermann.distsim.core.model.ResourceId;
import de.jakobhoermann.distsim.core.model.SimulationSettings;
import de.jakobhoermann.distsim.core.model.snapshot.AlgorithmRunSummary;
import de.jakobhoermann.distsim.core.model.snapshot.ElectionStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.DeadlockStateSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.EventLogCategory;
import de.jakobhoermann.distsim.core.model.snapshot.EventLogEntrySnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.EventLogSeverity;
import de.jakobhoermann.distsim.core.model.snapshot.MessageSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.MutexStateSnapshot;
import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.snapshot.NodeSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.ResourceSnapshot;
import de.jakobhoermann.distsim.core.model.snapshot.SimulationSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MutableSimulationState {
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("\\bnode-([a-z]+)\\b");

    private final EventScheduler scheduler;
    private final Map<NodeId, NodeSnapshot> nodes = new LinkedHashMap<>();
    private final Map<ResourceId, ResourceSnapshot> resources = new LinkedHashMap<>();
    private final Map<String, MessageSnapshot> messagesInFlight = new LinkedHashMap<>();
    private final List<EventLogEntrySnapshot> eventLog = new ArrayList<>();
    private SimulationSettings settings = SimulationSettings.defaults();
    private AlgorithmRunSummary runSummary = AlgorithmRunSummary.empty();
    private Random random = new Random(settings.randomSeed());
    private boolean running;
    private long time;

    public MutableSimulationState(EventScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void replaceNodes(List<NodeSnapshot> snapshots) {
        nodes.clear();
        for (NodeSnapshot snapshot : snapshots) {
            nodes.put(snapshot.nodeId(), snapshot);
        }
    }

    public void replaceResources(List<ResourceSnapshot> snapshots) {
        resources.clear();
        for (ResourceSnapshot snapshot : snapshots) {
            resources.put(snapshot.resourceId(), snapshot);
        }
    }

    public void addResource(ResourceSnapshot snapshot) {
        resources.put(snapshot.resourceId(), snapshot);
    }

    public void removeResource(ResourceId resourceId) {
        resources.remove(resourceId);
        for (NodeId nodeId : List.copyOf(nodes.keySet())) {
            NodeSnapshot current = nodes.get(nodeId);
            if (current == null) {
                continue;
            }
            LinkedHashSet<ResourceId> heldResources = new LinkedHashSet<>(current.deadlockState().heldResources());
            LinkedHashSet<ResourceId> awaitedResources = new LinkedHashSet<>(current.deadlockState().awaitedResources());
            heldResources.remove(resourceId);
            awaitedResources.remove(resourceId);
            nodes.put(nodeId, withDeadlockState(current, withDeadlockResources(
                    current.deadlockState(),
                    heldResources,
                    awaitedResources
            )));
        }
        messagesInFlight.values().removeIf(message -> resourceId.toString().equals(message.attributes().get("resource")));
    }

    public void addNode(NodeSnapshot snapshot) {
        nodes.put(snapshot.nodeId(), snapshot);
    }

    public void removeNode(NodeId nodeId) {
        nodes.remove(nodeId);
        messagesInFlight.values().removeIf(message -> message.from().equals(nodeId) || message.to().equals(nodeId));
    }

    public void updateNodeAvailability(NodeId nodeId, boolean alive) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }

        nodes.put(nodeId, withAvailability(current, alive));
        if (!alive) {
            messagesInFlight.values().removeIf(message -> message.from().equals(nodeId) || message.to().equals(nodeId));
        }
    }

    public void updateNodePriority(NodeId nodeId, int priority) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        nodes.put(nodeId, withPriority(current, priority));
    }

    public void updateResourceOwner(ResourceId resourceId, NodeId owner) {
        ResourceSnapshot current = resources.get(resourceId);
        if (current == null) {
            return;
        }
        resources.put(resourceId, new ResourceSnapshot(
                current.resourceId(),
                current.label(),
                owner,
                owner == null
        ));
    }

    public void setHeldResource(NodeId nodeId, ResourceId resourceId, boolean held) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        LinkedHashSet<ResourceId> heldResources = new LinkedHashSet<>(current.deadlockState().heldResources());
        if (held) {
            heldResources.add(resourceId);
        } else {
            heldResources.remove(resourceId);
        }
        nodes.put(nodeId, withDeadlockState(current, withDeadlockResources(
                current.deadlockState(),
                heldResources,
                current.deadlockState().awaitedResources()
        )));
    }

    public void setAwaitedResource(NodeId nodeId, ResourceId resourceId, boolean awaited) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        LinkedHashSet<ResourceId> awaitedResources = new LinkedHashSet<>(current.deadlockState().awaitedResources());
        if (awaited) {
            awaitedResources.add(resourceId);
        } else {
            awaitedResources.remove(resourceId);
        }
        nodes.put(nodeId, withDeadlockState(current, withDeadlockResources(
                current.deadlockState(),
                current.deadlockState().heldResources(),
                awaitedResources
        )));
    }

    public void clearDeadlockAnalysis() {
        for (NodeId nodeId : List.copyOf(nodes.keySet())) {
            updateDeadlockState(nodeId, current -> new DeadlockStateSnapshot(
                    current.heldResources(),
                    current.awaitedResources(),
                    List.of(),
                    List.of(),
                    false
            ));
        }
    }

    public NodeSnapshot node(NodeId nodeId) {
        return nodes.get(nodeId);
    }

    public List<NodeSnapshot> nodes() {
        return List.copyOf(nodes.values());
    }

    public ResourceSnapshot resource(ResourceId resourceId) {
        return resources.get(resourceId);
    }

    public List<ResourceSnapshot> resources() {
        return List.copyOf(resources.values());
    }

    public List<NodeSnapshot> aliveNodes() {
        return nodes.values().stream()
                .filter(NodeSnapshot::alive)
                .toList();
    }

    public List<NodeSnapshot> higherPriorityNodes(NodeId nodeId) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return List.of();
        }
        return nodes.values().stream()
                .filter(candidate -> candidate.electionPriority() > current.electionPriority())
                .sorted(Comparator.comparingInt(NodeSnapshot::electionPriority))
                .toList();
    }

    public NodeSnapshot highestPriorityAliveNode() {
        return nodes.values().stream()
                .filter(NodeSnapshot::alive)
                .max(Comparator.comparingInt(NodeSnapshot::electionPriority))
                .orElse(null);
    }

    public void updateElectionState(NodeId nodeId, UnaryOperator<ElectionStateSnapshot> updater) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        nodes.put(nodeId, withElectionState(current, updater.apply(current.electionState())));
    }

    public void updateDeadlockState(NodeId nodeId, UnaryOperator<DeadlockStateSnapshot> updater) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        nodes.put(nodeId, withDeadlockState(current, updater.apply(current.deadlockState())));
    }

    public void updateMutexState(NodeId nodeId, UnaryOperator<MutexStateSnapshot> updater) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        nodes.put(nodeId, withMutexState(current, updater.apply(current.mutexState())));
    }

    public void updateLamportClock(NodeId nodeId, long lamportClock) {
        NodeSnapshot current = nodes.get(nodeId);
        if (current == null) {
            return;
        }
        nodes.put(nodeId, withLamportClock(current, lamportClock));
    }

    public void addMessage(MessageSnapshot message) {
        messagesInFlight.put(message.id(), message);
    }

    public void removeMessage(String messageId) {
        messagesInFlight.remove(messageId);
    }

    public boolean hasMessage(String messageId) {
        return messagesInFlight.containsKey(messageId);
    }

    public boolean dropMessage(String messageId) {
        MessageSnapshot message = messagesInFlight.remove(messageId);
        if (message == null) {
            return false;
        }

        recordLostMessage(message.kind());
        appendLog("t=" + time + " manually dropped " + message.payload()
                + " from " + message.from() + " to " + message.to());
        return true;
    }

    public void startRun(AlgorithmId algorithm, NodeId initiator, long startedAt, int activeNodesAtStart) {
        resetRandom();
        runSummary = new AlgorithmRunSummary(
                algorithm,
                initiator,
                startedAt,
                null,
                0,
                0,
                0,
                Map.of(),
                activeNodesAtStart,
                settings.randomSeed(),
                ""
        );
    }

    public void recordProcessedEvent() {
        if (!runSummary.present() || runSummary.completed()) {
            return;
        }
        runSummary = new AlgorithmRunSummary(
                runSummary.algorithm(),
                runSummary.initiator(),
                runSummary.startedAt(),
                runSummary.finishedAt(),
                runSummary.processedEvents() + 1,
                runSummary.sentMessages(),
                runSummary.lostMessages(),
                runSummary.messagesByKind(),
                runSummary.activeNodesAtStart(),
                runSummary.randomSeed(),
                runSummary.result()
        );
    }

    public void recordSentMessage(MessageKind kind) {
        if (!runSummary.present() || runSummary.completed()) {
            return;
        }
        runSummary = new AlgorithmRunSummary(
                runSummary.algorithm(),
                runSummary.initiator(),
                runSummary.startedAt(),
                runSummary.finishedAt(),
                runSummary.processedEvents(),
                runSummary.sentMessages() + 1,
                runSummary.lostMessages(),
                incrementMessageKind(runSummary.messagesByKind(), kind),
                runSummary.activeNodesAtStart(),
                runSummary.randomSeed(),
                runSummary.result()
        );
    }

    public void recordLostMessage(MessageKind kind) {
        if (!runSummary.present() || runSummary.completed()) {
            return;
        }
        runSummary = new AlgorithmRunSummary(
                runSummary.algorithm(),
                runSummary.initiator(),
                runSummary.startedAt(),
                runSummary.finishedAt(),
                runSummary.processedEvents(),
                runSummary.sentMessages(),
                runSummary.lostMessages() + 1,
                runSummary.messagesByKind(),
                runSummary.activeNodesAtStart(),
                runSummary.randomSeed(),
                runSummary.result()
        );
    }

    public void recordRunResult(String result) {
        if (!runSummary.present() || runSummary.completed() || result == null || result.isBlank()) {
            return;
        }
        runSummary = new AlgorithmRunSummary(
                runSummary.algorithm(),
                runSummary.initiator(),
                runSummary.startedAt(),
                runSummary.finishedAt(),
                runSummary.processedEvents(),
                runSummary.sentMessages(),
                runSummary.lostMessages(),
                runSummary.messagesByKind(),
                runSummary.activeNodesAtStart(),
                runSummary.randomSeed(),
                result
        );
    }

    public void finishRun(long finishedAt) {
        if (!runSummary.present() || runSummary.completed()) {
            return;
        }
        String result = runSummary.result().isBlank() ? "No result" : runSummary.result();
        runSummary = new AlgorithmRunSummary(
                runSummary.algorithm(),
                runSummary.initiator(),
                runSummary.startedAt(),
                finishedAt,
                runSummary.processedEvents(),
                runSummary.sentMessages(),
                runSummary.lostMessages(),
                runSummary.messagesByKind(),
                runSummary.activeNodesAtStart(),
                runSummary.randomSeed(),
                result
        );
    }

    public SimulationSettings settings() {
        return settings;
    }

    public void updateSettings(SimulationSettings settings) {
        this.settings = settings;
        resetRandom();
    }

    public boolean shouldLoseMessage() {
        if (settings.messageLossProbabilityPercent() <= 0) {
            return false;
        }
        return random.nextDouble(100.0) < settings.messageLossProbabilityPercent();
    }

    public long messageDeliveryTime(long scheduledTime, long fallbackDelay) {
        long baseDelay = settings.baseMessageDelay() > 0 ? settings.baseMessageDelay() : fallbackDelay;
        long variance = settings.messageDelayVariance();
        long offset = variance == 0L ? 0L : random.nextLong(variance * 2L + 1L) - variance;
        return scheduledTime + Math.max(1L, baseDelay + offset);
    }

    public void appendLog(String line) {
        eventLog.add(new EventLogEntrySnapshot(
                time,
                categorize(line),
                severity(line),
                null,
                compactNodeIds(stripLeadingTime(line))
        ));
    }

    public void schedule(SimulationEvent event) {
        scheduler.schedule(event);
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long time() {
        return time;
    }

    public SimulationSnapshot snapshot() {
        return new SimulationSnapshot(
                time,
                running,
                scheduler.hasNext(),
                List.copyOf(nodes.values()),
                List.copyOf(resources.values()),
                List.copyOf(messagesInFlight.values()),
                List.copyOf(eventLog),
                eventLog.stream()
                        .map(entry -> "t=" + entry.time() + " " + entry.message())
                        .toList(),
                settings,
                runSummary
        );
    }

    public void reset() {
        nodes.clear();
        resources.clear();
        messagesInFlight.clear();
        eventLog.clear();
        runSummary = AlgorithmRunSummary.empty();
        resetRandom();
        running = false;
        time = 0L;
    }

    private void resetRandom() {
        random = new Random(settings.randomSeed());
    }

    private Map<MessageKind, Long> incrementMessageKind(Map<MessageKind, Long> current, MessageKind kind) {
        LinkedHashMap<MessageKind, Long> messagesByKind = new LinkedHashMap<>(current);
        messagesByKind.merge(kind, 1L, Long::sum);
        return messagesByKind;
    }

    private EventLogCategory categorize(String line) {
        String normalizedLine = line.toLowerCase();
        if (normalizedLine.contains("sent")
                || normalizedLine.contains("received")
                || normalizedLine.contains("dropped")
                || normalizedLine.contains("lost")
                || normalizedLine.contains("delivered")) {
            return EventLogCategory.MESSAGE;
        }
        if (normalizedLine.contains("detected")
                || normalizedLine.contains("became coordinator")
                || normalizedLine.contains("selected")
                || normalizedLine.contains("entered the critical section")
                || normalizedLine.contains("released the critical section")) {
            return EventLogCategory.RESULT;
        }
        if (normalizedLine.contains("cannot")
                || normalizedLine.contains("aborted")
                || normalizedLine.contains("ignored")
                || normalizedLine.contains("not available")
                || normalizedLine.contains("no more events")) {
            return EventLogCategory.WARNING;
        }
        if (normalizedLine.contains("turned")
                || normalizedLine.contains("set to")
                || normalizedLine.contains("added")
                || normalizedLine.contains("removed")
                || normalizedLine.contains("started")
                || normalizedLine.contains("requested")) {
            return EventLogCategory.STATE;
        }
        return EventLogCategory.GENERAL;
    }

    private EventLogSeverity severity(String line) {
        EventLogCategory category = categorize(line);
        if (category == EventLogCategory.WARNING) {
            return EventLogSeverity.WARNING;
        }
        return EventLogSeverity.INFO;
    }

    private String stripLeadingTime(String line) {
        if (!line.startsWith("t=")) {
            return line;
        }
        int firstSpaceIndex = line.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex >= line.length() - 1) {
            return line;
        }
        return line.substring(firstSpaceIndex + 1);
    }

    private String compactNodeIds(String line) {
        Matcher matcher = NODE_ID_PATTERN.matcher(line);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private NodeSnapshot withAvailability(NodeSnapshot current, boolean alive) {
        return new NodeSnapshot(
                current.nodeId(),
                current.x(),
                current.y(),
                current.label(),
                alive,
                current.electionPriority(),
                current.ringPosition(),
                current.ringSuccessor(),
                current.ringPredecessor(),
                current.lamportClock(),
                alive ? current.electionState() : ElectionStateSnapshot.initial(),
                current.mutexState(),
                current.deadlockState()
        );
    }

    private NodeSnapshot withElectionState(NodeSnapshot current, ElectionStateSnapshot electionState) {
        return new NodeSnapshot(
                current.nodeId(),
                current.x(),
                current.y(),
                current.label(),
                current.alive(),
                current.electionPriority(),
                current.ringPosition(),
                current.ringSuccessor(),
                current.ringPredecessor(),
                current.lamportClock(),
                electionState,
                current.mutexState(),
                current.deadlockState()
        );
    }

    private NodeSnapshot withPriority(NodeSnapshot current, int priority) {
        return new NodeSnapshot(
                current.nodeId(),
                current.x(),
                current.y(),
                current.label(),
                current.alive(),
                priority,
                current.ringPosition(),
                current.ringSuccessor(),
                current.ringPredecessor(),
                current.lamportClock(),
                current.electionState(),
                current.mutexState(),
                current.deadlockState()
        );
    }

    private NodeSnapshot withDeadlockState(NodeSnapshot current, DeadlockStateSnapshot deadlockState) {
        return new NodeSnapshot(
                current.nodeId(),
                current.x(),
                current.y(),
                current.label(),
                current.alive(),
                current.electionPriority(),
                current.ringPosition(),
                current.ringSuccessor(),
                current.ringPredecessor(),
                current.lamportClock(),
                current.electionState(),
                current.mutexState(),
                deadlockState
        );
    }

    private NodeSnapshot withMutexState(NodeSnapshot current, MutexStateSnapshot mutexState) {
        return new NodeSnapshot(
                current.nodeId(),
                current.x(),
                current.y(),
                current.label(),
                current.alive(),
                current.electionPriority(),
                current.ringPosition(),
                current.ringSuccessor(),
                current.ringPredecessor(),
                current.lamportClock(),
                current.electionState(),
                mutexState,
                current.deadlockState()
        );
    }

    private NodeSnapshot withLamportClock(NodeSnapshot current, long lamportClock) {
        return new NodeSnapshot(
                current.nodeId(),
                current.x(),
                current.y(),
                current.label(),
                current.alive(),
                current.electionPriority(),
                current.ringPosition(),
                current.ringSuccessor(),
                current.ringPredecessor(),
                lamportClock,
                current.electionState(),
                current.mutexState(),
                current.deadlockState()
        );
    }

    private DeadlockStateSnapshot withDeadlockResources(
            DeadlockStateSnapshot current,
            Set<ResourceId> heldResources,
            Set<ResourceId> awaitedResources
    ) {
        return new DeadlockStateSnapshot(
                Set.copyOf(heldResources),
                Set.copyOf(awaitedResources),
                current.knownWaitForGraph(),
                current.activeProbes(),
                current.deadlockDetected()
        );
    }

    public NodeSnapshot withTopology(
            NodeSnapshot current,
            double x,
            double y,
            Integer ringPosition,
            NodeId ringSuccessor,
            NodeId ringPredecessor
    ) {
        return new NodeSnapshot(
                current.nodeId(),
                x,
                y,
                current.label(),
                current.alive(),
                current.electionPriority(),
                ringPosition,
                ringSuccessor,
                ringPredecessor,
                current.lamportClock(),
                current.electionState(),
                current.mutexState(),
                current.deadlockState()
        );
    }
}
