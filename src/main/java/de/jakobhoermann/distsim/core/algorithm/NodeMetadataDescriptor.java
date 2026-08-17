package de.jakobhoermann.distsim.core.algorithm;

public record NodeMetadataDescriptor(
        NodeMetadata metadata,
        String displayName,
        boolean editable
) {
}
