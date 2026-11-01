package net.kindlingmc.preview;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.dreeam.leaf.config.modules.network.PreviewChunks;

/**
 * The background threads that compute previews, shared by all worlds and players. Daemon threads at the lowest
 * priority, so previews never hold up the server's own work or its shutdown. The thread count is read once, on the
 * first preview; changing it needs a restart.
 */
final class PreviewWorkers {
    private static final String THREAD_NAME = "Kindling Preview Worker #";

    private static volatile ExecutorService pool;

    private PreviewWorkers() {
    }

    static void execute(final Runnable task) {
        pool().execute(task);
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
