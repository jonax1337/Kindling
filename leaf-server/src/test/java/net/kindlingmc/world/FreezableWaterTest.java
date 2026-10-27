package net.kindlingmc.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@VanillaFeature
class FreezableWaterTest {

    @Test
    void aWaterBlockCanFreeze() {
        assertTrue(FreezableWater.isFreezableWater(Blocks.WATER.defaultBlockState()));
    }

    @Test
    void aWaterloggedBlockCannotFreeze() {
        final BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);

        assertFalse(FreezableWater.isFreezableWater(waterloggedSlab));
    }

    @Test
    void otherLiquidsAndSolidsCannotFreeze() {
        assertFalse(FreezableWater.isFreezableWater(Blocks.LAVA.defaultBlockState()));
        assertFalse(FreezableWater.isFreezableWater(Blocks.AIR.defaultBlockState()));
        assertFalse(FreezableWater.isFreezableWater(Blocks.ICE.defaultBlockState()));
    }
}
