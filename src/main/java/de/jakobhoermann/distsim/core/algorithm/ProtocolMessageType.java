package de.jakobhoermann.distsim.core.algorithm;

import de.jakobhoermann.distsim.core.model.MessageKind;

public interface ProtocolMessageType {
    MessageKind kind();

    default String payload() {
        return toString();
    }

    default long defaultDelay() {
        return 10L;
    }
}
