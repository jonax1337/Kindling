package net.kindlingmc.chunk;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region file hands out no stream for these chunks, so Moonrise generates them anew; the sectors must be kept. */
class UnreadableRegionFileTest {

    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = 4;
    private static final int CHUNK_DATA_VERSION = 4000; // after the removal of the Level tag
    private static final int SECTOR_BYTES = 4096;
    private static final int FIRST_SECTOR_OFFSET = 2 * SECTOR_BYTES;
    private static final int OFFSET_ENTRY = 4 * (CHUNK_X + CHUNK_Z * 32);

    @TempDir
    Path world;

    private Path regionFolder;
    private Path regionFile;

    @BeforeEach
    void storeChunk() throws IOException {
        this.regionFolder = this.world.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region");
        try (RegionFileStorage storage = newStorage(this.regionFolder)) {
            final CompoundTag chunk = new CompoundTag();
            chunk.putInt("DataVersion", CHUNK_DATA_VERSION);
            chunk.putInt("xPos", CHUNK_X);
            chunk.putInt("zPos", CHUNK_Z);
            storage.write(new ChunkPos(CHUNK_X, CHUNK_Z), chunk);
        }
        this.regionFile = this.regionFolder.resolve("r.0.0.mca");
    }

    @Test
    void aTruncatedStreamIsCopiedAsStoredAndNoStreamIsHandedOut() throws IOException {
        this.overwrite(FIRST_SECTOR_OFFSET, ByteBuffer.allocate(4).putInt(SECTOR_BYTES + 1).array());
        final byte[] sector = this.sector();

        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.NO_DATA, read.result());
        assertArrayEquals(sector, this.copy());
    }

    @Test
    void aMissingStreamIsCopiedAsStored() throws IOException {
        this.overwrite(FIRST_SECTOR_OFFSET, new byte[] {0, 0, 0, 0});
        final byte[] sector = this.sector();

        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.NO_DATA, read.result());
        assertArrayEquals(sector, this.copy());
    }

    @Test
    void aNegativeLengthIsCopiedAsStored() throws IOException {
        this.overwrite(FIRST_SECTOR_OFFSET, ByteBuffer.allocate(4).putInt(-5).array());
        final byte[] sector = this.sector();

        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.NO_DATA, read.result());
        assertArrayEquals(sector, this.copy());
    }

    @Test
    void anUnknownCompressionIsCopiedAsStored() throws IOException {
        this.overwrite(FIRST_SECTOR_OFFSET + 4, new byte[] {99});
        final byte[] sector = this.sector();

        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.NO_DATA, read.result());
        assertArrayEquals(sector, this.copy());
    }

    @Test
    void aTruncatedHeaderKeepsTheBytesTheFileStillHolds() throws IOException {
        try (FileChannel channel = FileChannel.open(this.regionFile, StandardOpenOption.WRITE)) {
            channel.truncate(FIRST_SECTOR_OFFSET + 3);
        }
        final byte[] stored = Arrays.copyOfRange(Files.readAllBytes(this.regionFile), FIRST_SECTOR_OFFSET, FIRST_SECTOR_OFFSET + 3);

        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.NO_DATA, read.result());
        assertArrayEquals(stored, this.copy());
    }

    @Test
    void theCopyIsTakenFromWhereTheHeaderPointedBeforeItWasRecalculated() throws IOException {
        this.overwrite(FIRST_SECTOR_OFFSET, new byte[] {0, 0, 0, 0});
        final byte[] sector = this.sector();

        this.readChunk();

        assertEquals(0, this.headerOffsetOfTheChunk(), "the recalculated header no longer lists the chunk");
        assertArrayEquals(sector, this.copy());
    }

    @Test
    void aChunkTheRecalculationRecoversIsNotCopied() throws IOException {
        this.overwrite(OFFSET_ENTRY, ByteBuffer.allocate(4).putInt(3 << 8 | 1).array()); // an empty sector behind the chunk
        this.overwrite(FIRST_SECTOR_OFFSET + SECTOR_BYTES, new byte[SECTOR_BYTES]);

        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.HAS_DATA, read.result());
        assertNotNull(read.input());
        assertFalse(Files.exists(this.world.resolve("kindling-quarantine")));
    }

    @Test
    void aChunkIsCopiedOnlyOnceNoMatterHowOftenItIsRead() throws IOException {
        this.overwrite(FIRST_SECTOR_OFFSET, new byte[] {0, 0, 0, 0});

        this.readChunk();
        this.readChunk();

        assertTrue(Files.exists(this.copyPath()));
        assertFalse(Files.exists(this.copyPath().resolveSibling("c.3.4-2.bin")));
    }

    @Test
    void aReadableChunkIsHandedOutWithoutACopy() throws IOException {
        final ReadData read = this.readChunk();

        assertEquals(ReadData.ReadResult.HAS_DATA, read.result());
        assertFalse(Files.exists(this.world.resolve("kindling-quarantine")));
    }

    private ReadData readChunk() throws IOException {
        try (RegionFileStorage storage = newStorage(this.regionFolder)) {
            return storage.moonrise$readData(CHUNK_X, CHUNK_Z);
        }
    }

    private void overwrite(final long position, final byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(this.regionFile, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes), position);
        }
    }

    private byte[] sector() throws IOException {
        return Arrays.copyOfRange(Files.readAllBytes(this.regionFile), FIRST_SECTOR_OFFSET, FIRST_SECTOR_OFFSET + SECTOR_BYTES);
    }

    private int headerOffsetOfTheChunk() throws IOException {
        return ByteBuffer.wrap(Files.readAllBytes(this.regionFile), OFFSET_ENTRY, 4).getInt();
    }

    private byte[] copy() throws IOException {
        return Files.readAllBytes(this.copyPath());
    }

    private Path copyPath() {
        return this.world.resolve("kindling-quarantine").resolve("minecraft_overworld").resolve("r.0.0").resolve("c.3.4-1.bin");
    }

    private static RegionFileStorage newStorage(final Path regionFolder) {
        return new RegionFileStorage(new RegionStorageInfo("world", Level.OVERWORLD, "chunk"), regionFolder, true);
    }
}
