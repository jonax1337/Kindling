package net.kindlingmc.pregen;

import java.util.OptionalDouble;

/**
 * The settings a {@link PregenTask} obeys on every tick.
 *
 * @param maxMainThreadWait  the share of its time the main thread may wait for a CPU over the last minute; 0 for no limit
 */
record PregenLimits(long budgetNanos, int maxParallel, long minFreeDiskBytes, double maxMainThreadWait) {

    /** Below this share of a limit the server has room to spare, and a run may add chunks in flight. */
    private static final double RELAXED_SHARE = 0.75;

    boolean tickTooSlow(final long averageTickNanos) {
        return averageTickNanos > this.budgetNanos;
    }

    /** Never while the wait is not measured (not on Linux, or the host monitor is off). */
    boolean mainThreadWaitsTooLong(final OptionalDouble mainThreadWait) {
        return this.limitsMainThreadWait() && mainThreadWait.isPresent() && mainThreadWait.getAsDouble() > this.maxMainThreadWait;
    }

    /** Whether the tick time and the main thread's wait both stay well below their limits. */
    boolean relaxed(final long averageTickNanos, final OptionalDouble mainThreadWait) {
        final boolean waitRelaxed = !this.limitsMainThreadWait() || mainThreadWait.isEmpty()
            || mainThreadWait.getAsDouble() < this.maxMainThreadWait * RELAXED_SHARE;
        return averageTickNanos < this.budgetNanos * RELAXED_SHARE && waitRelaxed;
    }

    private boolean limitsMainThreadWait() {
        return this.maxMainThreadWait > 0;
    }
}
