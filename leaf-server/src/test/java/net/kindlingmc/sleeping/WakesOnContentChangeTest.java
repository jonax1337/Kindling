package net.kindlingmc.sleeping;

import java.lang.reflect.Constructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plugin that changes a stack of a sleeping furnace or brewing stand in place wakes it, as Paper would see it next tick. */
@VanillaFeature
class WakesOnContentChangeTest {

    private static final int RESULT_SLOT = 2;
    private static final int BOTTLE_SLOT = 0;

    @Test
    void aSleepingFurnaceWakesWhenAPluginTakesFromItsResult() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        furnace.setItem(RESULT_SLOT, new ItemStack(Items.STONE, 64));
        fallAsleep(furnace);

        CraftItemStack.asBukkitMirror(furnace.getItem(RESULT_SLOT)).setAmount(1);

        assertFalse(furnace.isSleeping());
    }

    @Test
    void aSleepingBrewingStandWakesWhenAPluginChangesABottle() {
        BrewingStandBlockEntity stand = new BrewingStandBlockEntity(BlockPos.ZERO, Blocks.BREWING_STAND.defaultBlockState());
        stand.setItem(BOTTLE_SLOT, new ItemStack(Items.POTION));
        fallAsleep(stand);

        stand.getItem(BOTTLE_SLOT).set(DataComponents.CUSTOM_NAME, Component.literal("renamed by a plugin"));

        assertFalse(stand.isSleeping());
    }

    private static <T extends BlockEntity & WakesOnContentChange> void fallAsleep(T blockEntity) {
        blockEntity.lithium$setTickWrapper(tickWrapperOf(blockEntity));
        blockEntity.sleepUntilContentChanges();
        assertTrue(blockEntity.isSleeping());
    }

    /** The wrapper a chunk ticks the block entity through; only the chunk creates one. */
    private static LevelChunk.RebindableTickingBlockEntityWrapper tickWrapperOf(BlockEntity blockEntity) {
        TickingBlockEntity ticker = new TickingBlockEntity() {
            @Override
            public void tick() {
            }

            @Override
            public boolean isRemoved() {
                return false;
            }

            @Override
            public BlockPos getPos() {
                return blockEntity.getBlockPos();
            }

            @Override
            public String getType() {
                return "test";
            }
        };
        try {
            Constructor<LevelChunk.RebindableTickingBlockEntityWrapper> constructor =
                LevelChunk.RebindableTickingBlockEntityWrapper.class.getDeclaredConstructor(TickingBlockEntity.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ticker);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create the tick wrapper of a block entity", e);
        }
    }
}
