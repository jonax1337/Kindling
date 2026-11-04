package net.kindlingmc.preview;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Snow and ice on previews of a real overworld generator: cold biomes get them, warm ones none, and snow sits on what carries it. */
@Normal
class PreviewSnowTest {
    private static final long SEED = 11L;
    private static final int CHUNKS_PER_BIOME = 3;
    private static final int COST_CHUNKS = 40;

    private final PreviewTestWorld world = new PreviewTestWorld(SEED);
    private final PreviewTrees trees = PreviewTrees.of(this.world.registries(), SEED);

    @Test
    void snowyPlainsGetSnowLayersAndSnowyGrass() {
        int snow = 0;
        for (ChunkPos pos : chunksOf(Biomes.SNOWY_PLAINS)) {
            ProtoChunk chunk = this.world.finishedPreview(this.trees, pos);
            snow += count(chunk, Blocks.SNOW);
            assertEquals(0, snowWithoutSnowyGround(chunk), "snow without a snowy block below in " + pos);
        }
        assertTrue(snow > 3 * 100, "snow layers in snowy plains: " + snow);
    }

    @Test
    void snowLiesOnLeavesToo() {
        int snowOnLeaves = 0;
        for (ResourceKey<Biome> biome : List.of(Biomes.SNOWY_TAIGA, Biomes.GROVE, Biomes.TAIGA)) {
            for (ChunkPos pos : this.world.someChunksOf(biome, CHUNKS_PER_BIOME)) {
                snowOnLeaves += snowOnLeaves(this.world.finishedPreview(this.trees, pos));
            }
        }
        assertTrue(snowOnLeaves > 0, "no snow layer lies on a leaf block");
    }

    @Test
    void snowNeverSitsOnIceOrAir() {
        for (ChunkPos pos : chunksOf(Biomes.FROZEN_OCEAN)) {
            ProtoChunk chunk = this.world.finishedPreview(this.trees, pos);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    for (int y = chunk.getMinY() + 1; y <= chunk.getMaxY(); y++) {
                        if (PreviewTestWorld.blockAt(chunk, x, y, z).is(Blocks.SNOW)) {
                            BlockState below = PreviewTestWorld.blockAt(chunk, x, y - 1, z);
                            assertFalse(below.isAir() || below.is(BlockTags.CANNOT_SUPPORT_SNOW_LAYER), "snow on " + below + " in " + pos);
                        }
                    }
                }
            }
        }
    }

    @Test
    void frozenOceansGetIceOnTheirWater() {
        int ice = 0;
        for (ChunkPos pos : chunksOf(Biomes.FROZEN_OCEAN)) {
            ice += count(this.world.finishedPreview(this.trees, pos), Blocks.ICE);
        }
        assertTrue(ice > 3 * 100, "ice in frozen ocean: " + ice);
    }

    @Test
    void warmBiomesGetNeitherSnowNorIce() {
        for (ResourceKey<Biome> biome : List.of(Biomes.DESERT, Biomes.PLAINS, Biomes.OCEAN, Biomes.JUNGLE)) {
            for (ChunkPos pos : this.world.someChunksOf(biome, 2)) {
                ProtoChunk chunk = this.world.finishedPreview(this.trees, pos);
                assertEquals(0, count(chunk, Blocks.SNOW) + count(chunk, Blocks.ICE), biome.identifier() + " chunk " + pos);
            }
        }
    }

    @Test
    void theSameChunkAlwaysFreezesTheSame() {
        ChunkPos pos = chunksOf(Biomes.SNOWY_PLAINS).get(0);
        ProtoChunk first = this.world.finishedPreview(this.trees, pos);
        ProtoChunk second = this.world.finishedPreview(this.trees, pos);
        assertEquals(0, differingBlocks(first, second));
    }

    @Test
    void freezingCost() {
        List<ChunkPos> positions = new ArrayList<>(this.world.someChunksOf(Biomes.SNOWY_PLAINS, COST_CHUNKS / 2));
        positions.addAll(this.world.someChunksOf(Biomes.FROZEN_OCEAN, COST_CHUNKS / 2));
        List<ProtoChunk> warmUp = generate(positions);
        warmUp.forEach(chunk -> PreviewSnow.freeze(chunk, SEED, this.world.seaLevel()));
        List<ProtoChunk> chunks = generate(positions);
        long start = System.nanoTime();
        chunks.forEach(chunk -> PreviewSnow.freeze(chunk, SEED, this.world.seaLevel()));
        long elapsed = System.nanoTime() - start;
        System.out.printf("PreviewSnow cost, snowy plains and frozen ocean: %.3f ms per chunk over %d chunks (after a warm-up pass)%n",
            elapsed / 1.0e6 / chunks.size(), chunks.size());
    }

    private List<ProtoChunk> generate(List<ChunkPos> positions) {
        List<ProtoChunk> chunks = new ArrayList<>();
        for (ChunkPos pos : positions) {
            ProtoChunk chunk = this.world.previews().generate(pos);
            this.trees.decorate(chunk);
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<ChunkPos> chunksOf(ResourceKey<Biome> biome) {
        List<ChunkPos> found = this.world.someChunksOf(biome, CHUNKS_PER_BIOME);
        assertEquals(CHUNKS_PER_BIOME, found.size(), "chunks entirely in " + biome.identifier());
        return found;
    }

    private static int count(ProtoChunk chunk, Block block) {
        int count = 0;
        for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (PreviewTestWorld.blockAt(chunk, x, y, z).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Snow layers whose block below has the SNOWY property but not set to true. */
    private static int snowWithoutSnowyGround(ProtoChunk chunk) {
        int wrong = 0;
        for (int y = chunk.getMinY() + 1; y <= chunk.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (PreviewTestWorld.blockAt(chunk, x, y, z).is(Blocks.SNOW)) {
                        BlockState below = PreviewTestWorld.blockAt(chunk, x, y - 1, z);
                        if (below.hasProperty(SnowyBlock.SNOWY) && !below.getValue(SnowyBlock.SNOWY)) {
                            wrong++;
                        }
                    }
                }
            }
        }
        return wrong;
    }

    private static int snowOnLeaves(ProtoChunk chunk) {
        int onLeaves = 0;
        for (int y = chunk.getMinY() + 1; y <= chunk.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (PreviewTestWorld.blockAt(chunk, x, y, z).is(Blocks.SNOW) && PreviewTestWorld.blockAt(chunk, x, y - 1, z).is(BlockTags.LEAVES)) {
                        onLeaves++;
                    }
                }
            }
        }
        return onLeaves;
    }

    private static int differingBlocks(ProtoChunk expected, ProtoChunk actual) {
        int differing = 0;
        for (int y = expected.getMinY(); y <= expected.getMaxY(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (PreviewTestWorld.blockAt(expected, x, y, z) != PreviewTestWorld.blockAt(actual, x, y, z)) {
                        differing++;
                    }
                }
            }
        }
        return differing;
    }
}
