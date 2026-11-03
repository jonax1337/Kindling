package net.kindlingmc.preview;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sky light on built chunks: open sky is 15, leaves and water dampen by one, enclosed cells are dark, light spreads sideways. */
@Normal
class SkyLightTest {
    private static final long SEED = 11L;
    private static final int GROUND_TOP_Y = 63;
    private static final int COST_CHUNKS = 40;

    private final PreviewTestWorld world = new PreviewTestWorld(SEED);

    @Test
    void openSkyIsFullyLitAndStoneIsDark() {
        ProtoChunk chunk = groundedChunk();
        Lit lit = Lit.of(chunk);
        assertEquals(15, lit.at(8, GROUND_TOP_Y + 1, 8));
        assertEquals(15, lit.at(0, 200, 15));
        assertEquals(0, lit.at(8, GROUND_TOP_Y, 8));
    }

    @Test
    void groundUnderALeafBlockIsLitByOneLessThanTheSky() {
        ProtoChunk chunk = groundedChunk();
        set(chunk, 8, 70, 8, Blocks.OAK_LEAVES.defaultBlockState());
        Lit lit = Lit.of(chunk);
        assertEquals(14, lit.at(8, 70, 8), "the leaf block itself");
        assertEquals(14, lit.at(8, 69, 8), "air under it is lit from the open air beside it");
    }

    @Test
    void groundUnderACanopyIsLitFromTheOpenColumnsAround() {
        ProtoChunk chunk = groundedChunk();
        for (int x = 4; x <= 12; x++) {
            for (int z = 4; z <= 12; z++) {
                set(chunk, x, 70, z, Blocks.OAK_LEAVES.defaultBlockState());
            }
        }
        Lit lit = Lit.of(chunk);
        assertEquals(14, lit.at(8, 70, 8), "light falls through the leaves, losing one level");
        assertEquals(13, lit.at(8, 69, 8), "and one more level under them");
        assertEquals(14, lit.at(4, 69, 8), "at the canopy's edge the open air beside gives 14");
        assertEquals(15, lit.at(3, 69, 8), "next to the canopy the sky is still 15");
    }

    @Test
    void aCellEnclosedInStoneIsDark() {
        ProtoChunk chunk = groundedChunk();
        set(chunk, 8, 40, 8, Blocks.AIR.defaultBlockState());
        assertEquals(0, Lit.of(chunk).at(8, 40, 8));
    }

    @Test
    void lightSpreadsSidewaysIntoATunnelAndFadesWithDistance() {
        ProtoChunk chunk = groundedChunk();
        for (int x = 0; x <= 8; x++) {
            set(chunk, x, GROUND_TOP_Y, 8, Blocks.AIR.defaultBlockState());
        }
        for (int x = 1; x <= 8; x++) {
            set(chunk, x, GROUND_TOP_Y + 1, 8, Blocks.STONE.defaultBlockState());
        }
        Lit lit = Lit.of(chunk);
        for (int x = 0; x <= 8; x++) {
            assertEquals(15 - x, lit.at(x, GROUND_TOP_Y, 8), "tunnel cell " + x);
        }
    }

    @Test
    void waterDarkensByOneLevelPerBlock() {
        ProtoChunk chunk = groundedChunk();
        for (int y = GROUND_TOP_Y + 1; y <= GROUND_TOP_Y + 3; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    set(chunk, x, y, z, Blocks.WATER.defaultBlockState());
                }
            }
        }
        Lit lit = Lit.of(chunk);
        assertEquals(14, lit.at(8, GROUND_TOP_Y + 3, 8));
        assertEquals(13, lit.at(8, GROUND_TOP_Y + 2, 8));
        assertEquals(12, lit.at(8, GROUND_TOP_Y + 1, 8));
        assertEquals(15, lit.at(8, GROUND_TOP_Y + 4, 8));
    }

    @Test
    void anEmptyChunkIsLitAsOpenSky() {
        Lit lit = Lit.of(this.world.emptyChunk(new ChunkPos(0, 0)));
        assertEquals(15, lit.at(3, -64, 3));
    }

    @Test
    void realPreviewsHaveLightUnderTheirTreesAndTimeIt() {
        PreviewTrees trees = PreviewTrees.of(this.world.registries(), SEED);
        List<ProtoChunk> forests = new ArrayList<>();
        for (ChunkPos pos : this.world.someChunksOf(Biomes.DARK_FOREST, COST_CHUNKS)) {
            forests.add(this.world.finishedPreview(trees, pos));
        }
        assertTrue(!forests.isEmpty(), "no dark forest chunks found");
        int litUnderLeaves = 0;
        for (ProtoChunk chunk : forests) {
            litUnderLeaves += airCellsLitUnderLeaves(chunk);
        }
        assertTrue(litUnderLeaves > 0, "no air under any leaf block is lit");
        printCost(forests);
    }

    private static void printCost(List<ProtoChunk> chunks) {
        chunks.forEach(chunk -> SkyLight.fromAbove(chunk.getSections()));
        long start = System.nanoTime();
        chunks.forEach(chunk -> SkyLight.fromAbove(chunk.getSections()));
        long elapsed = System.nanoTime() - start;
        System.out.printf("SkyLight cost, dark forest previews: %.3f ms per chunk over %d chunks (after a warm-up pass)%n",
            elapsed / 1.0e6 / chunks.size(), chunks.size());
    }

    /** Air cells directly under a leaf block with light above 0. */
    private static int airCellsLitUnderLeaves(ProtoChunk chunk) {
        Lit lit = Lit.of(chunk);
        int count = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = chunk.getMinY() + 1; y <= chunk.getMaxY(); y++) {
                    if (PreviewTestWorld.blockAt(chunk, x, y, z).is(net.minecraft.tags.BlockTags.LEAVES)
                        && PreviewTestWorld.blockAt(chunk, x, y - 1, z).isAir() && lit.at(x, y - 1, z) > 0) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** A chunk with stone up to and including GROUND_TOP_Y and air above. */
    private ProtoChunk groundedChunk() {
        ProtoChunk chunk = this.world.emptyChunk(new ChunkPos(0, 0));
        for (int y = chunk.getMinY(); y <= GROUND_TOP_Y; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    set(chunk, x, y, z, Blocks.STONE.defaultBlockState());
                }
            }
        }
        return chunk;
    }

    private static void set(ProtoChunk chunk, int x, int y, int z, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
    }

    /** The sky light of a chunk, read back from the arrays the packet carries. */
    private record Lit(SkyLight.Light light, int minY) {
        static Lit of(ProtoChunk chunk) {
            return new Lit(SkyLight.fromAbove(chunk.getSections()), chunk.getMinY());
        }

        /** The light at a cell; 15 in sections without an array above the highest block, 0 in all-zero sections. */
        int at(int x, int y, int z) {
            return PreviewFidelity.skyLightAt(this.light, this.minY, x, y, z);
        }
    }
}
