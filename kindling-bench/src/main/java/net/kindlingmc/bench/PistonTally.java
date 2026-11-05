package net.kindlingmc.bench;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;

/**
 * Counts the extensions of chosen pistons by name, so a clock that drives a piston can be counted without a container
 * that would fill up. Paper creates the event for every extension whether or not anyone listens ({@link PistonCount}).
 */
final class PistonTally implements Listener {

    private final Map<Block, String> namesByPiston = new HashMap<>();
    private final Map<String, Long> extensions = new TreeMap<>();

    /** Counts every extension of the piston at {@code piston} under {@code name}. */
    void watch(Block piston, String name) {
        namesByPiston.put(piston, name);
        extensions.putIfAbsent(name, 0L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        String name = namesByPiston.get(event.getBlock());
        if (name != null) {
            extensions.merge(name, 1L, Long::sum);
        }
    }

    Map<String, Long> totals() {
        return new TreeMap<>(extensions);
    }
}
