package net.kindlingmc.bench;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lead figures of one stretch of the flight: the whole flight or one window of it. Besides the lead straight ahead,
 * it keeps the worst lead of all rays, since a player turning or banking meets the void on the side as well.
 */
final class LeadStats {

    private static final double NANOS_PER_SECOND = 1e9;

    private final LeadHistogram ahead;
    private final LeadHistogram worstRay;
    private final long startNanos = System.nanoTime();
    private long endNanos;
    private int ownChunkMissing;
    private long newChunks;

    LeadStats(int maxLead) {
        this.ahead = new LeadHistogram(maxLead);
        this.worstRay = new LeadHistogram(maxLead);
    }

    void record(int leadAhead, int worstLead, boolean ownChunkSent) {
        ahead.add(leadAhead);
        worstRay.add(worstLead);
        if (!ownChunkSent) {
            ownChunkMissing++;
        }
    }

    void countNewChunk() {
        newChunks++;
    }

    /** Fixes the end of the stretch, which is what its wall time and rates are measured against. */
    void close() {
        endNanos = System.nanoTime();
    }

    double wallSeconds() {
        return (endNanos - startNanos) / NANOS_PER_SECOND;
    }

    double newChunksPerSecond() {
        return Math.round(10.0 * newChunks / wallSeconds()) / 10.0;
    }

    Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("wall_seconds", Math.round(wallSeconds() * 10) / 10.0);
        summary.put("samples", ahead.samples());
        summary.put("lead_avg", Math.round(ahead.average() * 100) / 100.0);
        summary.put("hole_pct", ahead.share(0, 0));
        summary.put("worst_ray_lead_avg", Math.round(worstRay.average() * 100) / 100.0);
        summary.put("worst_ray_hole_pct", worstRay.share(0, 0));
        summary.put("own_chunk_missing_pct", LeadHistogram.percent(ownChunkMissing, ahead.samples()));
        summary.put("new_chunks", newChunks);
        summary.put("new_chunks_per_second", newChunksPerSecond());
        return summary;
    }
}
