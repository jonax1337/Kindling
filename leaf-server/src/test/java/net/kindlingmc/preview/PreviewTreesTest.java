package net.kindlingmc.preview;

import io.papermc.paper.configuration.WorldConfiguration;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Approximate trees on previews of a real overworld generator: the same chunk always gets the same trees, forests get
 * trees and deserts and oceans none, and every trunk stands on a dirt-like block. Prints the derived style table of
 * common biomes and the cost of decorating.
 */
@Normal
class PreviewTreesTest {
    private static final long SEED = 11L;
    private static final int SEARCH_STEP = 4;
    private static final int SEARCH_RADIUS = 256;
    private static final int SURFACE_QUART_Y = 16;
    private static final int PROBES_PER_AXIS = 3;
    private static final int COST_CHUNKS = 50;
    private static final int NO_LOG = Integer.MIN_VALUE;
    private static final int FOREST_CHUNKS_FOR_SPREAD = 40;
    /** Uniform trunks over about twelve columns give about 8 percent per line; piling them on the edges gives 19 percent and more. */
    private static final double MAX_SHARE_OF_ONE_LINE = 0.14;
    /** The deepest water any vegetal tree placement of the overworld allows (mangroves). */
    private static final int MAX_FILTERED_WATER_DEPTH = 5;

    private final RegistryAccess registries = RegistryHelper.registryAccess();
    /** Terrain keeps its level only weakly, so this field keeps the stand-in alive for the whole test. */
    private final Level level = standInLevel();
    private final Terrain terrain = terrain(SEED);
    private final PreviewGenerator previews = new PreviewGenerator(this.terrain);
    private final PreviewTrees trees = PreviewTrees.of(this.registries, SEED);

    @Test
    void theSameChunkGetsTheSameTrees() {
        for (ChunkPos pos : chunksOf(Biomes.FOREST, 2)) {
            ProtoChunk first = decorated(pos);
            ProtoChunk second = decorated(pos);
            assertEquals(0, differingBlocks(first, second), "chunk " + pos);
        }
    }

    @Test
    void forestsGetTrees() {
        for (ChunkPos pos : chunksOf(Biomes.FOREST, 3)) {
            ProtoChunk chunk = decorated(pos);
            assertTrue(count(chunk, BlockTags.LOGS) > 0, "logs in forest chunk " + pos);
            assertTrue(count(chunk, BlockTags.LEAVES) > 0, "leaves in forest chunk " + pos);
        }
    }

    @Test
    void desertsAndOceansGetNoTrees() {
        List<ChunkPos> barren = new ArrayList<>(chunksOf(Biomes.DESERT, 2));
        barren.addAll(chunksOf(Biomes.DEEP_OCEAN, 2));
        for (ChunkPos pos : barren) {
            ProtoChunk chunk = decorated(pos);
            assertEquals(0, count(chunk, BlockTags.LOGS) + count(chunk, BlockTags.LEAVES), "tree blocks in chunk " + pos);
        }
    }

    @Test
    void everyTrunkStandsOnDirtLikeGround() {
        int trunksChecked = 0;
        for (ResourceKey<Biome> biome : List.of(Biomes.FOREST, Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.JUNGLE, Biomes.SAVANNA,
            Biomes.MANGROVE_SWAMP, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.CHERRY_GROVE, Biomes.SWAMP)) {
            for (ChunkPos pos : someChunksOf(biome, 2)) {
                ProtoChunk chunk = decorated(pos);
                assertEquals(0, trunksNotOnDirt(chunk), biome.identifier() + " chunk " + pos);
                trunksChecked += trunkFeet(chunk);
            }
        }
        assertTrue(trunksChecked > 50, "only " + trunksChecked + " trunks were checked");
    }

