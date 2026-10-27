package net.kindlingmc.bench;

import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Stands in for a plugin that tags what certain hoppers move, as filters and custom-item plugins do: it replaces the
 * moved item through {@link InventoryMoveItemEvent#setItem}. Paper then takes from the hopper what was moved, whatever
 * the replacement is.
 */
final class HopperRewriter implements Listener {

    private static final Component TAG = Component.text("moved");

    private final Set<Block> hoppers;

    HopperRewriter(Set<Block> hoppers) {
        this.hoppers = hoppers;
    }

    @EventHandler
    public void tag(InventoryMoveItemEvent event) {
        if (isTagged(event.getSource())) {
            ItemStack tagged = event.getItem().clone();
            tagged.editMeta(meta -> meta.displayName(TAG));
            event.setItem(tagged);
        }
    }

    private boolean isTagged(Inventory source) {
        Location location = source.getLocation();
        return location != null && hoppers.contains(location.getBlock());
    }
}
