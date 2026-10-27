package net.kindlingmc.save;

import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/**
 * File writes that run on one background thread in the order they were queued, so the newest queued write of a file
 * is also the last one to reach the disk.
 * <p>
 * A caller that reads a file back waits for its pending write ({@link #awaitWrite}); a caller that promises data on
 * disk waits for everything queued so far ({@link #awaitQueuedWrites}). No write is dropped: one queued after
 * {@link #shutdown} runs on the caller's thread once the writes queued before it are on disk. Waiting has no time
 * limit, because giving up would let a reader see the file from before the write; a slow disk shows up as a warning
 * every stall interval instead, the first one with the stack of the write thread.
 */
public final class SerialWrites<K> {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final ExecutorService executor;
    private final long stallIntervalNanos;
    private final Map<K, Future<?>> newestWriteByKey = new HashMap<>();
    private volatile Thread writeThread;

    /**
     * @param executor runs one task at a time in submission order and throws {@link RejectedExecutionException} once
     *                 it is shut down, like {@code Executors.newSingleThreadExecutor()}
     */
    public SerialWrites(final ExecutorService executor, final Duration stallInterval) {
        this.executor = executor;
        this.stallIntervalNanos = stallInterval.toNanos();
    }

    public void queue(final K key, final Runnable write) {
        final Write task = new KeyedWrite(key, write);
        // Registering and queueing under one lock keeps the registered write the last queued one for its key.
        synchronized (this.newestWriteByKey) {
            this.newestWriteByKey.put(key, task);
            if (this.tryQueue(task)) {
                return;
            }
        }
        this.runAfterQueuedWrites(task);
    }

    public void queue(final Runnable write) {
        this.queueTask(new Write(write));
    }

    public void awaitWrite(final K key) {
        final Future<?> write;
        synchronized (this.newestWriteByKey) {
            write = this.newestWriteByKey.get(key);
        }
        if (write != null) {
            this.awaitStalling(timeout -> isDone(write, timeout), "the pending write of " + key);
        }
    }

    public void awaitQueuedWrites() {
        final Write barrier = new Write(() -> {});
        this.queueTask(barrier);
        this.awaitStalling(timeout -> isDone(barrier, timeout), "the queued writes");
    }

    /** Takes no more writes onto the write thread and waits up to {@code timeout} for the queued ones. */
    public void shutdown(final Duration timeout) {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                LOGGER.error("Queued writes are still not on disk after {} s; the server goes on stopping without them",
                    timeout.toSeconds(), this.writeThreadStack());
            }
        } catch (final InterruptedException interrupted) {
            LOGGER.error("Interrupted while waiting for the queued writes; they may not be on disk", interrupted);
            Thread.currentThread().interrupt();
        }
    }

    private void queueTask(final Write task) {
        if (!this.tryQueue(task)) {
            this.runAfterQueuedWrites(task);
        }
    }

    private boolean tryQueue(final Write task) {
        try {
            this.executor.execute(task);
            return true;
        } catch (final RejectedExecutionException shutDown) {
            return false;
        }
    }

    private void runAfterQueuedWrites(final Write task) {
        this.awaitStalling(
            timeout -> this.executor.awaitTermination(timeout, TimeUnit.NANOSECONDS), "the writes queued before shutdown"
        );
        task.run();
    }

    private void awaitStalling(final Wait wait, final String what) {
        boolean interrupted = false;
        int stalls = 0;
        while (true) {
            try {
                if (wait.isDone(this.stallIntervalNanos)) {
                    break;
                }
                stalls++;
                this.reportStall(what, stalls);
            } catch (final InterruptedException keepWaiting) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void reportStall(final String what, final int stalls) {
        final long waitedSeconds = TimeUnit.NANOSECONDS.toSeconds(this.stallIntervalNanos * stalls);
        if (stalls == 1) {
            LOGGER.warn("Waiting {} s for {} to reach the disk", waitedSeconds, what, this.writeThreadStack());
        } else {
            LOGGER.warn("Still waiting for {} to reach the disk, {} s so far", what, waitedSeconds);
        }
    }

    private Throwable writeThreadStack() {
        final Thread thread = this.writeThread;
        if (thread == null) {
            return new Throwable("No write has started yet");
        }
        final Throwable stack = new Throwable("Stack of the write thread " + thread.getName());
        stack.setStackTrace(thread.getStackTrace());
        return stack;
    }

    private static boolean isDone(final Future<?> write, final long timeoutNanos) throws InterruptedException {
        try {
            write.get(timeoutNanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (final TimeoutException stillRunning) {
            return false;
        } catch (final ExecutionException | CancellationException finishedWithoutResult) {
            return true;
        }
    }

    @FunctionalInterface
    private interface Wait {
        boolean isDone(long timeoutNanos) throws InterruptedException;
    }

    private class Write extends FutureTask<Void> {

        Write(final Runnable write) {
            super(write, null);
        }

        @Override
        public void run() {
            SerialWrites.this.writeThread = Thread.currentThread();
            super.run();
        }

        @Override
        protected void setException(final Throwable failure) {
            LOGGER.error("A queued write failed", failure);
            super.setException(failure);
        }
    }

    private final class KeyedWrite extends Write {

        private final K key;

        KeyedWrite(final K key, final Runnable write) {
            super(write);
            this.key = key;
        }

        @Override
        protected void done() {
            synchronized (SerialWrites.this.newestWriteByKey) {
                SerialWrites.this.newestWriteByKey.remove(this.key, this);
            }
        }
    }
}
