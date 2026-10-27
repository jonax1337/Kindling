package net.kindlingmc.pregen;

/**
 * The rate of a growing count over its last few samples, so the figure follows the current pace: a pause, a resume or
 * a stretch of chunks that only load from disk fades out of it once it leaves the window.
 */
final class RateWindow {

    private static final double NANOS_PER_SECOND = 1e9;

    private final long[] nanos;
    private final long[] counts;
    private int oldest;
    private int size;

    RateWindow(final int samples) {
        this.nanos = new long[samples];
        this.counts = new long[samples];
    }

    void sample(final long nowNanos, final long count) {
        final int slot = (this.oldest + this.size) % this.nanos.length;
        if (this.size == this.nanos.length) {
            this.oldest = (this.oldest + 1) % this.nanos.length;
        } else {
            this.size++;
        }
        this.nanos[slot] = nowNanos;
        this.counts[slot] = count;
    }

    double perSecond() {
        if (this.size < 2) {
            return 0.0;
        }
        final int newest = (this.oldest + this.size - 1) % this.nanos.length;
        final long elapsed = this.nanos[newest] - this.nanos[this.oldest];
        return elapsed <= 0 ? 0.0 : (this.counts[newest] - this.counts[this.oldest]) * NANOS_PER_SECOND / elapsed;
    }
}
