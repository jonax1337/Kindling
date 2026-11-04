package net.kindlingmc.preview;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The category of a top block that differs, and the order in which the categories win. */
@Normal
class TopBlockMismatchTest {
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SHORT_GRASS = Blocks.SHORT_GRASS.defaultBlockState();

    @Test
    void aTreeOnlyTheRealChunkHasIsMissing() {
        assertEquals(TopBlockMismatch.TREE_MISSING, TopBlockMismatch.of(Blocks.OAK_LEAVES.defaultBlockState(), GRASS_BLOCK));
        assertEquals(TopBlockMismatch.TREE_MISSING, TopBlockMismatch.of(Blocks.OAK_LOG.defaultBlockState(), SHORT_GRASS));
    }

    @Test
    void aTreeOnlyThePreviewHasIsExtra() {
        assertEquals(TopBlockMismatch.TREE_EXTRA, TopBlockMismatch.of(GRASS_BLOCK, Blocks.BIRCH_LEAVES.defaultBlockState()));
    }

    @Test
    void aTreeBeatsSnowAndWater() {
        assertEquals(TopBlockMismatch.TREE_MISSING, TopBlockMismatch.of(Blocks.SPRUCE_LEAVES.defaultBlockState(), Blocks.SNOW.defaultBlockState()));
        assertEquals(TopBlockMismatch.TREE_EXTRA, TopBlockMismatch.of(Blocks.WATER.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState()));
    }

    @Test
    void differentKindsOfTreeAreNotATreeMismatch() {
        assertEquals(TopBlockMismatch.STRUCTURE_OR_OTHER,
            TopBlockMismatch.of(Blocks.OAK_LEAVES.defaultBlockState(), Blocks.BIRCH_LEAVES.defaultBlockState()));
    }

    @Test
    void snowAndIceOnOneSideOnlyAreSnowIce() {
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.SNOW.defaultBlockState(), GRASS_BLOCK));
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.STONE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState()));
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.PACKED_ICE.defaultBlockState(), Blocks.STONE.defaultBlockState()));
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.POWDER_SNOW.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.ICE.defaultBlockState(), Blocks.WATER.defaultBlockState()));
    }

    @Test
    void differentKindsOfSnowAreSnowIce() {
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.SNOW.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState()));
    }

    @Test
    void snowBeatsAPlant() {
        assertEquals(TopBlockMismatch.SNOW_ICE, TopBlockMismatch.of(Blocks.SNOW.defaultBlockState(), SHORT_GRASS));
    }

    @Test
    void waterAndWaterloggedBlocksOnOneSideOnlyAreWater() {
        BlockState waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        assertEquals(TopBlockMismatch.WATER, TopBlockMismatch.of(Blocks.WATER.defaultBlockState(), Blocks.SAND.defaultBlockState()));
        assertEquals(TopBlockMismatch.WATER, TopBlockMismatch.of(Blocks.SAND.defaultBlockState(), waterlogged));
        assertEquals(TopBlockMismatch.WATER, TopBlockMismatch.of(Blocks.SEAGRASS.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()));
    }

    @Test
    void waterBeatsAPlant() {
        assertEquals(TopBlockMismatch.WATER, TopBlockMismatch.of(Blocks.WATER.defaultBlockState(), SHORT_GRASS));
    }

    @Test
    void plantsWithoutCollisionOnEitherSideArePlants() {
        assertEquals(TopBlockMismatch.PLANT, TopBlockMismatch.of(SHORT_GRASS, GRASS_BLOCK));
        assertEquals(TopBlockMismatch.PLANT, TopBlockMismatch.of(GRASS_BLOCK, Blocks.DANDELION.defaultBlockState()));
        assertEquals(TopBlockMismatch.PLANT, TopBlockMismatch.of(Blocks.FERN.defaultBlockState(), SHORT_GRASS));
    }

    @Test
    void solidBlocksThatDifferAreOther() {
        assertEquals(TopBlockMismatch.STRUCTURE_OR_OTHER, TopBlockMismatch.of(Blocks.COBBLESTONE.defaultBlockState(), GRASS_BLOCK));
    }
}
