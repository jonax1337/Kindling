package net.kindlingmc.preview;

import net.kindlingmc.preview.PreviewTestWorld.Dimension;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.preview.PreviewTestWorld.blockAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Previews of the Nether and the End: the whole column at the world's height instead of a band over rock, so caves
 * and the void under islands stay open; block light instead of sky light where the world has no sky; and vanilla's
 * features of both worlds.
 */
@Normal
class PreviewDimensionsTest {
    private static final long SEED = 7L;
    private static final int VANILLA_WORLD_HEIGHT = 256;
    private static final int COLUMNS = 256;
    private static final int NETHER_ROOF_Y = 127;
    private static final int NETHER_LAVA_SEA_Y = 31;
    /** 640 blocks from the centre: inside the End's empty ring, where the terrain has no islands. */
    private static final ChunkPos END_VOID = new ChunkPos(40, 0);
    private static final ChunkPos END_MAIN_ISLAND = new ChunkPos(0, 0);
    private static final ChunkPos NETHER_CHUNK = new ChunkPos(3, -2);
    private static final int LIGHT_SECTIONS_BELOW_WORLD = 1;
    private static final int DECORATED_CHUNKS = 4;
    private static final int SMALL_ISLAND_CHUNKS = 60;
    private static final Set<String> NEEDS_THE_SERVER_LEVEL = Set.of("small_basalt_columns", "large_basalt_columns");

    @Test
    void onlyTheOverworldGetsASurfaceBand() {
        assertTrue(new PreviewTestWorld(SEED).terrain().shape().surfaceBand());
        PreviewShape nether = new PreviewTestWorld(SEED, Dimension.NETHER).terrain().shape();
        assertFalse(nether.surfaceBand());
        assertFalse(nether.skyLight());
        PreviewShape end = new PreviewTestWorld(SEED, Dimension.END).terrain().shape();
        assertFalse(end.surfaceBand());
        assertTrue(end.skyLight());
    }

