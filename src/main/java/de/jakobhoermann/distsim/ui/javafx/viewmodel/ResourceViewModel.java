package de.jakobhoermann.distsim.ui.javafx.viewmodel;

import de.jakobhoermann.distsim.core.model.NodeId;
import de.jakobhoermann.distsim.core.model.ResourceId;

public record ResourceViewModel(
        ResourceId resourceId,
        String label,
        NodeId owner,
        boolean available
) {
}
