package net.kindlingmc.bench;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

/**
 * Stands in for a protection plugin that holds hoppers back for a while, as one does until its claims are loaded:
 * until the release tick it hides every hopper's source and cancels every transfer and pickup, then it lets
 * everything through. Afterwards a server has to move items again exactly as one without sleeping hoppers.
 */
final class HopperGuard implements Listener {

    private final int releaseTick;

    HopperGuard(int releaseTick) {
        this.releaseTick = releaseTick;
    }

    @EventHandler
    public void hideSource(HopperInventorySearchEvent event) {
        if (holding() && event.getContainerType() == HopperInventorySearchEvent.ContainerType.SOURCE) {
            event.setInventory(null);
        }
    }

    @EventHandler
    public void cancelTransfer(InventoryMoveItemEvent event) {
        if (holding()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void cancelPickup(InventoryPickupItemEvent event) {
        if (holding()) {
            event.setCancelled(true);
        }
    }

    private boolean holding() {
        return Bukkit.getCurrentTick() < releaseTick;
    }
}
