package net.kindlingmc.chunk;

/**
 * The chunk worker count {@link WorkerDemand} has chosen, as a pure function of the backlog samples it has seen. When
 * more than {@link #START_ABOVE_WAITING} generation steps waited on average, every configured worker runs at once; after
 * a quiet period in which fewer than {@link #QUIET_BELOW_WAITING} waited, the count falls back to the floor. Paper's
 * quarter of the cores keeps up with loading saved chunks and with the new chunks at the view edge of a flight through
 * generated terrain, and the extra workers only crowd the main thread there (docs/measurements/host.md).
 *
 * @param workers       the chosen count
 * @param quietSamples  samples in a row in which fewer than {@link #QUIET_BELOW_WAITING} steps waited
 */
public record DemandState(int workers, int quietSamples) {

    /** A flight through generated terrain never passed 16 on the bench; exploring new terrain stays far above it. */
    static final double START_ABOVE_WAITING = 16.0;
    static final double QUIET_BELOW_WAITING = 8.0;

    /** The state before the first sample: every configured worker runs. */
    public static DemandState startingAt(final int workers) {
        return new DemandState(workers, 0);
    }

    public DemandState next(final double averageWaiting, final GovernorLimits limits) {
        if (averageWaiting > START_ABOVE_WAITING) {
            return new DemandState(limits.ceiling(), 0);
        }
        final int workers = Math.clamp(this.workers, limits.floor(), limits.ceiling());
        if (averageWaiting >= QUIET_BELOW_WAITING) {
            return new DemandState(workers, 0);
        }
        final int quiet = this.quietSamples + 1;
        return new DemandState(quiet >= limits.quietSamples() ? limits.floor() : workers, quiet);
    }
}
