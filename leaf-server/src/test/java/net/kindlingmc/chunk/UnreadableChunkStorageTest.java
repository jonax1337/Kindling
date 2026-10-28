package net.kindlingmc.chunk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.ZipException;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.ReadData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnreadableChunkStorageTest {

    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = 4;
    private static final int CHUNK_DATA_VERSION = 4000; // after the removal of the Level tag
    private static final int FIRST_SECTOR_OFFSET = 8192;
    private static final int STREAM_START = FIRST_SECTOR_OFFSET + 5;

    @TempDir
    Path world;

    @Test
    void anUnreadableChunkIsCopiedAndTheReadStillFailsTheSameWay() throws IOException {
        final Path regionFolder = this.world.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region");
        final Path regionFile = storeChunkAndCorruptItsStream(regionFolder);
        final byte[] stored = Files.readAllBytes(regionFile);

        final IOException failure = assertThrows(IOException.class, () -> readChunk(regionFolder));

        final Path copy = this.world.resolve("kindling-quarantine").resolve("minecraft_overworld").resolve("r.0.0").resolve("c.3.4-1.bin");
        assertTrue(Files.exists(copy), "no copy at " + copy);
        assertArrayEquals(storedChunk(stored), Files.readAllBytes(copy));
        assertInstanceOf(ZipException.class, failure);
        assertArrayEquals(stored, Files.readAllBytes(regionFile));
    }

    @Test
    void aReadableChunkLeavesNoQuarantine() throws IOException {
        final Path regionFolder = this.world.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region");
        storeChunk(regionFolder);

        assertEquals(CHUNK_X, readChunk(regionFolder).getIntOr("xPos", -1));

        assertFalse(Files.exists(this.world.resolve("kindling-quarantine")));
    }

    private static Path storeChunkAndCorruptItsStream(final Path regionFolder) throws IOException {
        final Path regionFile = storeChunk(regionFolder);
        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.WRITE)) {
            final ByteBuffer garbage = ByteBuffer.wrap(new byte[] {0x13, 0x37, 0x13, 0x37, 0x13, 0x37});
            channel.write(garbage, STREAM_START + 4);
        }
        return regionFile;
    }

    private static Path storeChunk(final Path regionFolder) throws IOException {
        try (RegionFileStorage storage = newStorage(regionFolder)) {
            final CompoundTag chunk = new CompoundTag();
            chunk.putInt("DataVersion", CHUNK_DATA_VERSION);
            chunk.putInt("xPos", CHUNK_X);
            chunk.putInt("zPos", CHUNK_Z);
            storage.write(new ChunkPos(CHUNK_X, CHUNK_Z), chunk);
        }
        return regionFolder.resolve("r.0.0.mca");
    }

    private static CompoundTag readChunk(final Path regionFolder) throws IOException {
        try (RegionFileStorage storage = newStorage(regionFolder)) {
            final ReadData read = storage.moonrise$readData(CHUNK_X, CHUNK_Z);
            return read.syncRead() != null ? read.syncRead() : storage.moonrise$finishRead(CHUNK_X, CHUNK_Z, read);
        }
    }

    private static RegionFileStorage newStorage(final Path regionFolder) {
        return new RegionFileStorage(new RegionStorageInfo("world", Level.OVERWORLD, "chunk"), regionFolder, true);
    }

    // the five header bytes and the stream of the chunk, without the padding of its sector
    private static byte[] storedChunk(final byte[] regionFileBytes) {
        final int length = ByteBuffer.wrap(regionFileBytes, FIRST_SECTOR_OFFSET, 4).getInt();
        return Arrays.copyOfRange(regionFileBytes, FIRST_SECTOR_OFFSET, FIRST_SECTOR_OFFSET + 4 + length);
    }
}
