package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes the small state files of the pregeneration so that a crash leaves the previous file intact. */
final class DurableFile {

    private DurableFile() {
    }

    /** Replaces the file atomically; the bytes are on disk before the move, so a power loss cannot leave a truncated file. */
    static void replace(final Path file, final String text) throws IOException {
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            writeDurably(temporary, text);
            move(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Keeps the unusable file next to the original for diagnosis. */
    static void keepCorrupt(final Path file) throws IOException {
        Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeDurably(final Path file, final String text) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
            final ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    private static void move(final Path temporary, final Path file) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
