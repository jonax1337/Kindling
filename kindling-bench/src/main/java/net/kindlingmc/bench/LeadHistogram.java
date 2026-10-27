package net.kindlingmc.bench;

import java.util.LinkedHashMap;
import java.util.Map;

/** How often each lead (chunks the client already has ahead of the player) was seen, with the figures read off it. */
final class LeadHistogram {

    private static final int CLOSE_LEAD = 2;
    private static final double PERCENT = 100.0;

    private final int[] counts;
    private int samples;

    /** @param maxLead the largest lead a sample can have */
    LeadHistogram(int maxLead) {
        this.counts = new int[maxLead + 1];
    }

    void add(int lead) {
        counts[lead]++;
        samples++;
    }

    int samples() {
        return samples;
    }

    /** Samples, mean, minimum and 5th percentile of the lead, and the shares with no lead (the void) and with at most 2. */
    Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("samples", samples);
        summary.put("lead_avg", Math.round(average() * 100) / 100.0);
        summary.put("lead_min", lowest());
        summary.put("lead_p5", percentile(0.05));
        summary.put("hole_pct", share(0, 0));
        summary.put("close_pct", share(0, CLOSE_LEAD));
        return summary;
    }

    double average() {
        long total = 0;
        for (int lead = 0; lead < counts.length; lead++) {
            total += (long) lead * counts[lead];
        }
        return samples == 0 ? 0 : (double) total / samples;
    }

    double share(int fromLead, int toLead) {
        int within = 0;
        for (int lead = fromLead; lead <= toLead; lead++) {
            within += counts[lead];
        }
        return percent(within, samples);
    }

    /** {@code part} of {@code whole} in percent, to one decimal; 0 for nothing. */
    static double percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(PERCENT * 10 * part / whole) / 10.0;
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
}
