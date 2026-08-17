package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.SimulationEvent;

import java.util.Comparator;
import java.util.PriorityQueue;

public final class PriorityQueueEventScheduler implements EventScheduler {
    private final PriorityQueue<QueuedSimulationEvent> queue = new PriorityQueue<>(
            Comparator.comparingLong((QueuedSimulationEvent queuedEvent) -> queuedEvent.event().scheduledTime())
                    .thenComparingLong(QueuedSimulationEvent::sequenceNumber)
    );
    private long nextSequenceNumber;

    @Override
    public void schedule(SimulationEvent event) {
        queue.add(new QueuedSimulationEvent(nextSequenceNumber++, event));
    }

    @Override
    public SimulationEvent pollNext() {
        QueuedSimulationEvent queuedEvent = queue.poll();
        return queuedEvent == null ? null : queuedEvent.event();
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
        nextSequenceNumber = 0L;
    }

    private record QueuedSimulationEvent(
            long sequenceNumber,
            SimulationEvent event
    ) {
    }
}
