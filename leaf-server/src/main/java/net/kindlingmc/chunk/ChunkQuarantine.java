package net.kindlingmc.chunk;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * Keeps a copy of the stored bytes of a chunk that cannot be read, because vanilla and Paper log the error and then
 * generate the chunk anew, and the next save overwrites what was stored. The copy is {@code <world>/kindling-quarantine/
 * <dimension>/r.<x>.<z>/c.<x>.<z>-<n>.bin} next to a {@code .txt} with the exception. It is never read back and never
 * changes what the world writes. Each chunk is copied once per run (a copy that failed with an {@link IOException} is
 * tried again the next time the chunk is read), a chunk larger than what is left is skipped while smaller ones are still
 * copied, and nothing is copied once the folder holds {@value #DEFAULT_CAP_BYTES} bytes.
 */
public final class ChunkQuarantine {

    public static final long DEFAULT_CAP_BYTES = 256L * 1024 * 1024;

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final String FOLDER_NAME = "kindling-quarantine";
    private static final String DIMENSIONS_FOLDER = "dimensions";
    private static final int REGION_SHIFT = 5;
    private static final Map<Path, ChunkQuarantine> BY_ROOT = new ConcurrentHashMap<>();

    /** The stored bytes of one chunk, read only when the chunk is actually copied. */
    @FunctionalInterface
    public interface StoredChunk {

        /**
         * Never returns more than {@code maxBytes} and returns no bytes when nothing is stored for the chunk; throws
         * {@link OverCapException} when the stored bytes are more than {@code maxBytes} and {@link IOException} when it
         * cannot hand the bytes out.
         */
        byte[] read(long maxBytes) throws IOException;
    }

    /** The stored bytes of this chunk do not fit into what is left of the folder's cap; smaller chunks may still fit. */
    public static final class OverCapException extends IOException {

        public OverCapException(final String message) {
            super(message);
        }
    }

    private final Path root;
    private final String owner;
    private final long capBytes;
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private long usedBytes = -1;
    private boolean full;

    ChunkQuarantine(final Path root, final String owner, final long capBytes) {
        this.root = root;
        this.owner = owner;
        this.capBytes = capBytes;
    }

    /** The quarantine of the dimension whose region files live in {@code regionFolder}. */
    public static ChunkQuarantine forRegionFolder(final String level, final String dimension, final Path regionFolder) {
        final Path root = worldFolder(regionFolder).resolve(FOLDER_NAME).resolve(dimension.replace(':', '_').replace('/', '_'));
        return BY_ROOT.computeIfAbsent(root, key -> new ChunkQuarantine(key, level + "/" + dimension, DEFAULT_CAP_BYTES));
    }

    // <world>/dimensions/<namespace>/<path>/region; a layout without "dimensions" keeps the copies beside the region folder
    static Path worldFolder(final Path regionFolder) {
        for (Path folder = regionFolder.getParent(); folder != null; folder = folder.getParent()) {
            final Path name = folder.getFileName();
            if (name != null && DIMENSIONS_FOLDER.equals(name.toString()) && folder.getParent() != null) {
                return folder.getParent();
            }
        }
        return regionFolder.getParent();
    }

    /**
     * Copies the chunk unless it was copied before or the folder is full; returns the path of the copy. The stored bytes
     * are read without the lock of this quarantine, because reading them may need the lock of a region file, and a
     * region file that reports a chunk holds that lock while it calls this method.
     */
    public Optional<Path> keep(final int chunkX, final int chunkZ, final StoredChunk stored, final Throwable cause) {
        if (isClosedStorage(cause)) {
            return Optional.empty();
        }
        final long chunk = packed(chunkX, chunkZ);
        final long room = this.claim(chunk);
        if (room <= 0) {
            return Optional.empty();
        }
        try {
            return this.copy(chunkX, chunkZ, stored.read(room), cause);
        } catch (final OverCapException ex) {
            LOGGER.warn("Chunk [{}, {}] in {} could not be read ({}) and is not copied: {}", chunkX, chunkZ, this.owner, cause, ex.getMessage());
        } catch (final IOException ex) {
            this.release(chunk);
            LOGGER.warn("Chunk [{}, {}] in {} could not be read ({}) and no copy could be kept: {}", chunkX, chunkZ, this.owner, cause, ex.toString());
        } catch (final RuntimeException ex) { // whatever goes wrong here must never replace the read error
            LOGGER.warn("Chunk [{}, {}] in {} could not be read ({}) and no copy could be kept: {}", chunkX, chunkZ, this.owner, cause, ex.toString());
        }
        return Optional.empty();
    }

    // a read that fails because the region file is being closed says nothing about the chunk, so it must not use up its one copy
    private static boolean isClosedStorage(final Throwable cause) {
        for (Throwable link = cause; link != null; link = link.getCause()) {
            if (link instanceof ClosedChannelException) {
                return true;
            }
        }
        return false;
    }

    // the bytes that still fit for a chunk that has not been copied yet, or 0 when nothing is to be copied
    private synchronized long claim(final long chunk) {
        if (this.full || this.seen.contains(chunk)) {
            return 0;
        }
        try {
            final long remaining = this.capBytes - this.usedBytes();
            if (remaining <= 0) {
                this.stopCopying();
                return 0;
            }
            this.seen.add(chunk);
            return remaining;
        } catch (final IOException | RuntimeException ex) {
            LOGGER.warn("The size of the chunk quarantine of {} could not be read, so no chunk is copied: {}", this.owner, ex.toString());
            return 0;
        }
    }

    // a copy that failed with an IOException may succeed the next time the chunk is read
    private synchronized void release(final long chunk) {
        this.seen.remove(chunk);
    }

    // no bytes means that nothing is stored for the chunk
    private synchronized Optional<Path> copy(final int chunkX, final int chunkZ, final byte[] bytes, final Throwable cause) throws IOException {
        if (bytes.length == 0) {
            return Optional.empty();
        }
        final long remaining = this.capBytes - this.usedBytes();
        if (bytes.length > remaining) {
            throw new OverCapException("The " + bytes.length + " stored bytes do not fit into the " + remaining + " bytes left");
        }
        final Path regionFolder = this.root.resolve("r." + (chunkX >> REGION_SHIFT) + "." + (chunkZ >> REGION_SHIFT));
        Files.createDirectories(regionFolder);
        final Path copy = writeNumbered(regionFolder, "c." + chunkX + "." + chunkZ, bytes);
        this.usedBytes += bytes.length;
        this.writeReport(copy, chunkX, chunkZ, cause);
        LOGGER.warn("Chunk [{}, {}] in {} could not be read ({}); kept a copy of its stored bytes at {}", chunkX, chunkZ, this.owner, cause, copy);
        return Optional.of(copy);
    }

    private void stopCopying() {
        this.full = true;
        LOGGER.warn("The chunk quarantine of {} has no room left within its {} bytes; unreadable chunks are no longer copied", this.owner, this.capBytes);
    }

    private long usedBytes() throws IOException {
        if (this.usedBytes < 0) {
            this.usedBytes = sizeOfFilesIn(this.root);
        }
        return this.usedBytes;
    }

    // the first free number, so copies of earlier runs stay
    private static Path writeNumbered(final Path folder, final String name, final byte[] bytes) throws IOException {
        for (int number = 1; ; number++) {
            final Path copy = folder.resolve(name + "-" + number + ".bin");
            try {
                Files.write(copy, bytes, StandardOpenOption.CREATE_NEW);
                return copy;
            } catch (final FileAlreadyExistsException taken) {
                // try the next number
            }
        }
    }

    private void writeReport(final Path copy, final int chunkX, final int chunkZ, final Throwable cause) {
        final String fileName = copy.getFileName().toString();
        final Path report = copy.resolveSibling(fileName.substring(0, fileName.length() - ".bin".length()) + ".txt");
        final StringWriter text = new StringWriter();
        text.append("world/dimension: ").append(this.owner).append('\n');
        text.append("chunk: ").append(String.valueOf(chunkX)).append(' ').append(String.valueOf(chunkZ)).append('\n');
        text.append("region: ").append(copy.getParent().getFileName().toString()).append('\n');
        text.append("time: ").append(Instant.now().toString()).append('\n');
        cause.printStackTrace(new PrintWriter(text));
        try {
            final byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(report, bytes);
            this.usedBytes += bytes.length;
        } catch (final IOException ex) {
            LOGGER.warn("The exception report next to {} could not be written: {}", copy, ex.toString());
        }
    }

    private static long sizeOfFilesIn(final Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        try (final Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).mapToLong(ChunkQuarantine::sizeOf).sum();
        }
    }

    private static long sizeOf(final Path file) {
        try {
            return Files.size(file);
        } catch (final IOException ex) {
            return 0;
        }
    }

    private static long packed(final int chunkX, final int chunkZ) {
        return (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    }
}
