package net.kindlingmc.chunk;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
 * changes what the world writes. Each chunk is copied once per run, and nothing is copied once the folder holds
 * {@value #DEFAULT_CAP_BYTES} bytes.
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

        /** Never returns more than {@code maxBytes}; throws {@link IOException} when it cannot hand the bytes out. */
        byte[] read(long maxBytes) throws IOException;
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

    /** Copies the chunk unless it was copied before or the folder is full; returns the path of the copy. */
    public synchronized Optional<Path> keep(final int chunkX, final int chunkZ, final StoredChunk stored, final Exception cause) {
        if (this.full || !this.seen.add(packed(chunkX, chunkZ))) {
            return Optional.empty();
        }
        try {
            return this.copy(chunkX, chunkZ, stored, cause);
        } catch (final IOException | RuntimeException ex) { // whatever goes wrong here must never replace the read error
            LOGGER.warn("Chunk [{}, {}] in {} could not be read ({}) and no copy could be kept: {}", chunkX, chunkZ, this.owner, cause, ex.toString());
            return Optional.empty();
        }
    }

    private Optional<Path> copy(final int chunkX, final int chunkZ, final StoredChunk stored, final Exception cause) throws IOException {
        final long remaining = this.capBytes - this.usedBytes();
        if (remaining <= 0) {
            this.stopCopying();
            return Optional.empty();
        }
        final byte[] bytes = stored.read(remaining);
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
        LOGGER.warn("The chunk quarantine of {} holds {} bytes and is full; unreadable chunks are no longer copied", this.owner, this.capBytes);
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

    private void writeReport(final Path copy, final int chunkX, final int chunkZ, final Exception cause) {
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
