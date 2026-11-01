package net.kindlingmc.preview;

/**
 * Whether a player is on a fast journey, from its horizontal speed over each second: {@link #START_SAMPLES} seconds in
 * a row above the speed start a journey (one second may be a pearl or a short teleport), {@link #CALM_SAMPLES} slower
 * seconds in a row end it, so a player slowing down for a moment keeps its distances. A jump farther than
 * {@link #TELEPORT_BLOCKS} in a second is a teleport, and a second without samples (the player was not ticked here)
 * starts over; neither is a journey. Tick thread only.
 */
final class FastTravel {
    static final int SAMPLE_TICKS = 20;
    static final int START_SAMPLES = 2;
    static final int CALM_SAMPLES = 5;
    /** Farther than any vanilla way of travel gets in a second (an ice boat makes about 70 blocks). */
    static final double TELEPORT_BLOCKS = 160.0;

    private boolean hasSample;
    private double sampleX;
    private double sampleZ;
    private long sampleTick;
    private int fastSamples;
    private int calmSamples;
    private boolean fast;

    /** Takes the player's position; call every tick. */
    void sample(final double x, final double z, final long tick, final double fastAboveBlocksPerSecond) {
        final long elapsed = tick - this.sampleTick;
        if (!this.hasSample || elapsed > 2L * SAMPLE_TICKS) {
            this.restartAt(x, z, tick);
            return;
        }
        if (elapsed < SAMPLE_TICKS) {
            return;
        }
        final double blocks = Math.hypot(x - this.sampleX, z - this.sampleZ);
        if (blocks > TELEPORT_BLOCKS) {
            this.restartAt(x, z, tick);
            return;
        }
        this.judge(blocks * SAMPLE_TICKS / elapsed > fastAboveBlocksPerSecond);
        this.sampleX = x;
        this.sampleZ = z;
        this.sampleTick = tick;
    }

    boolean isFast() {
        return this.fast;
    }

    private void restartAt(final double x, final double z, final long tick) {
        this.hasSample = true;
        this.sampleX = x;
        this.sampleZ = z;
        this.sampleTick = tick;
        this.fastSamples = 0;
        this.calmSamples = 0;
        this.fast = false;
    }

    private void judge(final boolean fastSecond) {
        if (fastSecond) {
            this.calmSamples = 0;
            this.fast |= ++this.fastSamples >= START_SAMPLES;
            return;
        }
        this.fastSamples = 0;
        if (this.fast && ++this.calmSamples >= CALM_SAMPLES) {
            this.fast = false;
            this.calmSamples = 0;
        }
    }
}
