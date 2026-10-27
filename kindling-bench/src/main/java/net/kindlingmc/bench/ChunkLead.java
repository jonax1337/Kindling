package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * How far ahead of each flying player the server has already sent the world: the lead is the number of chunks along a
 * line from the player, not counting the player's own, that the client has received. A lead of 0 means the next chunk
 * is missing and the player flies into the void; at elytra speed (about 2 chunks a second) a lead of 2 is a second of
 * warning. Also counts the chunks the server generated, which is what keeps the lead up or lets it fall.
 *
 * <p>The lead is measured along five lines (straight ahead and 45 and 90 degrees to either side), each followed chunk
 * by chunk, and the player's own chunk is checked too. The first versions of this class followed one line straight
 * ahead in 16-block steps and never looked at the player's own chunk; those figures are still taken
 * ({@code lead_avg} to {@code close_pct}), so older runs stay comparable.
 */
final class ChunkLead implements Listener {

    private static final int CHUNK_SIZE = 16;
    private static final int TICKS_PER_SECOND = 20;
    private static final int WINDOW_SECONDS = 10;
    private static final int[] RAY_DEGREES = {-90, -45, 0, 45, 90};
    private static final int AHEAD_RAY = 2;
    private static final List<String> TOTAL_FIGURES = List.of("wall_seconds", "own_chunk_missing_pct",
            "worst_ray_lead_avg", "worst_ray_hole_pct", "new_chunks", "new_chunks_per_second");

    private final int sendDistance;
    private final int startTick = Bukkit.getCurrentTick();
    private final LeadHistogram steppedAhead;
    private final LeadHistogram[] rays = new LeadHistogram[RAY_DEGREES.length];
    private final LeadStats total;
    private final List<LeadStats> windows = new ArrayList<>();

    /** @param sendDistance the radius in chunks the server sends; a lead never exceeds it */
    ChunkLead(int sendDistance) {
        this.sendDistance = sendDistance;
        this.steppedAhead = new LeadHistogram(sendDistance);
        this.total = new LeadStats(sendDistance);
        for (int ray = 0; ray < rays.length; ray++) {
            rays[ray] = new LeadHistogram(sendDistance);
        }
        windows.add(new LeadStats(sendDistance));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            total.countNewChunk();
            currentWindow().countNewChunk();
        }
    }

    void sample(Player player, double angle) {
        Location at = player.getLocation();
        steppedAhead.add(steppedLead(player, at, angle));
        boolean ownChunkSent = player.isChunkSent(Chunk.getChunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4));
        int leadAhead = 0;
        int worstLead = sendDistance;
        for (int ray = 0; ray < rays.length; ray++) {
            int lead = ownChunkSent ? leadAlong(player, at, angle + Math.toRadians(RAY_DEGREES[ray])) : 0;
            rays[ray].add(lead);
            worstLead = Math.min(worstLead, lead);
            if (ray == AHEAD_RAY) {
                leadAhead = lead;
            }
        }
        total.record(leadAhead, worstLead, ownChunkSent);
        currentWindow().record(leadAhead, worstLead, ownChunkSent);
    }

    /** The lead as the first versions measured it: one line in 16-block steps, which can skip chunks on a diagonal. */
    private int steppedLead(Player player, Location at, double angle) {
        for (int ahead = 1; ahead <= sendDistance; ahead++) {
            int chunkX = Math.floorDiv((int) Math.floor(at.getX() + Math.cos(angle) * CHUNK_SIZE * ahead), CHUNK_SIZE);
            int chunkZ = Math.floorDiv((int) Math.floor(at.getZ() + Math.sin(angle) * CHUNK_SIZE * ahead), CHUNK_SIZE);
            if (!player.isChunkSent(Chunk.getChunkKey(chunkX, chunkZ))) {
                return ahead - 1;
            }
        }
        return sendDistance;
    }

    private int leadAlong(Player player, Location at, double angle) {
        ChunkRay ray = new ChunkRay(at.getX(), at.getZ(), angle);
        for (int ahead = 0; ahead < sendDistance; ahead++) {
            if (!player.isChunkSent(ray.nextChunkKey())) {
                return ahead;
            }
        }
        return sendDistance;
    }

    /** The window of the flight the current tick falls into; the chunk loads and the samples of a window share it. */
    private LeadStats currentWindow() {
        int index = (Bukkit.getCurrentTick() - startTick) / (WINDOW_SECONDS * TICKS_PER_SECOND);
        while (windows.size() <= index) {
            windows.getLast().close();
            windows.add(new LeadStats(sendDistance));
        }
        return windows.get(index);
    }

    Map<String, Object> summary() {
        total.close();
        windows.getLast().close();
        Map<String, Object> summary = new LinkedHashMap<>(steppedAhead.summary());
        Map<String, Object> totals = total.summary();
        TOTAL_FIGURES.forEach(figure -> summary.put(figure, totals.get(figure)));
        summary.put("rays", raySummaries());
        summary.put("windows", windowSummaries());
        return summary;
    }

    private Map<String, Object> raySummaries() {
        Map<String, Object> summaries = new LinkedHashMap<>();
        for (int ray = 0; ray < rays.length; ray++) {
            summaries.put(Integer.toString(RAY_DEGREES[ray]), rays[ray].summary());
        }
        return summaries;
    }

    private List<Map<String, Object>> windowSummaries() {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (int index = 0; index < windows.size(); index++) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("start_s", index * WINDOW_SECONDS);
            summary.putAll(windows.get(index).summary());
            summaries.add(summary);
        }
        return summaries;
    }
}
