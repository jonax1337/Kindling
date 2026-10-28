package net.kindlingmc.bench;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/**
 * Stands in for a protection or logging plugin that listens to every hopper transfer and lets it through. Its presence
 * alone makes Paper fire {@link InventoryMoveItemEvent} for each transfer, so hoppers take the event path that most
 * real servers run on.
 */
final class PassiveMoveListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void letThrough(InventoryMoveItemEvent event) {
    }
}
