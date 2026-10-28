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
 * Writes {@link PregenState} snapshots to disk on a thread of its own, so the main thread never waits for the fsync.
 * Snapshots that pile up while a write runs are replaced by the newest one. {@link #writeAndClose} writes the last state
 * for the shutdown, on the same thread after the write in progress, so two writes never touch the file at once.
 */
final class StateWriter {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long CLOSE_TIMEOUT_SECONDS = 10L;

    /** Saves a state to the file; replaced in the tests, which need a write that takes long. */
    @FunctionalInterface
    interface Saving {
        void save(PregenState state, Path file) throws IOException;
    }

    private final Path file;
    private final Saving saving;
    private final long closeTimeoutMillis;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "Kindling pregeneration state writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<PregenState> newest = new AtomicReference<>();

    StateWriter(final Path file) {
        this(file, PregenState::save, TimeUnit.SECONDS.toMillis(CLOSE_TIMEOUT_SECONDS));
    }

    StateWriter(final Path file, final Saving saving, final long closeTimeoutMillis) {
        this.file = file;
        this.saving = saving;
        this.closeTimeoutMillis = closeTimeoutMillis;
    }

    /** Takes ownership of the snapshot: it must not be changed afterwards. */
    void writeLater(final PregenState snapshot) {
        this.newest.set(snapshot);
        this.executor.execute(this::writeNewest);
    }

    private void writeNewest() {
        final PregenState snapshot = this.newest.getAndSet(null);
        if (snapshot != null) {
            this.write(snapshot);
        }
    }

    /**
     * Queues {@code last} behind the write in progress and stops the writer thread, then waits for it. If that takes
     * longer than the timeout the last state may still be unwritten; it is never written from this thread meanwhile.
     */
    void writeAndClose(final PregenState last) {
        this.newest.set(null);
        this.executor.execute(() -> this.write(last));
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(this.closeTimeoutMillis, TimeUnit.MILLISECONDS)) {
                LOGGER.error("The pregeneration state writer did not finish in {} ms, the last state may not be written", this.closeTimeoutMillis);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(final PregenState state) {
        try {
            this.saving.save(state, this.file);
        } catch (final IOException e) {
            LOGGER.error("Cannot write the pregeneration state {}", this.file, e);
        }
    }
}
