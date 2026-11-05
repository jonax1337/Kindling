package net.kindlingmc.bench;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * Counts the primed TNT that explodes in chosen chunks. Paper fires {@link ExplosionPrimeEvent} for every TNT that
 * explodes, whether or not anyone listens, so counting it does not change how TNT is handled.
 */
final class TntTally implements Listener {

    private final Set<Long> watchedChunks = new HashSet<>();
    private long explosions;

    /** Counts the TNT exploding in the chunk that holds {@code spot}. */
    void watch(Location spot) {
        watchedChunks.add(Chunk.getChunkKey(spot));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof TNTPrimed tnt && watchedChunks.contains(Chunk.getChunkKey(tnt.getLocation()))) {
            explosions++;
        }
    }

    long explosions() {
        return explosions;
    }
}
