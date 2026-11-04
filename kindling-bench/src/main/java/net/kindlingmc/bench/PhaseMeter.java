package net.kindlingmc.bench;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What one phase cost beyond its ticks, as the change between {@link #start()} and {@link #stop()}: wall time, garbage
 * collection pauses, the heap bytes the main thread and all threads allocated, the CPU time of all threads of the JVM and, on Linux, how long the main thread
 * ran and waited for a CPU and how much CPU time of the machine was idle or stolen.
 */
final class PhaseMeter {

    private static final double NANOS_PER_SECOND = 1e9;
    private static final double NANOS_PER_MILLI = 1e6;
    private static final double PERCENT = 100.0;

    private static final com.sun.management.OperatingSystemMXBean OPERATING_SYSTEM =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private final SchedulerStats scheduler;
    private long startNanos;
    private GcTotals startGc = new GcTotals(0, 0);
    private AllocatedBytes startAllocated = new AllocatedBytes(0, 0);
    private long startProcessCpuNanos;
    private Optional<SchedulerStats.Sample> startSample = Optional.empty();

    /** @param scheduler the statistics of the main thread, which is the thread that measures the phases */
    PhaseMeter(SchedulerStats scheduler) {
        this.scheduler = scheduler;
    }

    void start() {
        startNanos = System.nanoTime();
        startGc = GcTotals.ofPauses();
        startAllocated = AllocatedBytes.now();
        startProcessCpuNanos = processCpuNanos();
        startSample = scheduler.sample();
    }

    Map<String, Object> stop() {
        long wallNanos = System.nanoTime() - startNanos;
        GcTotals gc = GcTotals.ofPauses().since(startGc);
        AllocatedBytes endAllocated = AllocatedBytes.now();
        long processCpuNanos = processCpuNanos() - startProcessCpuNanos;
        Optional<SchedulerStats.Sample> endSample = scheduler.sample();
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("wall_seconds", round(wallNanos / NANOS_PER_SECOND));
        figures.put("gc_count", gc.collections());
        figures.put("gc_pause_millis", gc.millis());
        if (startAllocated.isCounted() && endAllocated.isCounted()) {
            figures.putAll(allocationFigures(endAllocated.since(startAllocated)));
        }
        if (startProcessCpuNanos >= 0) {
            figures.put("process_cpu_ms", round(processCpuNanos / NANOS_PER_MILLI));
        }
        if (startSample.isPresent() && endSample.isPresent()) {
            figures.putAll(schedulerFigures(startSample.get(), endSample.get()));
        }
        return figures;
    }

    /** CPU time of all threads of the JVM so far, or -1 where the JVM does not tell. */
    private static long processCpuNanos() {
        return OPERATING_SYSTEM.getProcessCpuTime();
    }

    private static Map<String, Object> allocationFigures(AllocatedBytes allocated) {
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("main_alloc_mb", allocated.currentThread() / ServerState.BYTES_PER_MB);
        figures.put("all_alloc_mb", allocated.allThreads() / ServerState.BYTES_PER_MB);
        return figures;
    }

    private static Map<String, Object> schedulerFigures(SchedulerStats.Sample start, SchedulerStats.Sample end) {
        long cpuTotal = end.cpuTotal() - start.cpuTotal();
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("main_run_ms", round((end.threadRunNanos() - start.threadRunNanos()) / NANOS_PER_MILLI));
        figures.put("main_wait_ms", round((end.threadWaitNanos() - start.threadWaitNanos()) / NANOS_PER_MILLI));
        figures.put("cpu_steal_pct", share(end.cpuSteal() - start.cpuSteal(), cpuTotal));
        figures.put("cpu_idle_pct", share(end.cpuIdle() - start.cpuIdle(), cpuTotal));
        return figures;
    }

    private static double share(long part, long whole) {
        return whole <= 0 ? 0 : round(PERCENT * part / whole);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
