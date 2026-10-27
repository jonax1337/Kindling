package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Time a thread or a group of threads spent running and waiting in a CPU's run queue, as the kernel counts it in
 * {@code /proc/<pid>/task/<tid>/schedstat}. A counter that only grows, or the growth between two readings.
 */
record SchedTime(long runNanos, long waitNanos) {

    SchedTime plus(final SchedTime other) {
        return new SchedTime(this.runNanos + other.runNanos, this.waitNanos + other.waitNanos);
    }

    /** The growth since an earlier reading; empty when a counter went backwards, as it does when a thread exits. */
    Optional<SchedTime> since(final SchedTime earlier) {
        final long run = this.runNanos - earlier.runNanos;
        final long wait = this.waitNanos - earlier.waitNanos;
        return run < 0 || wait < 0 ? Optional.empty() : Optional.of(new SchedTime(run, wait));
    }

    /** The share of the time the threads wanted a CPU in which they had to wait for one; empty when they never ran. */
    OptionalDouble waitShare() {
        final long wanted = this.runNanos + this.waitNanos;
        return wanted <= 0 ? OptionalDouble.empty() : OptionalDouble.of((double) this.waitNanos / wanted);
    }
}
