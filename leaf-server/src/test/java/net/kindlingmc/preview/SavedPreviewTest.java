package net.kindlingmc.preview;

import java.util.EnumMap;
import java.util.List;
import java.util.stream.IntStream;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skin of a saved chunk and the preview built from it: each column keeps its surface and a few blocks below, what is deeper is the default block. */
@Normal
class SavedPreviewTest {
    private static final long SEED = 21L;
    private static final ChunkPos POS = new ChunkPos(4, -9);
    private static final int GROUND_TOP_Y = 63;
    private static final int KEPT_FROM_Y = GROUND_TOP_Y - Skin.KEPT_BELOW_SURFACE;
    private static final int SHAFT_X = 3;
    private static final int SHAFT_Z = 12;
    private static final int WATER_TOP_Y = 62;
    private static final int FLOOR_TOP_Y = 40;

    private final PreviewTestWorld world = new PreviewTestWorld(SEED);
    private final BlockState defaultBlock = this.world.terrain().settings().defaultBlock();

    @Test
    void flatGroundKeepsEightBlocksBelowTheSurfaceAndTheBandStartsThere() {
        ProtoChunk chunk = groundedChunk(GROUND_TOP_Y);
        set(chunk, 2, KEPT_FROM_Y, 2, Blocks.COAL_ORE);
        set(chunk, 2, KEPT_FROM_Y - 1, 2, Blocks.DIAMOND_ORE);
        set(chunk, 2, chunk.getMinY(), 2, Blocks.BEDROCK);

        Band band = skin(chunk);

        assertEquals(KEPT_FROM_Y, band.minY());
        assertEquals(chunk.getMaxY(), band.maxY());
        assertSame(Blocks.COAL_ORE, blockAt(chunk, 2, KEPT_FROM_Y, 2));
        assertSame(this.defaultBlock.getBlock(), blockAt(chunk, 2, KEPT_FROM_Y - 1, 2));
        assertSame(this.defaultBlock.getBlock(), blockAt(chunk, 2, chunk.getMinY(), 2));
    }

    @Test
    void aShaftDoesNotExposeTheDepthsOfItsNeighbours() {
        ProtoChunk chunk = groundedChunk(GROUND_TOP_Y);
        int shaftFloorY = chunk.getMinY() + 20;
        for (int y = shaftFloorY + 1; y <= GROUND_TOP_Y; y++) {
            set(chunk, SHAFT_X, y, SHAFT_Z, Blocks.AIR);
        }
        set(chunk, SHAFT_X, shaftFloorY - 8, SHAFT_Z, Blocks.DIAMOND_ORE);
        set(chunk, SHAFT_X, shaftFloorY - 9, SHAFT_Z, Blocks.EMERALD_ORE);
        set(chunk, SHAFT_X + 1, shaftFloorY, SHAFT_Z, Blocks.GOLD_ORE);

        Band band = skin(chunk);

        assertEquals(shaftFloorY - 8, band.minY());
        assertSame(Blocks.DIAMOND_ORE, blockAt(chunk, SHAFT_X, shaftFloorY - 8, SHAFT_Z));
        assertSame(this.defaultBlock.getBlock(), blockAt(chunk, SHAFT_X, shaftFloorY - 9, SHAFT_Z));
        assertSame(this.defaultBlock.getBlock(), blockAt(chunk, SHAFT_X + 1, shaftFloorY, SHAFT_Z));
        assertSame(Blocks.STONE, blockAt(chunk, SHAFT_X + 1, KEPT_FROM_Y, SHAFT_Z));
    }

    @Test
    void leavesAndLogsAboveGroundDoNotCountAsSurface() {
        ProtoChunk chunk = groundedChunk(GROUND_TOP_Y);
        set(chunk, 5, GROUND_TOP_Y + 1, 5, Blocks.OAK_LOG);
        set(chunk, 5, GROUND_TOP_Y + 2, 5, Blocks.OAK_LEAVES);
        set(chunk, 7, GROUND_TOP_Y + 1, 7, Blocks.OAK_LEAVES);

        Band band = skin(chunk);

        assertEquals(KEPT_FROM_Y, band.minY());
        assertSame(Blocks.OAK_LOG, blockAt(chunk, 5, GROUND_TOP_Y + 1, 5));
        assertSame(Blocks.OAK_LEAVES, blockAt(chunk, 5, GROUND_TOP_Y + 2, 5));
    }

