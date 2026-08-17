package de.jakobhoermann.distsim.core.model;

public enum AlgorithmCategory {
    ELECTION("Election"),
    MUTEX("Mutual Exclusion"),
    DEADLOCK_DETECTION("Deadlock Detection");

    private final String displayName;

    AlgorithmCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
