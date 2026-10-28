package net.kindlingmc.pregen;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * How many chunks a pregeneration may have in flight: while the average tick takes longer than the budget, or the main
 * thread waits too long for a CPU, it halves them and allows none; while both stay well below their limits it adds one a
 * second, up to the configured maximum.
 */
final class Pacing {

    private static final int TICKS_PER_SECOND = 20;
    private static final double NANOS_PER_MILLI = 1e6;
    private static final double PERCENT = 100;

    private int parallel = 1;
    private Throttle throttle = Throttle.NONE;
    /** What the throttle measured and the limit it went over, in the unit of its message. */
    private double throttleMeasured;
    private double throttleLimit;

    /** @return how many chunks may be in flight now; 0 while the server is too busy */
    int allowedInFlight(final long tick, final long averageTickNanos, final OptionalDouble mainThreadWait, final PregenLimits limits) {
        if (limits.tickTooSlow(averageTickNanos)) {
            this.throttle(Throttle.TICK_TIME, averageTickNanos / NANOS_PER_MILLI, limits.budgetNanos() / NANOS_PER_MILLI);
            return 0;
        }
        if (limits.mainThreadWaitsTooLong(mainThreadWait)) {
            this.throttle(Throttle.MAIN_THREAD_WAIT, mainThreadWait.getAsDouble() * PERCENT, limits.maxMainThreadWait() * PERCENT);
            return 0;
        }
        this.throttle = Throttle.NONE;
        if (tick % TICKS_PER_SECOND == 0 && limits.relaxed(averageTickNanos, mainThreadWait)) {
            this.parallel = Math.min(limits.maxParallel(), this.parallel + 1);
        }
        return this.parallel;
    }

    /** The chunks in flight the server allows at the moment, as the status line says it ({@code up to 3}). */
    int parallel() {
        return this.parallel;
    }

    /** Why nothing is scheduled for now, as the status line says it; empty when nothing holds the task back. */
    String describeThrottle() {
        return this.throttle.describe(this.throttleMeasured, this.throttleLimit);
    }

    private void throttle(final Throttle cause, final double measured, final double limit) {
        this.parallel = Math.max(1, this.parallel / 2);
        this.throttle = cause;
        this.throttleMeasured = measured;
        this.throttleLimit = limit;
    }

    private enum Throttle {
        NONE(""),
        TICK_TIME(", throttled: %.0f ms average > %.0f ms"),
        MAIN_THREAD_WAIT(", throttled: the main thread waited for a CPU %.0f %% of the last minute > %.0f %%");

        private final String format;

        Throttle(final String format) {
            this.format = format;
        }

        String describe(final double measured, final double limit) {
            return this == NONE ? "" : String.format(Locale.ROOT, this.format, measured, limit);
        }
    }
}
