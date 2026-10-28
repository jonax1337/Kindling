package net.kindlingmc.doctor;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/** Checks of what the host monitor measured: is the lag this server's, or the machine's? */
final class HostChecks {

    /** The first samples say little: a check needs this much of the last minute. */
    private static final long MIN_COVERED_SECONDS = 10;
    private static final double MAIN_THREAD_WAIT_WARNING = 0.10;
    /**
     * Below this the waits are wake-up latency of a thread with little to do, a few microseconds each on a virtual
     * machine, not CPUs taken by others: a nearly idle server on the VPS waited 11 to 14 % of its time but about
     * 3 ms per second, a loaded bench host 25 to 50 ms per second.
     */
    private static final double MAIN_THREAD_WAIT_MILLIS_PER_SECOND_WARNING = 20;
    private static final double STEAL_WARNING = 0.05;
    private static final double THROTTLED_PERIODS_WARNING = 0.01;
    private static final double SMALL_SHARE_PERCENT = 10;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long LONGEST_SPAN_IN_SECONDS = 120;

    private HostChecks() {
    }

    static Optional<Finding> mainThreadWait(final HostLoads live) {
        final OptionalDouble share = shareOf(live.lastMinute(), HostLoad::mainThreadWaitShare);
        if (share.isEmpty()) {
            return Optional.empty();
        }
        final double millisPerSecond = live.lastMinute().mainThreadWaitMillisPerSecond().orElse(0);
        final String measured = "Main thread waited for a CPU " + percent(share.getAsDouble()) + " of the time it had work to do in the last "
            + span(live.lastMinute()) + trend(live, HostLoad::mainThreadWaitShare)
            + String.format(Locale.ROOT, ", %.1f ms per second", millisPerSecond);
        if (share.getAsDouble() <= MAIN_THREAD_WAIT_WARNING) {
            return Optional.of(Finding.note(measured + ": CPU contention is not what slows it down", Basis.LIVE_READING));
        }
        if (millisPerSecond < MAIN_THREAD_WAIT_MILLIS_PER_SECOND_WARNING) {
            return Optional.of(Finding.note(
                measured + ": a thread with little to do waits a few microseconds each time it wakes up, as on virtual machines, so this is not contention either",
                Basis.LIVE_READING));
        }
        return Optional.of(Finding.warn(
            measured + ": other threads or processes on this machine compete with it",
            "Give the server CPUs of its own: stop other programs on this machine, reserve cores for the server (CPU affinity or a container CPU request), "
                + "or lower the load the server's own helper threads cause, such as chunk worker threads (performance.worker-priority puts them "
                + "behind the main thread) and pregeneration",
            Basis.LIVE_READING));
    }

    static Optional<Finding> chunkWorkerWait(final HostLoads live) {
        final OptionalDouble share = shareOf(live.lastMinute(), HostLoad::chunkWorkerWaitShare);
        return share.isEmpty() ? Optional.empty() : Optional.of(Finding.note(
            "Chunk worker threads waited for a CPU " + percent(share.getAsDouble()) + " of the time they had work to do in the last "
                + span(live.lastMinute()) + trend(live, HostLoad::chunkWorkerWaitShare)
                + ": they share the processors with the main thread and the garbage collector, so this is high while chunks are generated",
            Basis.LIVE_READING));
    }

    static Optional<Finding> hostSteal(final HostLoads live) {
        final OptionalDouble share = shareOf(live.lastMinute(), HostLoad::stealShare);
        if (share.isEmpty()) {
            return Optional.empty();
        }
        final String measured = "The host took " + percent(share.getAsDouble()) + " of this machine's CPU time (steal) in the last "
            + span(live.lastMinute()) + trend(live, HostLoad::stealShare);
        if (share.getAsDouble() <= STEAL_WARNING) {
            return Optional.of(Finding.note(measured, Basis.LIVE_READING));
        }
        return Optional.of(Finding.warn(
            measured + ": this virtual machine shares its CPU with other customers, so lag that comes with it is the host's, not the server's",
            "Ask the provider about CPU steal or move to a host with dedicated cores; lowering the server's settings does not give the time back",
            Basis.LIVE_READING));
    }

    static Optional<Finding> cpuThrottling(final HostLoads live) {
        final OptionalDouble share = shareOf(live.lastMinute(), HostLoad::throttledShare);
        if (share.isEmpty()) {
            return Optional.empty();
        }
        final HostLoad minute = live.lastMinute();
        final String measured = "The container's CPU limit stopped the server in " + percent(share.getAsDouble()) + " of the scheduling periods ("
            + String.format(Locale.ROOT, "%.1f s", minute.throttledSeconds()) + " in total) in the last " + span(minute)
            + trend(live, HostLoad::throttledShare);
        if (share.getAsDouble() < THROTTLED_PERIODS_WARNING) {
            return Optional.of(Finding.note(measured + ": the limit is not what slows it down", Basis.LIVE_READING));
        }
        return Optional.of(Finding.warn(
            measured + ": the server wants more CPU than its container may use, and every stop shows as lag",
            "Raise the container's CPU limit (cpu.max, --cpus) or lower the server's thread counts, and start Java with -XX:ActiveProcessorCount matching the limit",
            Basis.LIVE_READING));
    }

    /** The value over the last minute, once the monitor has seen enough of it to say something. */
    private static OptionalDouble shareOf(final HostLoad minute, final Function<HostLoad, OptionalDouble> share) {
        return minute.coveredSeconds() < MIN_COVERED_SECONDS ? OptionalDouble.empty() : share.apply(minute);
    }

    /** The same value over the last 15 minutes, as context for the one over the last minute. */
    private static String trend(final HostLoads live, final Function<HostLoad, OptionalDouble> share) {
        final OptionalDouble longer = share.apply(live.last15Minutes());
        final boolean coversMore = live.last15Minutes().coveredNanos() > live.lastMinute().coveredNanos();
        return longer.isEmpty() || !coversMore ? "" : " (last " + span(live.last15Minutes()) + ": " + percent(longer.getAsDouble()) + ")";
    }

    private static String span(final HostLoad load) {
        final long seconds = load.coveredSeconds();
        return seconds < LONGEST_SPAN_IN_SECONDS ? seconds + " s" : Math.round((double) seconds / SECONDS_PER_MINUTE) + " min";
    }

    private static String percent(final double share) {
        final double percent = share * 100;
        return String.format(Locale.ROOT, percent < SMALL_SHARE_PERCENT ? "%.1f %%" : "%.0f %%", percent);
    }
}
