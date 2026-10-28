package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;

/** The {@link FrontierState} of the server and its file; writing goes through the {@link StateWriter}. */
final class FrontierStore {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final StateWriter<FrontierState> writer;
    private final FrontierState state;

    /** Reads the file. An unusable file is kept as {@code .corrupt}; what in it is still valid is used, the rest starts fresh. */
    FrontierStore(final Path file) {
        this.writer = new StateWriter<>("frontier pregeneration state", file, FrontierState::save);
        this.state = load(file);
    }

    FrontierState state() {
        return this.state;
    }

    void saveLater() {
        this.writer.writeLater(this.state.snapshot());
    }

    void saveAndClose() {
        this.writer.writeAndClose(this.state.snapshot());
    }

    private static FrontierState load(final Path file) {
        final FrontierState loaded = FrontierState.load(file);
        if (loaded.problems().isEmpty()) {
            return loaded;
        }
        LOGGER.warn("The frontier pregeneration state {} is damaged ({}); the affected worlds start over",
            file, String.join("; ", loaded.problems()));
        try {
            DurableFile.keepCorrupt(file);
        } catch (final IOException e) {
            LOGGER.error("Cannot keep a copy of the damaged frontier pregeneration state {}", file, e);
        }
        return loaded;
    }
}
