package net.kindlingmc.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What the Linux scheduler reports about the server main thread (time on the CPU and time waiting in the run queue)
 * and about the machine (idle and stolen CPU time). Nothing where {@code /proc} is missing or unreadable.
 */
final class SchedulerStats {

    /** Nanoseconds the thread ran and waited, and jiffies the machine's CPUs were idle, stolen and in total. */
    record Sample(long threadRunNanos, long threadWaitNanos, long cpuIdle, long cpuSteal, long cpuTotal) {
    }

    private static final Path THREAD_SELF = Path.of("/proc/thread-self");
    private static final Path SYSTEM_STAT = Path.of("/proc/stat");
    private static final String THREAD_SCHEDSTAT = "/proc/self/task/%s/schedstat";
    private static final int IDLE_COLUMN = 4;
    private static final int STEAL_COLUMN = 8;

    private final Optional<Path> threadSchedstat;

    private SchedulerStats(Optional<Path> threadSchedstat) {
        this.threadSchedstat = threadSchedstat;
    }

    /** The statistics of the thread this is called on, which must be the server main thread. */
    static SchedulerStats ofCurrentThread() {
        try {
            String threadId = Files.readSymbolicLink(THREAD_SELF).getFileName().toString();
            return new SchedulerStats(Optional.of(Path.of(THREAD_SCHEDSTAT.formatted(threadId))));
        } catch (IOException | UnsupportedOperationException e) {
            return new SchedulerStats(Optional.empty());
        }
    }

    Optional<Sample> sample() {
        if (threadSchedstat.isEmpty()) {
            return Optional.empty();
        }
        try {
            String[] thread = Files.readString(threadSchedstat.get()).trim().split("\\s+");
            String[] cpu = Files.readAllLines(SYSTEM_STAT).getFirst().trim().split("\\s+");
            return Optional.of(new Sample(Long.parseLong(thread[0]), Long.parseLong(thread[1]),
                    Long.parseLong(cpu[IDLE_COLUMN]), Long.parseLong(cpu[STEAL_COLUMN]), totalOf(cpu)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Guest time is part of user time already, so only the first eight columns (user to steal) add up to the total. */
    private static long totalOf(String[] cpuLine) {
        long total = 0;
        for (int column = 1; column <= STEAL_COLUMN; column++) {
            total += Long.parseLong(cpuLine[column]);
        }
        return total;
    }
}
