package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.algorithm.deadlock.DeadlockDetectionModule;
import de.jakobhoermann.distsim.core.algorithm.election.BullyElectionModule;
import de.jakobhoermann.distsim.core.algorithm.election.RingElectionModule;
import de.jakobhoermann.distsim.core.algorithm.mutex.RicartAgrawalaMutexModule;
import de.jakobhoermann.distsim.core.model.AlgorithmCategory;
import de.jakobhoermann.distsim.core.scenario.Scenario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class AlgorithmRegistry {
    private static final List<AlgorithmModule> MODULES = List.of(
            new BullyElectionModule(),
            new RingElectionModule(),
            new RicartAgrawalaMutexModule(),
            new DeadlockDetectionModule()
    );
    private static final List<AlgorithmDescriptor> DESCRIPTORS = MODULES.stream()
            .map(AlgorithmModule::descriptor)
            .toList();
    private static final Map<AlgorithmId, AlgorithmDescriptor> DESCRIPTORS_BY_ID = descriptorsById();

    private AlgorithmRegistry() {
    }

    public static List<AlgorithmModule> modules() {
        return MODULES;
    }

    public static List<AlgorithmDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    public static Optional<AlgorithmDescriptor> descriptor(AlgorithmId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DESCRIPTORS_BY_ID.get(id));
    }

    public static List<AlgorithmDescriptor> algorithmsFor(AlgorithmCategory category) {
        if (category == null) {
            return List.of();
        }
        return DESCRIPTORS.stream()
                .filter(descriptor -> descriptor.category() == category)
                .toList();
    }

    public static List<AlgorithmCategory> availableCategories() {
        return DESCRIPTORS.stream()
                .map(AlgorithmDescriptor::category)
                .distinct()
                .toList();
    }

    public static AlgorithmDescriptor defaultDescriptor() {
        return DESCRIPTORS.getFirst();
    }

    public static AlgorithmId defaultAlgorithm() {
        return defaultDescriptor().id();
    }

    public static Scenario defaultScenarioFor(AlgorithmId id) {
        return descriptor(id)
                .map(AlgorithmDescriptor::defaultScenario)
                .map(Supplier::get)
                .orElseGet(() -> defaultDescriptor()
                        .defaultScenario()
                        .get());
    }

    private static Map<AlgorithmId, AlgorithmDescriptor> descriptorsById() {
        Map<AlgorithmId, AlgorithmDescriptor> descriptorsById = new LinkedHashMap<>();
        for (AlgorithmDescriptor descriptor : DESCRIPTORS) {
            descriptorsById.put(descriptor.id(), descriptor);
        }
        return Map.copyOf(descriptorsById);
    }
}