    @Test
    void treesAreSpreadOverTheChunkInsteadOfPiledOnItsEdges() {
        int[] perX = new int[16];
        int[] perZ = new int[16];
        int total = 0;
        for (ChunkPos pos : someChunksOf(Biomes.FOREST, FOREST_CHUNKS_FOR_SPREAD)) {
            ProtoChunk chunk = decorated(pos);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (lowestLog(chunk, x, z) != NO_LOG) {
                        perX[x]++;
                        perZ[z]++;
                        total++;
                    }
                }
            }
        }
        assertTrue(total > 200, "only " + total + " trunks in the forest chunks");
        assertTrue(largest(perX) < total * MAX_SHARE_OF_ONE_LINE, "trunks per x line " + Arrays.toString(perX));
        assertTrue(largest(perZ) < total * MAX_SHARE_OF_ONE_LINE, "trunks per z line " + Arrays.toString(perZ));
    }

    @Test
    void swampAndMangroveTreesStandInShallowWaterOnly() {
        assertTrue(this.trees.describe(Biomes.SWAMP).contains("water<=2"), this.trees.describe(Biomes.SWAMP));
        assertTrue(this.trees.describe(Biomes.MANGROVE_SWAMP).contains("water<=5"), this.trees.describe(Biomes.MANGROVE_SWAMP));
        int feetInWater = 0;
        for (ResourceKey<Biome> biome : List.of(Biomes.SWAMP, Biomes.MANGROVE_SWAMP)) {
            for (ChunkPos pos : someChunksOf(biome, 4, 2)) {
                ProtoChunk chunk = decorated(pos);
                feetInWater += trunkFeetInWater(chunk, MAX_FILTERED_WATER_DEPTH);
            }
        }
        assertTrue(feetInWater > 0, "no tree stands in the shallow water of the swamp chunks");
    }

    @Test
    void derivedStylesAndCost() {
        printStyleTable();
        List<ChunkPos> mixed = new ArrayList<>();
        for (int i = 0; i < COST_CHUNKS; i++) {
            mixed.add(new ChunkPos(SEARCH_RADIUS + 7 * i, -SEARCH_RADIUS - 5 * i));
        }
        printCost("mixed biomes", mixed);
        List<ChunkPos> wooded = new ArrayList<>(someChunksOf(Biomes.FOREST, COST_CHUNKS / 2));
        wooded.addAll(someChunksOf(Biomes.DARK_FOREST, COST_CHUNKS / 2));
        printCost("forest and dark forest", wooded);
    }

    /** Decorates a warm-up set, then a fresh set of the same chunks, and prints the time of the second. */
    private void printCost(String label, List<ChunkPos> positions) {
        decorateAll(generateAll(positions));
        List<ProtoChunk> chunks = generateAll(positions);
        long start = System.nanoTime();
        decorateAll(chunks);
        long elapsed = System.nanoTime() - start;
        int treeBlocks = 0;
        for (ProtoChunk chunk : chunks) {
            treeBlocks += count(chunk, BlockTags.LOGS) + count(chunk, BlockTags.LEAVES);
        }
        System.out.printf("PreviewTrees cost, %s: %.3f ms per chunk over %d chunks (after a warm-up pass), %d tree blocks per chunk%n",
            label, elapsed / 1.0e6 / chunks.size(), chunks.size(), treeBlocks / chunks.size());
    }

    private void printStyleTable() {
        for (ResourceKey<Biome> key : List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.JUNGLE, Biomes.SAVANNA, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.CHERRY_GROVE,
            Biomes.DESERT, Biomes.SNOWY_PLAINS)) {
            System.out.println("PreviewTrees " + key.identifier().getPath() + ": " + this.trees.describe(key));
        }
    }

    private ProtoChunk decorated(ChunkPos pos) {
        ProtoChunk chunk = this.previews.generate(pos);
        this.trees.decorate(chunk);
        return chunk;
    }

    private List<ProtoChunk> generateAll(List<ChunkPos> positions) {
        List<ProtoChunk> chunks = new ArrayList<>();
        for (ChunkPos pos : positions) {
            chunks.add(this.previews.generate(pos));
        }
        return chunks;
    }

    private void decorateAll(List<ProtoChunk> chunks) {
        for (ProtoChunk chunk : chunks) {
            this.trees.decorate(chunk);
        }
    }

    /** Exactly the wanted number of chunks entirely in the biome; fails when the seed has too few within the search area. */
    private List<ChunkPos> chunksOf(ResourceKey<Biome> biome, int wanted) {
        List<ChunkPos> found = someChunksOf(biome, wanted);
        assertEquals(wanted, found.size(), "chunks entirely in " + biome.identifier() + " within " + SEARCH_RADIUS + " chunks");
        return found;
    }

    /** Up to the wanted number of chunks, searching on a coarse grid, whose probe columns all lie in the given biome. */
    private List<ChunkPos> someChunksOf(ResourceKey<Biome> biome, int wanted) {
        return someChunksOf(biome, wanted, PROBES_PER_AXIS);
    }

    /** The same with a given number of probe columns per axis; fewer probes find smaller biomes such as swamps. */
    private List<ChunkPos> someChunksOf(ResourceKey<Biome> biome, int wanted, int probesPerAxis) {
        BiomeResolver climate = this.terrain.generator().getBiomeSource()
            .createResolver(this.terrain.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build()));
        List<ChunkPos> found = new ArrayList<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS && found.size() < wanted; x += SEARCH_STEP) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS && found.size() < wanted; z += SEARCH_STEP) {
                if (isEntirelyIn(climate, x, z, biome, probesPerAxis)) {
                    found.add(new ChunkPos(x, z));
                }
            }
        }
        return found;
    }

    private static boolean isEntirelyIn(BiomeResolver climate, int chunkX, int chunkZ, ResourceKey<Biome> biome, int probes) {
        int step = 16 / (probes - 1);
        for (int i = 0; i < probes; i++) {
            for (int j = 0; j < probes; j++) {
                int quartX = QuartPos.fromBlock(chunkX * 16 + i * step - (i == probes - 1 ? 1 : 0));
                int quartZ = QuartPos.fromBlock(chunkZ * 16 + j * step - (j == probes - 1 ? 1 : 0));
                if (!climate.getNoiseBiome(quartX, SURFACE_QUART_Y, quartZ).is(biome)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int count(ProtoChunk chunk, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        int count = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(tag)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static int differingBlocks(ProtoChunk expected, ProtoChunk actual) {
        int differing = 0;
        for (int index = 0; index < expected.getSectionsCount(); index++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (expected.getSection(index).getBlockState(x, y, z) != actual.getSection(index).getBlockState(x, y, z)) {
                            differing++;
                        }
                    }
                }
            }
        }
        return differing;
    }

    /** The columns whose lowest log, the foot of a trunk, has no dirt-like block under it. */
    private static int trunksNotOnDirt(ProtoChunk chunk) {
        int floating = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int foot = lowestLog(chunk, x, z);
                if (foot != NO_LOG && !PreviewTrees.isSoil(blockAt(chunk, x, foot - 1, z))) {
                    floating++;
                }
            }
        }
        return floating;
    }

    private static int largest(int[] values) {
        int largest = 0;
        for (int value : values) {
            largest = Math.max(largest, value);
        }
        return largest;
    }

    /** Counts the trunks standing in water; fails when one stands deeper than the deepest depth filter allows. */
    private static int trunkFeetInWater(ProtoChunk chunk, int deepest) {
        int inWater = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (lowestLog(chunk, x, z) == NO_LOG) {
                    continue;
                }
                int depth = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                assertTrue(depth <= deepest, "a trunk stands in " + depth + " blocks of water");
                if (depth > 0) {
                    inWater++;
                }
            }
        }
        return inWater;
    }

    private static int trunkFeet(ProtoChunk chunk) {
        int feet = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (lowestLog(chunk, x, z) != NO_LOG) {
                    feet++;
                }
            }
        }
        return feet;
    }

    private static int lowestLog(ProtoChunk chunk, int x, int z) {
        for (int y = chunk.getMinY() + 1; y <= chunk.getMaxY(); y++) {
            if (blockAt(chunk, x, y, z).is(BlockTags.LOGS)) {
                return y;
            }
        }
        return NO_LOG;
    }

    private static BlockState blockAt(ProtoChunk chunk, int x, int y, int z) {
        return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x, y & 15, z);
    }

    private Terrain terrain(long seed) {
        Holder<NoiseGeneratorSettings> settings = this.registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> parameters = this.registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
        RandomState randomState = RandomState.create(this.registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
        return new Terrain(seed, generator, settings.value(), randomState, PalettedContainerFactory.create(this.registries), this.level);
    }

    /** The same stand-in Level as PreviewGeneratorTest uses: a ServerLevel whose constructor never ran, answering only the flat-bedrock setting. */
    private static Level standInLevel() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            WorldConfiguration config = (WorldConfiguration) unsafe.allocateInstance(WorldConfiguration.class);
            config.environment = (WorldConfiguration.Environment) unsafe.allocateInstance(WorldConfiguration.Environment.class);
            Level level = (Level) unsafe.allocateInstance(ServerLevel.class);
            Field paperConfig = Level.class.getDeclaredField("paperConfig");
            paperConfig.setAccessible(true);
            paperConfig.set(level, config);
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build the stand-in level", e);
        }
    }
}
