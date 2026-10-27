package net.kindlingmc.bench;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * How long the players take from joining until every chunk within the send distance of their position has reached
 * them. Checks every tick until all players have their view or {@link #stop()} is called.
 */
final class ViewArrival {

    private static final long NOT_YET = -1;
    private static final double NANOS_PER_MILLI = 1e6;

    private final List<Player> players;
    private final List<long[]> viewOfPlayer;
    private final long[] arrivedNanos;
    private final long joinedNanos = System.nanoTime();
    private final BukkitTask task;

    /** Starts watching; call right after the players joined, while they still stand where they joined. */
    ViewArrival(Plugin plugin, List<Player> players, int sendDistance) {
        this.players = players;
        this.viewOfPlayer = players.stream().map(player -> chunksWithin(player, sendDistance)).toList();
        this.arrivedNanos = new long[players.size()];
        Arrays.fill(arrivedNanos, NOT_YET);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 1, 1);
    }

    void stop() {
        task.cancel();
    }

    /**
     * {@code join_full_view_ms}: how long the slowest player took (-1 if one never got its view) and
     * {@code join_full_view_ms_avg} over the players that did.
     */
    Map<String, Object> summary() {
        long[] arrived = Arrays.stream(arrivedNanos).filter(nanos -> nanos != NOT_YET).toArray();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("join_full_view_ms", arrived.length < players.size() ? -1 : millisOf(Arrays.stream(arrived).max().orElse(0)));
        summary.put("join_full_view_ms_avg", millisOf(Math.round(Arrays.stream(arrived).average().orElse(0))));
        return summary;
    }

    private void check() {
        boolean allArrived = true;
        for (int index = 0; index < players.size(); index++) {
            if (arrivedNanos[index] == NOT_YET && hasAll(players.get(index), viewOfPlayer.get(index))) {
                arrivedNanos[index] = System.nanoTime() - joinedNanos;
            }
            allArrived &= arrivedNanos[index] != NOT_YET;
        }
        if (allArrived) {
            task.cancel();
        }
    }

    private static boolean hasAll(Player player, long[] chunkKeys) {
        for (long key : chunkKeys) {
            if (!player.isChunkSent(key)) {
                return false;
            }
        }
        return true;
    }

    /** The chunks no further from the player's than the send distance; the server sends at least these. */
    private static long[] chunksWithin(Player player, int distance) {
        int centreX = player.getLocation().getBlockX() >> 4;
        int centreZ = player.getLocation().getBlockZ() >> 4;
        long[] keys = new long[(2 * distance + 1) * (2 * distance + 1)];
        int count = 0;
        for (int dz = -distance; dz <= distance; dz++) {
            for (int dx = -distance; dx <= distance; dx++) {
                if (dx * dx + dz * dz <= distance * distance) {
                    keys[count++] = Chunk.getChunkKey(centreX + dx, centreZ + dz);
                }
            }
        }
        return Arrays.copyOf(keys, count);
    }

    private static long millisOf(long nanos) {
        return Math.round(nanos / NANOS_PER_MILLI);
    }
}
