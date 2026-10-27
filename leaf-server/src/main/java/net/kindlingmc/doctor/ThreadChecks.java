package net.kindlingmc.doctor;

import java.util.Optional;

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
}
