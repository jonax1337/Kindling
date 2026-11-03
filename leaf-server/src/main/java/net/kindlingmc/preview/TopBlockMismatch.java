package net.kindlingmc.preview;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Why the top block of a column differs between the preview and the real chunk. The first category that fits wins, in
 * the order of the constants: a missing or extra tree explains the most, then snow and ice, water, plants.
 */
enum TopBlockMismatch {
    /** The real column ends in a log or leaves, the preview's does not. */
    TREE_MISSING,
    /** The preview's column ends in a log or leaves, the real one's does not. */
    TREE_EXTRA,
    /** Either is snow (layer, block, powder) or ice: snow on one side only, or a different kind of snow or ice. */
    SNOW_ICE,
    /** Exactly one of the two is water or waterlogged. */
    WATER,
    /** Either is a plant: a block that is neither air nor a fluid and does not block motion, such as grass, ferns and flowers. */
    PLANT,
    /** Everything else: structures, other terrain, a different kind of tree. */
    STRUCTURE_OR_OTHER;

    /** The category of two top blocks that are different blocks. */
    static TopBlockMismatch of(final BlockState real, final BlockState preview) {
        if (isTree(real) != isTree(preview)) {
            return isTree(real) ? TREE_MISSING : TREE_EXTRA;
        }
        if (isSnowOrIce(real) || isSnowOrIce(preview)) {
            return SNOW_ICE;
        }
        if (isWater(real) != isWater(preview)) {
            return WATER;
        }
        if (isPlant(real) || isPlant(preview)) {
            return PLANT;
        }
        return STRUCTURE_OR_OTHER;
    }

    private static boolean isTree(final BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    private static boolean isSnowOrIce(final BlockState state) {
        return state.is(BlockTags.SNOW) || state.is(BlockTags.ICE);
    }

    private static boolean isWater(final BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean isPlant(final BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.BLOCKS_MOTION);
    }
}
