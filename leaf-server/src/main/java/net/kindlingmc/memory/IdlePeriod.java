package net.kindlingmc.memory;

/**
 * Tells when the server has been without players for long enough to give memory back: once per idle period, which
 * ends when a player is online again. The clock is passed in so the rule is testable.
 */
final class IdlePeriod {

    private final long afterNanos;
    private boolean idle;
    private long idleSinceNanos;
    private boolean reclaimed;

    IdlePeriod(final long afterNanos) {
        this.afterNanos = afterNanos;
    }

    /** Whether the memory should be given back now; true once per idle period. */
    boolean shouldReclaim(final long nowNanos, final boolean playersOnline) {
        if (playersOnline) {
            this.idle = false;
            this.reclaimed = false;
            return false;
        }
        if (!this.idle) {
            this.idle = true;
            this.idleSinceNanos = nowNanos;
        }
        if (this.reclaimed || nowNanos - this.idleSinceNanos < this.afterNanos) {
            return false;
        }
        this.reclaimed = true;
        return true;
    }
}
