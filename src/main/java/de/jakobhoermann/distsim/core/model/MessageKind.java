package de.jakobhoermann.distsim.core.model;

public enum MessageKind {
    USER_PAYLOAD,
    ELECTION,
    ANSWER,
    COORDINATOR,
    RING_TOKEN,
    MUTEX_REQUEST,
    MUTEX_REPLY,
    DEADLOCK_PROBE
}
