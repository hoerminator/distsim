package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.algorithm.mutex.RicartAgrawalaMutexController;
import de.jakobhoermann.distsim.core.model.AlgorithmCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmRegistryTest {
    @Test
    void exposesAlgorithmsGroupedByCategory() {
        assertEquals(
                List.of(AlgorithmIds.BULLY_ELECTION, AlgorithmIds.RING_ELECTION),
                AlgorithmRegistry.algorithmsFor(AlgorithmCategory.ELECTION).stream()
                        .map(AlgorithmDescriptor::id)
                        .toList()
        );
        assertEquals(
                List.of(AlgorithmIds.RICART_AGRAWALA_MUTEX),
                AlgorithmRegistry.algorithmsFor(AlgorithmCategory.MUTEX).stream()
                        .map(AlgorithmDescriptor::id)
                        .toList()
        );
    }

    @Test
    void descriptorProvidesControllerAndScenario() {
        AlgorithmDescriptor descriptor = AlgorithmRegistry.descriptor(AlgorithmIds.RICART_AGRAWALA_MUTEX)
                .orElseThrow();

        assertInstanceOf(RicartAgrawalaMutexController.class, descriptor.controllerFactory().get());
        assertEquals("Three-process mutex starter", descriptor.defaultScenario().get().name());
        assertEquals(TopologyProfile.COMPLETE_GRAPH, descriptor.topologyProfile());
        assertTrue(descriptor.usesMetadata(NodeMetadata.LAMPORT_CLOCK));
        assertTrue(descriptor.usesMetadata(NodeMetadata.MUTEX_STATE));
        assertTrue(AlgorithmRegistry.availableCategories().contains(AlgorithmCategory.DEADLOCK_DETECTION));
    }

    @Test
    void exposesAlgorithmModulesAsSingleExtensionPoint() {
        assertEquals(4, AlgorithmRegistry.modules().size());
        assertEquals(
                TopologyProfile.RING,
                AlgorithmRegistry.descriptor(AlgorithmIds.RING_ELECTION).orElseThrow().topologyProfile()
        );
    }

}
