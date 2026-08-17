package de.jakobhoermann.distsim.core.algorithm;

public final class AlgorithmIds {
    public static final AlgorithmId BULLY_ELECTION = new AlgorithmId("bully-election");
    public static final AlgorithmId RING_ELECTION = new AlgorithmId("ring-election");
    public static final AlgorithmId RICART_AGRAWALA_MUTEX = new AlgorithmId("ricart-agrawala-mutex");
    public static final AlgorithmId LAMPORT_DEADLOCK = new AlgorithmId("lamport-deadlock");

    private AlgorithmIds() {
    }
}
