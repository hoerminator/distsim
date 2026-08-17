package de.jakobhoermann.distsim.core.algorithm;

public record AlgorithmId(String value) {
    public AlgorithmId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Algorithm id must not be blank.");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
