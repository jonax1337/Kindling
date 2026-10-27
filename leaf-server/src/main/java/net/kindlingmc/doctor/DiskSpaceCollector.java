package net.kindlingmc.doctor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.bukkit.Bukkit;

/** Reads how much room the file store of the worlds has left. */
final class DiskSpaceCollector {

    private DiskSpaceCollector() {
    }

    static Optional<DiskSpace> collect() {
        try {
            final var store = Files.getFileStore(Bukkit.getWorldContainer().toPath());
            return Optional.of(new DiskSpace(store.getUsableSpace(), store.getTotalSpace()));
        } catch (final IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
