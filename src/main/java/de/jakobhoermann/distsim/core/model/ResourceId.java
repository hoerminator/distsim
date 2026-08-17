package de.jakobhoermann.distsim.core.model;

public record ResourceId(String value) {
    @Override
    public String toString() {
        return value;
    }
}
