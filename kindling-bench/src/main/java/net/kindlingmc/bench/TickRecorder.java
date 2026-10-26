package net.kindlingmc.bench;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Records how long each server tick took, per measured phase; ticks outside a phase are not kept. */
final class TickRecorder implements Listener {

    private static final int INITIAL_CAPACITY = 4096;

    private double[] durations = new double[INITIAL_CAPACITY];
    private int count;
    private boolean recording;

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        if (!recording) {
            return;
        }
        if (count == durations.length) {
            durations = Arrays.copyOf(durations, count * 2);
        }
        durations[count++] = event.getTickDuration();
    }

    void start() {
        count = 0;
        recording = true;
    }

    /** Stops recording and summarises the ticks since {@link #start()} in milliseconds. */
    Map<String, Object> stop() {
        recording = false;
        double[] sorted = Arrays.copyOf(durations, count);
        Arrays.sort(sorted);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticks", count);
        summary.put("mspt_avg", round(Arrays.stream(sorted).average().orElse(0)));
        summary.put("mspt_p50", round(percentile(sorted, 0.50)));
        summary.put("mspt_p95", round(percentile(sorted, 0.95)));
        summary.put("mspt_p99", round(percentile(sorted, 0.99)));
        summary.put("mspt_max", round(count == 0 ? 0 : sorted[count - 1]));
        summary.put("ticks_over_50ms", Arrays.stream(sorted).filter(duration -> duration > 50).count());
        return summary;
    }

    private static double percentile(double[] sorted, double fraction) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.min(sorted.length - 1, Math.floor(fraction * sorted.length))];
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
