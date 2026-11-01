package net.kindlingmc.preview;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.kindlingmc.chunk.GenerationBacklog;
import org.dreeam.leaf.config.modules.network.PreviewChunks;

/**
 * The background threads that compute previews, shared by all worlds and players: {@code threads} workers for players
 * on the move, and one unhurried worker for players standing still, whose rings can fill slowly without costing the
 * server more than one core. Daemon threads at the lowest priority, so previews never hold up the server's own work or
 * its shutdown. The thread count is read once, on the first preview; changing it needs a restart.
 */
final class PreviewWorkers {
    private static final String THREAD_NAME = "Kindling Preview Worker #";
    private static final String UNHURRIED_THREAD_NAME = "Kindling Preview Worker (unhurried) #";
    /**
     * Steps waiting on average above which new terrain counts as backlogged: the level below which the chunk worker
     * demand calls generation quiet (DemandState.QUIET_BELOW_WAITING).
     */
    private static final double BACKLOGGED_ABOVE_WAITING = 8.0;
    /** Fair, so that the unhurried worker gets its turn between the shared ones. */
    private static final Semaphore BACKLOG_TURN = new Semaphore(1, true);

    private PreviewWorkers() {
    }

    static void execute(final Runnable task) {
        Pools.SHARED.execute(task);
    }

    /** On the one unhurried worker, after every preview queued there before. */
    static void executeUnhurried(final Runnable task) {
        Pools.UNHURRIED.execute(task);
    }

    /**
     * Computes a seed preview, but while new terrain waits for the chunk workers ({@link GenerationBacklog}) only one at
     * a time: real chunks come first, and the previews of every player share one worker until the backlog is gone.
     * Saved and cached previews are cheap and do not take turns.
     */
    static PreviewPacket yieldingToGeneration(final Supplier<PreviewPacket> seedPreview) {
        if (GenerationBacklog.latestAverageWaiting() <= BACKLOGGED_ABOVE_WAITING) {
            return seedPreview.get();
        }
        BACKLOG_TURN.acquireUninterruptibly();
        try {
            return seedPreview.get();
        } finally {
            BACKLOG_TURN.release();
        }
    }

    private static ThreadFactory daemonThreads(final String name) {
        final AtomicInteger count = new AtomicInteger();
        return task -> {
            final Thread thread = new Thread(task, name + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    /** Created on first use (the JVM initialises a class when it is first touched); threads start with their first task. */
    private static final class Pools {
        static final ExecutorService SHARED = Executors.newFixedThreadPool(PreviewChunks.threads, daemonThreads(THREAD_NAME));
        static final ExecutorService UNHURRIED = Executors.newSingleThreadExecutor(daemonThreads(UNHURRIED_THREAD_NAME));
    }
}
