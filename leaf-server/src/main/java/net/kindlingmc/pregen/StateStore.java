package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import net.kindlingmc.pregen.PregenState.Phase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The {@link PregenState} of the server and its file. The file is read when the state is first needed, so nothing can be
 * written before the saved runs of all worlds were read; writing goes through the {@link StateWriter}.
 */
final class StateStore {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final Path file;
    private final StateWriter writer;
    private @Nullable PregenState state;

    StateStore(final Path file) {
        this.file = file;
        this.writer = new StateWriter(file);
    }

    /** Whether the file was read; a store that never was must not be written, it would drop what the file holds. */
    boolean isLoaded() {
        return this.state != null;
    }

    PregenState state() {
        if (this.state == null) {
            this.state = this.load();
        }
        return this.state;
    }

    /** Notes the task with an index stepped back over what a crash could lose. */
    void remember(final PregenTask task, final Phase phase, final int maxParallel) {
        this.state().put(task.world().name(), task.snapshot(phase, task.crashSafeResumeIndex(maxParallel)));
    }

    void saveLater() {
        this.writer.writeLater(this.state().snapshot());
    }

    void saveAndClose() {
        this.writer.writeAndClose(this.state().snapshot());
    }

    /** An unusable file is kept as {@code .corrupt}; what in it is still valid is resumed, the rest starts fresh. */
    private PregenState load() {
        final PregenState loaded = PregenState.load(this.file);
        if (loaded.problems().isEmpty()) {
            return loaded;
        }
        LOGGER.warn("The pregeneration state {} is damaged ({}); the affected worlds start over",
            this.file, String.join("; ", loaded.problems()));
        try {
            PregenState.keepCorrupt(this.file);
        } catch (final IOException e) {
            LOGGER.error("Cannot keep a copy of the damaged pregeneration state {}", this.file, e);
        }
        return loaded;
    }
}
