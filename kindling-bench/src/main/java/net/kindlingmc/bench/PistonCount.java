package net.kindlingmc.bench;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;

/**
 * Counts piston extensions. Paper creates this event for every extension whether or not anyone listens, so counting
 * it does not change how pistons are handled (unlike hopper events, which Paper skips when nobody listens).
 */
final class PistonCount implements Listener {

    private long extensions;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        extensions++;
    }

    long extensions() {
        return extensions;
    }
}
