package de.jakobhoermann.distsim.core.model;

public record SimulationSettings(
        long baseMessageDelay,
        long messageDelayVariance,
        int messageLossProbabilityPercent,
        long randomSeed
) {
    public static final long DEFAULT_BASE_MESSAGE_DELAY = 10L;
    public static final long DEFAULT_MESSAGE_DELAY_VARIANCE = 0L;
    public static final int DEFAULT_MESSAGE_LOSS_PROBABILITY_PERCENT = 0;
    public static final long DEFAULT_RANDOM_SEED = 1L;

    public SimulationSettings {
        baseMessageDelay = Math.max(1L, baseMessageDelay);
        messageDelayVariance = Math.max(0L, messageDelayVariance);
        messageLossProbabilityPercent = Math.max(0, Math.min(100, messageLossProbabilityPercent));
    }

    public static SimulationSettings defaults() {
        return new SimulationSettings(
                DEFAULT_BASE_MESSAGE_DELAY,
                DEFAULT_MESSAGE_DELAY_VARIANCE,
                DEFAULT_MESSAGE_LOSS_PROBABILITY_PERCENT,
                DEFAULT_RANDOM_SEED
        );
    }
}
