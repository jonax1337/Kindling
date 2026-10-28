package net.kindlingmc.doctor;

import java.util.Optional;
import net.kindlingmc.chunk.GovernorLimits;
import net.kindlingmc.chunk.GovernorStatus;
import net.kindlingmc.chunk.HostPressure;

/** Checks of how the server's threads share the CPUs. */
final class ThreadChecks {

    private ThreadChecks() {
    }

    /** Whether {@code performance.worker-priority} is on and, where the kernel tells, whether it took effect. */
    static Optional<Finding> workerPriority(final WorkerPriorityFacts facts) {
        if (facts.workerNice().isEmpty()) {
            return facts.enabled() ? Optional.of(Finding.note(
                "worker-priority is on: chunk and I/O worker threads start at a lower priority than the main thread; "
                    + "this system does not let the doctor read it back",
                Basis.GENERAL_ADVICE)) : Optional.empty();
        }
        final int workerNice = facts.workerNice().getAsInt();
        if (!facts.enabled()) {
            return Optional.of(Finding.note(
                "Chunk worker threads run at nice " + workerNice + ": Linux ignores Java thread priorities, so they compete with the "
                    + "main thread on equal terms; performance.worker-priority lowers them (off until the bench shows it helps)",
                Basis.LIVE_READING));
        }
        if (workerNice >= facts.nice()) {
            return Optional.of(Finding.ok("worker-priority is on and works: chunk worker threads run at nice " + workerNice));
        }
        return Optional.of(Finding.warn(
            "worker-priority asks for nice " + facts.nice() + ", but chunk worker threads run at nice " + workerNice,
            "Look in the log from the start for \"Kindling could not lower the priority\"; the setting applies to threads started "
                + "after it was read, so restart the server after turning it on",
            Basis.LIVE_READING));
    }

    /** Whether {@code performance.worker-governor} runs, and how many chunk workers it lets run now. */
    static Optional<Finding> workerGovernor(final WorkerGovernorFacts facts) {
        if (!facts.enabled()) {
            return Optional.empty();
        }
        return Optional.of(facts.status().map(ThreadChecks::governing).orElseGet(() -> Finding.note(
            "worker-governor is on but has not run: it needs Linux and performance.doctor.host-monitor, and starts with the first "
                + "host sample a few seconds after start-up; until then the configured number of chunk workers runs",
            Basis.LIVE_READING)));
    }

    private static Finding governing(final GovernorStatus status) {
        final GovernorLimits limits = status.limits();
        if (!limits.leavesRoom()) {
            return Finding.note("worker-governor is on but has nothing to choose: " + limits.ceiling()
                + " chunk worker thread(s) are configured, not more than Paper's default for this machine", Basis.LIVE_READING);
        }
        return Finding.note("worker-governor runs " + status.workers() + " chunk worker thread(s), between " + limits.floor()
            + " (Paper's default) and " + limits.ceiling() + "; in the latest host sample " + reading(status.pressure()), Basis.LIVE_READING);
    }

    private static String reading(final HostPressure pressure) {
        return switch (pressure) {
            case HIGH -> "the main thread waited for CPUs others took or the container limit throttled the server, so workers go";
            case MODERATE -> "the host was neither quiet nor crowded, so the count stays";
            case LOW -> "the host was quiet, so a worker comes back after the quiet period";
            case UNKNOWN -> "nothing readable was measured, so the count stays";
        };
    }
}
