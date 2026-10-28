package net.kindlingmc.sleeping;

import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import net.caffeinemc.mods.lithium.common.block.entity.SleepingBlockEntity;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import net.caffeinemc.mods.lithium.common.hopper.InventoryHelper;
import net.minecraft.world.Container;

/**
 * A sleeping block entity that wakes when a stack in its own inventory changes in place. {@code setItem} and
 * {@code setChanged} wake it already, but a plugin can change a stack without either, through the Bukkit mirror that
 * {@code Inventory#getItem} returns ({@code setAmount}, {@code setItemMeta}); on Paper the block entity sees that
 * change on its next tick. Such a change reaches only the listeners of the inventory's Lithium stack list, so the
 * block entity listens there while it sleeps.
 */
public interface WakesOnContentChange extends SleepingBlockEntity, InventoryChangeTracker, InventoryChangeListener, LithiumInventory {

    /** Falls asleep, if it can, until something wakes it, a change of its own inventory included. */
    default void sleepUntilContentChanges() {
        if (this.lithium$startSleeping()) {
            this.listenForContentChangesOnce(InventoryHelper.getLithiumStackList(this), this);
        }
    }

    @Override
    default void lithium$handleInventoryContentModified(final Container inventory) {
        this.wakeUpNow();
    }

    /** The stack list was replaced or the block entity removed: awake, it listens again when it next falls asleep. */
    @Override
    default void lithium$handleInventoryRemoved(final Container inventory) {
        this.wakeUpNow();
    }

    /** A comparator does not change what the block entity does, so it keeps listening. */
    @Override
    default boolean lithium$handleComparatorAdded(final Container inventory) {
        return false;
    }
}
