package net.kindlingmc.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla vegetation on previews of a real overworld generator: the same chunk gets the same plants on any thread,
 * forests get trees and plains short grass, and no feature of any overworld biome fails on a preview except those that
 * need the server level. Prints the cost
 * of decorating against the cost of generating.
 */
@Normal
class PreviewVegetationTest {
    private static final long SEED = 11L;
    private static final int THREADS = 4;
    private static final int COST_CHUNKS = 60;
    private static final ChunkPos FLAT_CHUNK = new ChunkPos(5, -3);
    private static final int FLAT_GRASS_Y = 63;
    private static final int FLAT_DIRT_DEPTH = 3;
    /** Its pale oaks' PaleMossDecorator places moss through level.getLevel().getChunkSource().getGenerator(). */
    private static final Set<String> NEEDS_THE_SERVER_LEVEL = Set.of("pale_garden_vegetation");

    private final PreviewTestWorld world = new PreviewTestWorld(SEED);
    private final PreviewVegetation vegetation = this.world.vegetation();

    @Test
    void theSameChunkGetsTheSamePlantsOnAnyThread() throws Exception {
        List<ChunkPos> positions = new ArrayList<>(this.world.someChunksOf(Biomes.FOREST, 4));
        positions.addAll(this.world.someChunksOf(Biomes.PLAINS, 4));
        List<ProtoChunk> serial = new ArrayList<>();
        for (ChunkPos pos : positions) {
            serial.add(decorated(pos));
        }
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<ProtoChunk>> parallel = new ArrayList<>();
            for (ChunkPos pos : positions) {
                parallel.add(pool.submit(() -> decorated(pos)));
            }
            for (int i = 0; i < positions.size(); i++) {
                assertEquals(0, differingBlocks(serial.get(i), parallel.get(i).get()), "chunk " + positions.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void forestsGetLogsAndLeaves() {
        for (ChunkPos pos : this.world.someChunksOf(Biomes.FOREST, 3)) {
            ProtoChunk chunk = decorated(pos);
            assertTrue(count(chunk, BlockTags.LOGS) > 0, "logs in forest chunk " + pos);
            assertTrue(count(chunk, BlockTags.LEAVES) > 0, "leaves in forest chunk " + pos);
        }
    }

    @Test
    void plainsGetShortGrass() {
        for (ChunkPos pos : this.world.someChunksOf(Biomes.PLAINS, 3)) {
            assertTrue(count(decorated(pos), Blocks.SHORT_GRASS) > 0, "short grass in plains chunk " + pos);
        }
    }

    @Test
    void onlyFeaturesThatNeedTheServerLevelFail() {
        int biomes = 0;
        for (Holder<Biome> biome : this.world.terrain().generator().getBiomeSource().possibleBiomes()) {
            this.vegetation.decorate(flatChunkOf(biome));
            biomes++;
        }
        assertTrue(biomes > 50, "only " + biomes + " overworld biomes");
        Set<String> failed = new TreeSet<>();
        for (PlacedFeature feature : this.vegetation.failedFeatures()) {
            failed.add(this.world.registries().lookupOrThrow(Registries.PLACED_FEATURE).getResourceKey(feature)
                .map(key -> key.identifier().getPath()).orElseGet(feature::toString));
        }
        failed.removeAll(NEEDS_THE_SERVER_LEVEL);
        assertEquals(Set.of(), failed, "features that failed on a preview");
    }

    @Test
    void cost() throws Exception {
        List<ChunkPos> positions = new ArrayList<>();
        for (int i = 0; i < COST_CHUNKS; i++) {
            positions.add(new ChunkPos(300 + 7 * i, -300 - 5 * i));
        }
        timeGeneratingAndDecorating(positions);
        long[] nanos = timeGeneratingAndDecorating(positions);
        System.out.printf("PreviewVegetation cost, 1 thread, %d mixed chunks after a warm-up pass: generate %.3f ms, vegetation %.3f ms per chunk%n",
            positions.size(), nanos[0] / 1.0e6 / positions.size(), nanos[1] / 1.0e6 / positions.size());
    }

    /** Generating and decorating times in nanoseconds, summed over the chunks. */
    private long[] timeGeneratingAndDecorating(List<ChunkPos> positions) {
        long generating = 0;
        long decorating = 0;
        for (ChunkPos pos : positions) {
            long start = System.nanoTime();
            PreviewGenerator.Generated preview = this.world.previews().generateWithBiomes(pos);
            long generated = System.nanoTime();
            this.vegetation.decorate(preview);
            decorating += System.nanoTime() - generated;
            generating += generated - start;
        }
        return new long[] {generating, decorating};
    }

    private ProtoChunk decorated(ChunkPos pos) {
        PreviewGenerator.Generated preview = this.world.previews().generateWithBiomes(pos);
        this.vegetation.decorate(preview);
        return preview.chunk();
    }

    /** A chunk of one biome: stone, three blocks of dirt and grass on top at sea level, air above. */
    private PreviewGenerator.Generated flatChunkOf(Holder<Biome> biome) {
        ProtoChunk chunk = this.world.emptyChunk(FLAT_CHUNK);
        int quartX = QuartPos.fromBlock(FLAT_CHUNK.getMinBlockX());
        int quartZ = QuartPos.fromBlock(FLAT_CHUNK.getMinBlockZ());
        for (int sectionY = chunk.getMinSectionY(); sectionY <= chunk.getMaxSectionY(); sectionY++) {
            chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY))
                .fillBiomesFromNoise((x, y, z) -> biome, quartX, QuartPos.fromSection(sectionY), quartZ);
        }
        for (int y = chunk.getMinY(); y <= FLAT_GRASS_Y; y++) {
            fillLayer(chunk, y, y == FLAT_GRASS_Y ? Blocks.GRASS_BLOCK : y >= FLAT_GRASS_Y - FLAT_DIRT_DEPTH ? Blocks.DIRT : Blocks.STONE);
        }
        ChunkBiomes biomes = ChunkBiomes.sections(SEED, chunk, chunk.getMinSectionY(), chunk.getMaxSectionY(),
            new ChunkBiomes.Outside(this.world.terrain()));
        return new PreviewGenerator.Generated(chunk, biomes);
    }

    private static void fillLayer(ProtoChunk chunk, int y, Block block) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                section.setBlockState(x, y & 15, z, block.defaultBlockState(), false);
            }
        }
    }

    private static int count(ProtoChunk chunk, TagKey<Block> tag) {
        return countMatching(chunk, state -> state.is(tag));
    }

    private static int count(ProtoChunk chunk, Block block) {
        return countMatching(chunk, state -> state.is(block));
    }

    private static int countMatching(ProtoChunk chunk, Predicate<BlockState> test) {
        int found = 0;
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (test.test(PreviewTestWorld.blockAt(chunk, x, y, z))) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    private static int differingBlocks(ProtoChunk first, ProtoChunk second) {
        int differing = 0;
        for (int y = first.getMinY(); y <= first.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (PreviewTestWorld.blockAt(first, x, y, z) != PreviewTestWorld.blockAt(second, x, y, z)) {
                        differing++;
                    }
                }
            }
        }
        return differing;
    }
}
