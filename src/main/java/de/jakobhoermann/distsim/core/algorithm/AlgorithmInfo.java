package de.jakobhoermann.distsim.core.algorithm;

public record AlgorithmInfo(
        String goal,
        String importantMetadata
) {
    public static AlgorithmInfo empty() {
        return new AlgorithmInfo("", "");
    }
}
