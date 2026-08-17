package de.jakobhoermann.distsim.core.algorithm;

public interface AlgorithmModule {
    AlgorithmDescriptor descriptor();

    default AlgorithmController controller() {
        return descriptor().controllerFactory().get();
    }
}
