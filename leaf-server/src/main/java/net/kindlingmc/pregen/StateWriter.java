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
 * on the calling thread, for the shutdown.
 */
final class StateWriter {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long CLOSE_TIMEOUT_SECONDS = 10L;

    private final Path file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "Kindling pregeneration state writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<PregenState> newest = new AtomicReference<>();

    StateWriter(final Path file) {
        this.file = file;
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

    /** Waits for the write in progress, then writes {@code last} and stops the writer thread. */
    void writeAndClose(final PregenState last) {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("The pregeneration state writer did not finish in {} s", CLOSE_TIMEOUT_SECONDS);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.newest.set(null);
        this.write(last);
    }

    private void write(final PregenState state) {
        try {
            state.save(this.file);
        } catch (final IOException e) {
            LOGGER.error("Cannot write the pregeneration state {}", this.file, e);
        }
    }
}
