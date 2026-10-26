package net.kindlingmc.bench;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * How far ahead of each flying player the server has already sent the world: the lead is the number of chunks in the
 * flight direction, counted from the player's own, that the client has received. A lead of 0 means the next chunk is
 * missing and the player flies into the void; at elytra speed (about 2 chunks a second) a lead of 2 is a second of warning.
 * Also counts the chunks the server generated, which is what keeps the lead up or lets it fall.
 */
final class ChunkLead implements Listener {

    private static final int CHUNK_SIZE = 16;
    private static final int CLOSE_LEAD = 2;
    private static final double PERCENT = 100.0;

    private final int[] counts;
    private int samples;
    private long newChunks;

    /** @param sendDistance the radius in chunks the server sends; a lead never exceeds it */
    ChunkLead(int sendDistance) {
        this.counts = new int[sendDistance + 1];
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            newChunks++;
        }
    }

    void sample(Player player, double angle) {
        counts[leadOf(player, angle)]++;
        samples++;
    }

    private int leadOf(Player player, double angle) {
        Location at = player.getLocation();
        for (int ahead = 1; ahead < counts.length; ahead++) {
            int chunkX = Math.floorDiv((int) Math.floor(at.getX() + Math.cos(angle) * CHUNK_SIZE * ahead), CHUNK_SIZE);
            int chunkZ = Math.floorDiv((int) Math.floor(at.getZ() + Math.sin(angle) * CHUNK_SIZE * ahead), CHUNK_SIZE);
            if (!player.isChunkSent(Chunk.getChunkKey(chunkX, chunkZ))) {
                return ahead - 1;
            }
        }
        return counts.length - 1;
    }

    Map<String, Object> summary(int seconds) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("samples", samples);
        summary.put("lead_avg", Math.round(average() * 100) / 100.0);
        summary.put("lead_min", lowest());
        summary.put("lead_p5", percentile(0.05));
        summary.put("hole_pct", share(0, 0));
        summary.put("close_pct", share(0, CLOSE_LEAD));
        summary.put("new_chunks", newChunks);
        summary.put("new_chunks_per_second", Math.round(10.0 * newChunks / seconds) / 10.0);
        return summary;
    }

    private double average() {
        long total = 0;
        for (int lead = 0; lead < counts.length; lead++) {
            total += (long) lead * counts[lead];
        }
        return samples == 0 ? 0 : (double) total / samples;
    }

    private int lowest() {
        for (int lead = 0; lead < counts.length; lead++) {
            if (counts[lead] > 0) {
                return lead;
            }
        }
        return counts.length - 1;
    }

    private int percentile(double fraction) {
        int seen = 0;
        for (int lead = 0; lead < counts.length; lead++) {
            seen += counts[lead];
            if (seen > fraction * samples) {
                return lead;
            }
        }
        return counts.length - 1;
    }

    private double share(int fromLead, int toLead) {
        int within = 0;
        for (int lead = fromLead; lead <= toLead; lead++) {
            within += counts[lead];
        }
        return samples == 0 ? 0 : Math.round(PERCENT * 10 * within / samples) / 10.0;
    }
}
