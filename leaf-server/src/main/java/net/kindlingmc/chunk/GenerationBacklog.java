package net.kindlingmc.chunk;

import java.util.concurrent.atomic.LongAdder;

/**
 * How long generation steps of new terrain waited for a chunk worker, summed over every step. The time summed over a
 * sample divided by the sample's length is the average number of steps that waited (Little's law): about zero while
 * the workers keep up, and the length of the backlog while players outrun them. Loading saved chunks is not counted.
 * Written by the chunk workers and the threads that schedule chunk tasks, read by {@link WorkerDemand}.
 */
public final class GenerationBacklog {

    private static final LongAdder WAITED_NANOS = new LongAdder();
    private static volatile double latestAverageWaiting;

    private GenerationBacklog() {
    }

    /** Called when a generation step is queued; the result goes back to {@link #stoppedWaiting}. */
    public static long queued() {
        return System.nanoTime();
    }

    /** Called once per queued step, when a worker starts it or it is cancelled before it started. */
    public static void stoppedWaiting(final long queuedNanos) {
        WAITED_NANOS.add(System.nanoTime() - queuedNanos);
    }

    /** The waiting time summed since the previous call. */
    static long takeWaitedNanos() {
        return WAITED_NANOS.sumThenReset();
    }

    /**
     * The average number of generation steps that waited for a chunk worker in {@link WorkerDemand}'s latest sample
     * (one per second, whether or not it scales the workers); 0 before the first. Readable from any thread.
     */
    public static double latestAverageWaiting() {
        return latestAverageWaiting;
    }

    static void recordSample(final double averageWaiting) {
        latestAverageWaiting = averageWaiting;
    }
}
