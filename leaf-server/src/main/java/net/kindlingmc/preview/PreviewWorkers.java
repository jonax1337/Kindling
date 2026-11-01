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
 * The background threads that compute previews, shared by all worlds and players. Daemon threads at the lowest
 * priority, so previews never hold up the server's own work or its shutdown. The thread count is read once, on the
 * first preview; changing it needs a restart.
 */
final class PreviewWorkers {
    private static final String THREAD_NAME = "Kindling Preview Worker #";
    /**
     * Steps waiting on average above which new terrain counts as backlogged: the level below which the chunk worker
     * demand calls generation quiet (DemandState.QUIET_BELOW_WAITING).
     */
    private static final double BACKLOGGED_ABOVE_WAITING = 8.0;
    private static final Semaphore BACKLOG_TURN = new Semaphore(1);

    private static volatile ExecutorService pool;

    private PreviewWorkers() {
    }

    static void execute(final Runnable task) {
        pool().execute(task);
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

    private static ExecutorService pool() {
        ExecutorService current = pool;
        if (current == null) {
            synchronized (PreviewWorkers.class) {
                current = pool;
                if (current == null) {
                    current = Executors.newFixedThreadPool(PreviewChunks.threads, threads());
                    pool = current;
                }
            }
        }
        return current;
    }

    private static ThreadFactory threads() {
        final AtomicInteger count = new AtomicInteger();
        return task -> {
            final Thread thread = new Thread(task, THREAD_NAME + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }
}
