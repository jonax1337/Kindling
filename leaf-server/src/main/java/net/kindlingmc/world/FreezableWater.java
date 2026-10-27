package net.kindlingmc.world;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The one kind of block that can turn into ice: a water block, not a waterlogged block.
 */
public final class FreezableWater {

    private FreezableWater() {
    }

    public static boolean isFreezableWater(final BlockState state) {
        return state.getFluidState().is(Fluids.WATER) && state.getBlock() instanceof LiquidBlock;
    }
}
