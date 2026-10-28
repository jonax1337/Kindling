package net.kindlingmc.sleeping;

import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import net.caffeinemc.mods.lithium.common.hopper.LithiumStackList;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * What wakes the block entities sleeping on a Lithium stack list: every change of the inventory, but not a count that
 * Paper's hopper sets for its move event and sets back in the same call.
 */
@VanillaFeature
class StackListWakeTest {

    private static final int SLOTS = 5;
    private static final int FULL = 64;
    private static final int MOVED = 1;

    private final WakeCounter listeners = new WakeCounter();
    private final SimpleContainer sameSizedContainer = new SimpleContainer(SLOTS);
    private LithiumStackList stackList;
    private ItemStack stack;

    @BeforeEach
    void fillTheFirstSlot() {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        this.stack = new ItemStack(Items.COBBLESTONE, FULL);
        items.set(0, this.stack);
        this.stackList = new LithiumStackList(items, FULL);
        this.stackList.setNextInventoryModificationCallback(this.listeners);
    }

    @Test
    void aCountSetBackWakesNobody() {
        this.stack.setCountTemporarily(MOVED);
        this.stack.setCount(FULL);

        assertEquals(0, this.listeners.wakes);
    }

    @Test
    void aCountSetBackStillInvalidatesWhatIsCachedFromTheCounts() {
        final long modCountBefore = this.stackList.getModCount();
        final int signalBefore = this.stackList.getSignalStrength(this.sameSizedContainer);

        this.stack.setCountTemporarily(MOVED);
        final int signalWhileMoved = this.stackList.getSignalStrength(this.sameSizedContainer);
        this.stack.setCount(FULL);

        assertNotEquals(signalBefore, signalWhileMoved);
        assertEquals(signalBefore, this.stackList.getSignalStrength(this.sameSizedContainer));
        assertNotEquals(modCountBefore, this.stackList.getModCount());
    }

    @Test
    void aCountSetBackKeepsTheSlotCounts() {
        this.stack.setCountTemporarily(MOVED);
        this.stack.setCount(FULL);

        assertEquals(1, this.stackList.getOccupiedSlots());
        assertEquals(1, this.stackList.getFullSlots());
    }

    @Test
    void aCountThatStaysChangedWakesTheListeners() {
        this.stack.setCountTemporarily(MOVED);
        this.stack.setCount(FULL - MOVED);

        assertEquals(1, this.listeners.wakes);
    }

    @Test
    void replacingTheResizedStackWakesTheListeners() {
        this.stack.setCountTemporarily(MOVED);
        this.stackList.set(0, this.stack.copyWithCount(FULL - MOVED));

        assertEquals(1, this.listeners.wakes);
    }

    @Test
    void emptyingTheResizedStackWakesTheListeners() {
        this.stack.setCountTemporarily(MOVED);
        this.stack.shrink(MOVED);

        assertEquals(1, this.listeners.wakes);
    }

    @Test
    void anotherChangeWhileTheCountIsChangedMakesSettingItBackAChange() {
        this.stack.setCountTemporarily(MOVED);
        this.stackList.set(1, new ItemStack(Items.STONE));
        this.stackList.setNextInventoryModificationCallback(this.listeners);
        this.stack.setCount(FULL);

        assertEquals(2, this.listeners.wakes);
    }

    @Test
    void anOrdinaryCountChangeWakesTheListeners() {
        this.stack.setCount(FULL - MOVED);

        assertEquals(1, this.listeners.wakes);
    }

    @Test
    void aComponentChangeWakesTheListeners() {
        this.stack.set(DataComponents.CUSTOM_NAME, Component.literal("renamed by a plugin"));

        assertEquals(1, this.listeners.wakes);
    }

    /** Counts how often the stack list reports a content change to the block entity it belongs to. */
    private static final class WakeCounter implements InventoryChangeTracker {

        private int wakes;

        @Override
        public void lithium$emitContentModified() {
            this.wakes++;
        }

        @Override
        public void lithium$emitStackListReplaced() {
        }

        @Override
        public void lithium$emitRemoved() {
        }

        @Override
        public void lithium$emitFirstComparatorAdded() {
        }

        @Override
        public void lithium$forwardContentChangeOnce(InventoryChangeListener listener, LithiumStackList stackList) {
        }

        @Override
        public void lithium$forwardMajorInventoryChanges(InventoryChangeListener listener) {
        }

        @Override
        public void lithium$stopForwardingMajorInventoryChanges(InventoryChangeListener listener) {
        }
    }
}
