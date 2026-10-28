package net.kindlingmc.chunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkQuarantineTest {

    private static final Exception CAUSE = new IOException("Not in GZIP format");
    private static final byte[] BYTES = {1, 2, 3, 4, 5};
    private static final long ROOMY = 1 << 20;

    @TempDir
    Path root;

    @Test
    void theStoredBytesAreCopiedUnderTheRegionOfTheChunk() throws IOException {
        final Optional<Path> copy = quarantine(ROOMY).keep(33, -1, maxBytes -> BYTES, CAUSE);

        assertEquals(this.root.resolve("r.1.-1").resolve("c.33.-1-1.bin"), copy.orElseThrow());
        assertArrayEquals(BYTES, Files.readAllBytes(copy.get()));
    }

    @Test
    void theExceptionIsWrittenNextToTheCopy() throws IOException {
        final Path copy = quarantine(ROOMY).keep(2, 3, maxBytes -> BYTES, CAUSE).orElseThrow();

        final String report = Files.readString(copy.resolveSibling("c.2.3-1.txt"));

        assertTrue(report.contains("chunk: 2 3"), report);
        assertTrue(report.contains("region: r.0.0"), report);
        assertTrue(report.contains("Not in GZIP format"), report);
    }

    @Test
    void aChunkIsCopiedOnlyOncePerRun() {
        final ChunkQuarantine quarantine = quarantine(ROOMY);
        final AtomicInteger reads = new AtomicInteger();

        final Optional<Path> first = quarantine.keep(4, 4, maxBytes -> read(reads), CAUSE);
        final Optional<Path> second = quarantine.keep(4, 4, maxBytes -> read(reads), CAUSE);

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        assertEquals(1, reads.get());
    }

    @Test
    void otherChunksOfTheSameRegionAreCopiedSeparately() {
        final ChunkQuarantine quarantine = quarantine(ROOMY);

        assertTrue(quarantine.keep(4, 4, maxBytes -> BYTES, CAUSE).isPresent());
        assertTrue(quarantine.keep(4, 5, maxBytes -> BYTES, CAUSE).isPresent());
        assertTrue(quarantine.keep(5, 4, maxBytes -> BYTES, CAUSE).isPresent());
    }

    @Test
    void aNewRunNumbersItsCopyAfterTheOnesOfEarlierRuns() throws IOException {
        quarantine(ROOMY).keep(7, 7, maxBytes -> BYTES, CAUSE);

        final Path again = quarantine(ROOMY).keep(7, 7, maxBytes -> BYTES, CAUSE).orElseThrow();

        assertEquals("c.7.7-2.bin", again.getFileName().toString());
        assertTrue(Files.exists(again.resolveSibling("c.7.7-1.bin")));
    }

    @Test
    void nothingIsCopiedOnceTheFolderIsFull() {
        final ChunkQuarantine quarantine = quarantine(40);
        final AtomicInteger reads = new AtomicInteger();

        assertTrue(quarantine.keep(0, 0, maxBytes -> new byte[40], CAUSE).isPresent());
        assertTrue(quarantine.keep(1, 0, maxBytes -> read(reads), CAUSE).isEmpty());
        assertTrue(quarantine.keep(2, 0, maxBytes -> read(reads), CAUSE).isEmpty());

        assertEquals(0, reads.get());
    }

    @Test
    void whatEarlierRunsKeptCountsAgainstTheCap() {
        quarantine(1 << 20).keep(0, 0, maxBytes -> new byte[100], CAUSE);

        assertTrue(quarantine(50).keep(1, 0, maxBytes -> BYTES, CAUSE).isEmpty());
    }

    @Test
    void theReaderIsToldHowManyBytesStillFit() {
        final ChunkQuarantine quarantine = quarantine(1000);
        final long[] offered = new long[1];

        quarantine.keep(0, 0, maxBytes -> {
            offered[0] = maxBytes;
            return BYTES;
        }, CAUSE);

        assertEquals(1000, offered[0]);
    }

    @Test
    void aChunkThatCannotBeReadAgainLeavesNothingAndNeverThrows() {
        final ChunkQuarantine quarantine = quarantine(ROOMY);

        final Optional<Path> copy = quarantine.keep(9, 9, maxBytes -> {
            throw new IOException("region file closed");
        }, CAUSE);

        assertTrue(copy.isEmpty());
        assertFalse(Files.exists(this.root.resolve("r.0.0")));
    }

    @Test
    void aFailingReaderNeverThrowsEvenWithAnUncheckedException() {
        final Optional<Path> copy = quarantine(ROOMY).keep(9, 9, maxBytes -> {
            throw new IllegalStateException("boom");
        }, CAUSE);

        assertTrue(copy.isEmpty());
    }

    @Test
    void theWorldFolderIsTheOneAboveTheDimensionsFolder() {
        final Path region = Path.of("world", "dimensions", "minecraft", "overworld", "region");

        assertEquals(Path.of("world"), ChunkQuarantine.worldFolder(region));
    }

    @Test
    void aDimensionWithASlashInItsPathStillResolvesToTheWorldFolder() {
        final Path region = Path.of("world", "dimensions", "pack", "deep", "dim", "region");

        assertEquals(Path.of("world"), ChunkQuarantine.worldFolder(region));
    }

    @Test
    void withoutADimensionsFolderTheRegionFoldersParentIsUsed() {
        assertEquals(Path.of("world"), ChunkQuarantine.worldFolder(Path.of("world", "region")));
    }

    private ChunkQuarantine quarantine(final long capBytes) {
        return new ChunkQuarantine(this.root, "world/minecraft:overworld", capBytes);
    }

    private static byte[] read(final AtomicInteger reads) {
        reads.incrementAndGet();
        return BYTES;
    }
}
