package net.kindlingmc.preview;

import java.util.EnumSet;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Comparing two chunks of one position column by column, which is what a preview and a second real generation are measured with. */
@Normal
class ChunkComparisonTest {
    private static final int GROUND_TOP_Y = 63;
    private static final int COLUMNS = 256;
    private static final int DARK = 5;
    private static final int LIT = 15;
    private static final double DELTA = 1e-9;

    private final PreviewTestWorld world = new PreviewTestWorld(1L);

    @Test
    void identicalChunksAreEqualInEveryColumn() {
        PreviewFidelity.Side reference = sideOf(ground(), LIT);
        PreviewFidelity.Side other = sideOf(ground(), LIT);

        Map<String, Object> figures = PreviewFidelity.compareSides(reference, other).toFigures();

        assertEquals(1L, figures.get("chunks"));
        assertEquals(100.0, percentOf(group(figures, "height"), "equal"), DELTA);
        assertEquals(100.0, percentOf(group(figures, "top_block"), "equal"), DELTA);
        assertEquals(100.0, group(figures, "light").get("above_surface_equal_percent"));
    }

    @Test
    void aTreeOnlyTheOtherSideHasIsAnExtraTreeOnAHigherSurface() {
        ProtoChunk withTree = ground();
        withTree.getSection(withTree.getSectionIndex(GROUND_TOP_Y + 1)).setBlockState(8, (GROUND_TOP_Y + 1) & 15, 8, Blocks.OAK_LEAVES.defaultBlockState(), false);

        Map<String, Object> figures = PreviewFidelity.compareSides(sideOf(ground(), LIT), sideOf(withTree, LIT)).toFigures();

        assertEquals(1L, count(group(figures, "top_block"), "tree_extra"));
        assertEquals(0L, count(group(figures, "top_block"), "tree_missing"));
        assertEquals(COLUMNS - 1L, count(group(figures, "top_block"), "equal"));
        assertEquals(1L, count(group(figures, "height"), "off_by_1_to_2"));
    }

    @Test
    void theSameTreeSeenFromTheOtherSideIsAMissingTree() {
        ProtoChunk withTree = ground();
        withTree.getSection(withTree.getSectionIndex(GROUND_TOP_Y + 1)).setBlockState(8, (GROUND_TOP_Y + 1) & 15, 8, Blocks.OAK_LEAVES.defaultBlockState(), false);

        Map<String, Object> figures = PreviewFidelity.compareSides(sideOf(withTree, LIT), sideOf(ground(), LIT)).toFigures();

        assertEquals(1L, count(group(figures, "top_block"), "tree_missing"));
        assertEquals(1L, count(group(figures, "height"), "off_by_1_to_2"));
    }

    @Test
    void eachSideIsAskedForItsOwnLight() {
        Map<String, Object> figures = PreviewFidelity.compareSides(sideOf(ground(), LIT), sideOf(ground(), DARK)).toFigures();

        Map<String, Object> light = group(figures, "light");
        assertEquals(0.0, light.get("above_surface_equal_percent"));
        assertEquals((double) LIT - DARK, (double) light.get("band_mean_abs_difference"), DELTA);
        assertEquals(100.0, light.get("band_differ_by_5_plus_percent"));
    }

    private ProtoChunk ground() {
        ProtoChunk chunk = this.world.emptyChunk(new ChunkPos(0, 0));
        for (int y = chunk.getMinY(); y <= GROUND_TOP_Y; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, Blocks.STONE.defaultBlockState(), false);
                }
            }
        }
        return chunk;
    }

    private static PreviewFidelity.Side sideOf(ProtoChunk chunk, int constantLight) {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.WORLD_SURFACE));
        return new PreviewFidelity.Side(chunk, (x, y, z) -> constantLight);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> group(Map<String, Object> figures, String name) {
        return (Map<String, Object>) figures.get(name);
    }

    private static long count(Map<String, Object> group, String category) {
        return (long) group(group, category).get("columns");
    }

    private static double percentOf(Map<String, Object> group, String category) {
        return (double) group(group, category).get("percent");
    }
}