    @Test
    void noiseReachingOutsideTheWorldGetsNoPreviews() {
        PreviewTestWorld overworld = new PreviewTestWorld(SEED);
        DimensionType type = overworld.registries().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD).value();
        LevelHeightAccessor lowerWorld = LevelHeightAccessor.create(0, VANILLA_WORLD_HEIGHT);
        assertTrue(PreviewShape.of(type, overworld.terrain().noiseSettings(), lowerWorld).isEmpty());
    }

    @Test
    void theNetherPreviewIsTheWholeColumnAtTheWorldsHeight() {
        ProtoChunk preview = new PreviewTestWorld(SEED, Dimension.NETHER).previews().generate(NETHER_CHUNK);

        assertEquals(VANILLA_WORLD_HEIGHT, preview.getHeight());
        assertTrue(countOf(preview, Blocks.BEDROCK, NETHER_ROOF_Y) > 0, "bedrock roof");
        assertTrue(countOf(preview, Blocks.BEDROCK, preview.getMinY()) > 0, "bedrock floor");
        assertTrue(airBetween(preview, NETHER_LAVA_SEA_Y + 1, NETHER_ROOF_Y - 1) > 0, "open caves between the lava sea and the roof");
        assertEquals(COLUMNS * (preview.getMaxY() - NETHER_ROOF_Y), airBetween(preview, NETHER_ROOF_Y + 1, preview.getMaxY()), "air above the roof");
    }

    @Test
    void theNetherIsSentWithBlockLightAndNoSkyLight() {
        PreviewTestWorld nether = new PreviewTestWorld(SEED, Dimension.NETHER);
        ProtoChunk chunk = nether.emptyChunk(NETHER_CHUNK);
        set(chunk, 8, NETHER_LAVA_SEA_Y, 8, Blocks.LAVA.defaultBlockState());

        SkyLight.Light light = nether.terrain().shape().estimatedLight(chunk.getSections());

        assertTrue(light.sky().present().isEmpty() && light.sky().empty().isEmpty(), "no sky light at all");
        assertEquals(15, blockLightAt(light, chunk, 8, NETHER_LAVA_SEA_Y, 8));
        assertEquals(14, blockLightAt(light, chunk, 8, NETHER_LAVA_SEA_Y + 1, 8));
        assertEquals(12, blockLightAt(light, chunk, 11, NETHER_LAVA_SEA_Y, 8));
    }

    @Test
    void theVoidOfTheEndStaysEmptyAndItsIslandHasNoRockBelow() {
        PreviewTestWorld end = new PreviewTestWorld(SEED, Dimension.END);

        ProtoChunk emptyRing = end.previews().generate(END_VOID);
        ProtoChunk island = end.previews().generate(END_MAIN_ISLAND);

        assertEquals(COLUMNS * emptyRing.getHeight(), airBetween(emptyRing, emptyRing.getMinY(), emptyRing.getMaxY()), "the empty ring");
        assertTrue(countOf(island, Blocks.END_STONE, 50) > 0, "the main island");
        assertEquals(COLUMNS, countOf(island, Blocks.AIR, island.getMinY()), "nothing under the main island");
        assertFalse(end.terrain().shape().estimatedLight(island.getSections()).sky().present().isEmpty(), "sky light");
    }

    @Test
    void theNetherGetsItsFungiAndTheEndItsChorus() {
        assertTrue(decoratedBlocks(Dimension.NETHER, Biomes.CRIMSON_FOREST, Blocks.CRIMSON_STEM, Set.of()) > 0, "crimson fungi");
        assertTrue(decoratedBlocks(Dimension.END, Biomes.END_HIGHLANDS, Blocks.CHORUS_PLANT, Set.of()) > 0, "chorus plants");
    }

    /** The small islands are a feature (one chunk in 14 has one) over terrain that is all void. */
    @Test
    void theEndGetsItsSmallIslands() {
        assertTrue(decoratedBlocks(Dimension.END, Biomes.SMALL_END_ISLANDS, Blocks.END_STONE, Set.of(), SMALL_ISLAND_CHUNKS) > 0, "small islands");
    }

    /** The basalt columns ask the server level for the generator; the rest of the basalt deltas' features run. */
    @Test
    void onlyTheBasaltColumnsFailInTheBasaltDeltas() {
        assertTrue(decoratedBlocks(Dimension.NETHER, Biomes.BASALT_DELTAS, Blocks.MAGMA_BLOCK, NEEDS_THE_SERVER_LEVEL) > 0, "deltas of lava and magma");
    }

    /**
     * How often the block stands in decorated previews of a few chunks lying in the biome; fails when other features
     * than the expected ones failed on any of them.
     */
    private static int decoratedBlocks(Dimension dimension, ResourceKey<Biome> biome, Block block, Set<String> expectedFailures) {
        return decoratedBlocks(dimension, biome, block, expectedFailures, DECORATED_CHUNKS);
    }

    private static int decoratedBlocks(Dimension dimension, ResourceKey<Biome> biome, Block block, Set<String> expectedFailures, int chunks) {
        PreviewTestWorld world = new PreviewTestWorld(SEED, dimension);
        PreviewVegetation vegetation = world.vegetation();
        int count = 0;
        for (ChunkPos pos : world.someChunksOf(biome, chunks)) {
            PreviewGenerator.Generated preview = world.previews().generateWithBiomes(pos);
            vegetation.decorate(preview);
            for (int y = preview.chunk().getMinY(); y <= preview.chunk().getMaxY(); y++) {
                count += countOf(preview.chunk(), block, y);
            }
        }
        Set<String> failed = new TreeSet<>();
        for (PlacedFeature feature : vegetation.failedFeatures()) {
            failed.add(world.registries().lookupOrThrow(Registries.PLACED_FEATURE).getResourceKey(feature)
                .map(key -> key.identifier().getPath()).orElseGet(feature::toString));
        }
        assertEquals(expectedFailures, failed, "features that failed in " + dimension);
        return count;
    }

    private static int countOf(ProtoChunk chunk, Block block, int y) {
        int count = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (blockAt(chunk, x, y, z).is(block)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Air blocks from minY to maxY, both included. */
    private static int airBetween(ProtoChunk chunk, int minY, int maxY) {
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            count += countOf(chunk, Blocks.AIR, y);
        }
        return count;
    }

    private static void set(ProtoChunk chunk, int x, int y, int z, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
    }

    /** The block light the preview is sent with at a block of its chunk (x and z inside the chunk). */
    private static int blockLightAt(SkyLight.Light light, ProtoChunk chunk, int x, int y, int z) {
        SkyLight.Layers block = light.block();
        int lightSection = ((y - chunk.getMinY()) >> 4) + LIGHT_SECTIONS_BELOW_WORLD;
        if (!block.present().get(lightSection)) {
            return 0;
        }
        byte[] packed = block.arrays().get(block.present().get(0, lightSection).cardinality());
        int cell = (y & 15) << 8 | z << 4 | x;
        return packed[cell >> 1] >> ((cell & 1) * 4) & 15;
    }
}