    @Test
    void underWaterTheSeaFloorIsTheSurface() {
        ProtoChunk chunk = groundedChunk(FLOOR_TOP_Y);
        for (int y = FLOOR_TOP_Y + 1; y <= WATER_TOP_Y; y++) {
            fillLayer(chunk, y, Blocks.WATER);
        }

        Band band = skin(chunk);

        assertEquals(FLOOR_TOP_Y - Skin.KEPT_BELOW_SURFACE, band.minY());
        assertSame(Blocks.WATER, blockAt(chunk, 0, WATER_TOP_Y, 0));
    }

    @Test
    void theBandNeverStartsBelowTheWorld() {
        ProtoChunk chunk = this.world.emptyChunk(POS);
        assertEquals(chunk.getMinY(), skin(chunk).minY());
    }

    @Test
    void thePacketCarriesTheChunkAndIsSmallerThanTheUnskinnedOne() {
        PreviewPacket saved = SavedPreview.of(savedData(chunkWithDeepOres()), this.world.terrain());
        ProtoChunk deepOres = chunkWithDeepOres();
        PreviewPacket unskinned = PreviewPacket.of(deepOres, SkyLight.fromAbove(deepOres.getSections()));
        assertEquals(POS.x(), saved.packet().x());
        assertEquals(POS.z(), saved.packet().z());
        assertTrue(saved.rawBytes() < unskinned.rawBytes(), saved.rawBytes() + " < " + unskinned.rawBytes());
    }

    private Band skin(ProtoChunk chunk) {
        return Skin.skinBelowSurfaces(chunk, this.defaultBlock, this.world.terrain().containerFactory());
    }

    /** Ground with an ore in every section below the kept part, so that those sections are not single-value and the skin saves bytes. */
    private ProtoChunk chunkWithDeepOres() {
        ProtoChunk chunk = groundedChunk(GROUND_TOP_Y);
        for (int y = chunk.getMinY() + 4; y < KEPT_FROM_Y; y += 16) {
            set(chunk, 0, y, 0, Blocks.IRON_ORE);
        }
        return chunk;
    }

    private ProtoChunk groundedChunk(int topY) {
        ProtoChunk chunk = this.world.emptyChunk(POS);
        for (int y = chunk.getMinY(); y <= topY; y++) {
            fillLayer(chunk, y, Blocks.STONE);
        }
        return chunk;
    }

    /** The data a region file would hold for the chunk, with the light not stored, parsed into its in-memory form. */
    private SerializableChunkData savedData(ProtoChunk chunk) {
        List<SerializableChunkData.SectionData> sections = IntStream.range(0, chunk.getSectionsCount())
            .mapToObj(index -> new SerializableChunkData.SectionData(chunk.getSectionYFromSectionIndex(index), chunk.getSections()[index], null, null))
            .toList();
        return new SerializableChunkData(this.world.terrain().containerFactory(), POS, chunk.getMinSectionY(), 0L, 0L, ChunkStatus.FULL, null, null,
            UpgradeData.EMPTY, new EnumMap<Heightmap.Types, long[]>(Heightmap.Types.class), new ChunkAccess.PackedTicks(List.of(), List.of()),
            new ShortList[0], false, sections, List.of(), List.of(), new CompoundTag(), null);
    }

    private static void fillLayer(ProtoChunk chunk, int y, Block block) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                set(chunk, x, y, z, block);
            }
        }
    }

    private static void set(ProtoChunk chunk, int x, int y, int z, Block block) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, block.defaultBlockState(), false);
    }

    private static Block blockAt(ProtoChunk chunk, int x, int y, int z) {
        return PreviewTestWorld.blockAt(chunk, x, y, z).getBlock();
    }
}
