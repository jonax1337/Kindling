package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/**
 * Writes snapshots of a state to disk on a thread of its own, so the main thread never waits for the fsync.
 * Snapshots that pile up while a write runs are replaced by the newest one. {@link #writeAndClose} writes the last state
 * for the shutdown, on the same thread after the write in progress, so two writes never touch the file at once.
 *
 * @param <S> the snapshot type; it must not be changed after it was handed over
 */
final class StateWriter<S> {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long CLOSE_TIMEOUT_SECONDS = 10L;
    private static final String PREGENERATION_STATE = "pregeneration state";

    /** Saves a state to the file; replaced in the tests, which need a write that takes long. */
    @FunctionalInterface
    interface Saving<S> {
        void save(S state, Path file) throws IOException;
    }

    private final String what;
    private final Path file;
    private final Saving<S> saving;
    private final long closeTimeoutMillis;
    private final ExecutorService executor;
    private final AtomicReference<S> newest = new AtomicReference<>();

    static StateWriter<PregenState> forRuns(final Path file) {
        return new StateWriter<>(PREGENERATION_STATE, file, PregenState::save);
    }

    StateWriter(final String what, final Path file, final Saving<S> saving) {
        this(what, file, saving, TimeUnit.SECONDS.toMillis(CLOSE_TIMEOUT_SECONDS));
    }

    /** @param what names the state in log messages and in the name of the writer thread, e.g. {@code pregeneration state} */
    StateWriter(final String what, final Path file, final Saving<S> saving, final long closeTimeoutMillis) {
        this.what = what;
        this.file = file;
        this.saving = saving;
        this.closeTimeoutMillis = closeTimeoutMillis;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "Kindling " + what + " writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Takes ownership of the snapshot: it must not be changed afterwards. */
    void writeLater(final S snapshot) {
        this.newest.set(snapshot);
        this.executor.execute(this::writeNewest);
    }

    private void writeNewest() {
        final S snapshot = this.newest.getAndSet(null);
        if (snapshot != null) {
            this.write(snapshot);
        }
    }

    /**
     * Queues {@code last} behind the write in progress and stops the writer thread, then waits for it. If that takes
     * longer than the timeout the last state may still be unwritten; it is never written from this thread meanwhile.
     */
    void writeAndClose(final S last) {
        this.newest.set(null);
        this.executor.execute(() -> this.write(last));
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(this.closeTimeoutMillis, TimeUnit.MILLISECONDS)) {
                LOGGER.error("The {} writer did not finish in {} ms, the last state may not be written", this.what, this.closeTimeoutMillis);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(final S state) {
        try {
            this.saving.save(state, this.file);
        } catch (final IOException e) {
            LOGGER.error("Cannot write the {} {}", this.what, this.file, e);
        }
    }
}
