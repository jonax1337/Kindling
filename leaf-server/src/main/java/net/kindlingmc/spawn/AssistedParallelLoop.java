package net.kindlingmc.spawn;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Runs a number of independent tasks on a few helper threads with the calling thread taking part. The caller never
 * waits for a helper to start: it claims tasks itself until none is left, so with every helper busy elsewhere it does
 * all the work alone. It then waits only for the tasks helpers already claimed. A helper that starts after that does
 * nothing, so once {@link #run} returns no task runs any more and the caller may change what the tasks read.
 */
public final class AssistedParallelLoop {

    private final ExecutorService helpers;
    private final int helperCount;

    public AssistedParallelLoop(final int helperCount, final String threadName) {
        this.helperCount = helperCount;
        this.helpers = Executors.newFixedThreadPool(helperCount, daemonThreads(threadName));
    }

    private static ThreadFactory daemonThreads(final String name) {
        final AtomicInteger number = new AtomicInteger();
        return task -> {
            final Thread thread = new Thread(task, name + " #" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Runs {@code task} once for every index below {@code count} and returns when all runs have finished. A task that
     * throws does not stop the others; the first failure is rethrown here after the last task finished.
     */
    public void run(final int count, final IntConsumer task) {
        final Job job = new Job(count, task);
        final int helpersWanted = Math.min(this.helperCount, count - 1);
        for (int i = 0; i < helpersWanted; i++) {
            this.helpers.execute(job::help);
        }
        job.work();
        job.close();
        job.rethrowFailure();
    }

    public void shutdown() {
        this.helpers.shutdown();
    }

    private static final class Job {
        private final int count;
        private final IntConsumer task;
        private final AtomicInteger nextIndex = new AtomicInteger();
        private final AtomicInteger activeHelpers = new AtomicInteger();
        private volatile boolean closed;
        private volatile Throwable failure;

        private Job(final int count, final IntConsumer task) {
            this.count = count;
            this.task = task;
        }

        /*
         * The helper announces itself before it looks at `closed`, and the caller sets `closed` before it looks at the
         * helpers: either the caller sees the helper and waits for it, or the helper sees the job closed and leaves
         * without touching anything.
         */
        private void help() {
            this.activeHelpers.incrementAndGet();
            try {
                if (!this.closed) {
                    this.work();
                }
            } finally {
                this.activeHelpers.decrementAndGet();
            }
        }

        private void work() {
            for (int index = this.nextIndex.getAndIncrement(); index < this.count; index = this.nextIndex.getAndIncrement()) {
                this.runTask(index);
            }
        }

        private void runTask(final int index) {
            try {
                this.task.accept(index);
            } catch (final Throwable t) {
                if (this.failure == null) {
                    this.failure = t;
                }
            }
        }

        private void close() {
            this.closed = true;
            while (this.activeHelpers.get() != 0) {
                Thread.onSpinWait();
            }
        }

        private void rethrowFailure() {
            final Throwable t = this.failure;
            if (t instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (t instanceof Error error) {
                throw error;
            }
            if (t != null) {
                throw new IllegalStateException("A parallel task failed", t);
            }
        }
    }
}
