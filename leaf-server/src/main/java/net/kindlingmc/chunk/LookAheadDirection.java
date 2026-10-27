package net.kindlingmc.chunk;

/**
 * The direction in which a player's chunk queues are ordered first: from the player's last two chunk crossings, weighted
 * by the share of the load distance the player covers in {@code lookAheadTicks} at that pace, at most {@link #MAX_WEIGHT}.
 * Only the order changes, never which chunks load.
 *
 * <p>Samples that do not describe a real flight are not used: a crossing within {@link #MIN_SAMPLE_TICKS} of the last
 * one (a player hovering on a chunk border) is ignored; a jump farther than {@link #MAX_CHUNKS_PER_TICK} allows (a
 * teleport) or after {@link #STALE_TICKS} without a crossing starts over from the new position.
 * Used by one player's chunk loader on the tick thread.
 */
public final class LookAheadDirection {

    public static final int STALE_TICKS = 100;
    public static final int MIN_SAMPLE_TICKS = 3;
    public static final double MAX_CHUNKS_PER_TICK = 0.5;
    public static final double MAX_WEIGHT = 0.75;
    private static final double ALWAYS_ALLOWED_CHUNKS = 2.0;

    // unit vector of the direction of travel, scaled by how strongly it bends the order (both 0: plain distance)
    private double directionX;
    private double directionZ;
    private boolean hasReference;
    private int referenceX;
    private int referenceZ;
    private long referenceTick;

    /**
     * Takes the player's position when its chunk queues are rebuilt.
     *
     * @param lookAheadTicks how far ahead, in ticks of travel, to look; 0 keeps the plain distance order
     */
    public void update(final int chunkX, final int chunkZ, final long tick, final int loadViewDistance, final int lookAheadTicks) {
        if (!this.hasReference) {
            this.restartAt(chunkX, chunkZ, tick);
            return;
        }
        final long elapsed = tick - this.referenceTick;
        final int movedX = chunkX - this.referenceX;
        final int movedZ = chunkZ - this.referenceZ;
        final double moved = Math.sqrt((double) movedX * movedX + (double) movedZ * movedZ);
        if (elapsed > STALE_TICKS || moved > Math.max(ALWAYS_ALLOWED_CHUNKS, MAX_CHUNKS_PER_TICK * elapsed)) {
            this.restartAt(chunkX, chunkZ, tick);
        } else if (elapsed >= MIN_SAMPLE_TICKS) {
            this.pointTo(movedX, movedZ, moved, elapsed, loadViewDistance, lookAheadTicks);
            this.referenceX = chunkX;
            this.referenceZ = chunkZ;
            this.referenceTick = tick;
        }
    }

    private void restartAt(final int chunkX, final int chunkZ, final long tick) {
        this.directionX = 0.0;
        this.directionZ = 0.0;
        this.hasReference = true;
        this.referenceX = chunkX;
        this.referenceZ = chunkZ;
        this.referenceTick = tick;
    }

    private void pointTo(final int movedX, final int movedZ, final double moved, final long elapsed,
                         final int loadViewDistance, final int lookAheadTicks) {
        if (moved == 0.0) {
            this.directionX = 0.0;
            this.directionZ = 0.0;
            return;
        }
        final double ahead = moved * lookAheadTicks / elapsed;
        final double weight = Math.min(MAX_WEIGHT, ahead / Math.max(1, loadViewDistance));
        this.directionX = movedX / moved * weight;
        this.directionZ = movedZ / moved * weight;
    }

    /**
     * The queue order of a chunk at the given offset from the player: its squared distance, shrunk ahead of a moving
     * player and grown behind it (times {@code 1 - w cos θ}, θ the angle to the direction of travel, w its weight).
     * Along any one direction the order stays by distance, so the chunks right in front never wait for farther ones.
     */
    public double priority(final int diffX, final int diffZ) {
        final double squared = (double) diffX * diffX + (double) diffZ * diffZ;
        if (this.directionX == 0.0 && this.directionZ == 0.0) {
            return squared;
        }
        return squared - Math.sqrt(squared) * (diffX * this.directionX + diffZ * this.directionZ);
    }
}
