package de.jakobhoermann.distsim.core.model;

public record NodeId(String value) {
    @Override
    public String toString() {
        return value;
    }
}
