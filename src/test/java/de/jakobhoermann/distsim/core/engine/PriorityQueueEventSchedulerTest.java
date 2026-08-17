package de.jakobhoermann.distsim.core.engine;

import de.jakobhoermann.distsim.core.algorithm.AlgorithmEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityQueueEventSchedulerTest {
    @Test
    void usesInsertionOrderAsTieBreakerForEqualScheduledTimes() {
        PriorityQueueEventScheduler scheduler = new PriorityQueueEventScheduler();

        scheduler.schedule(event(5, "first"));
        scheduler.schedule(event(5, "second"));
        scheduler.schedule(event(5, "third"));

        assertEquals("first", scheduler.pollNext().description());
        assertEquals("second", scheduler.pollNext().description());
        assertEquals("third", scheduler.pollNext().description());
    }

    @Test
    void stillPrioritizesEarlierScheduledTimesBeforeInsertionOrder() {
        PriorityQueueEventScheduler scheduler = new PriorityQueueEventScheduler();

        scheduler.schedule(event(5, "later"));
        scheduler.schedule(event(2, "earlier"));

        assertEquals("earlier", scheduler.pollNext().description());
        assertEquals("later", scheduler.pollNext().description());
    }

    private AlgorithmEvent event(long scheduledTime, String description) {
        return new AlgorithmEvent(
                scheduledTime,
                description,
                state -> {
                }
        );
    }
}
