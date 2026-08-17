package de.jakobhoermann.distsim.core.engine;

public final class SimulationClock {
    private long now;

    public long now() {
        return now;
    }

    public void advanceTo(long timestamp) {
        now = Math.max(now, timestamp);
    }

    public void reset() {
        now = 0L;
    }
}
