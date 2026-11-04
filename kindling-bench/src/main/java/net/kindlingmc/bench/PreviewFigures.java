package net.kindlingmc.bench;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.kindlingmc.preview.PreviewStats;

/**
 * The preview counters of the server under test, for the result's {@code previews} object. Servers without the preview
 * classes (plain Leaf or Paper) have no such counters: the class is then not found when first used, and the result goes
 * without the figures. Everything that names a preview class stays in this class.
 */
final class PreviewFigures {

    private static final String GROUP = "previews";
    private static final double NANOS_PER_MILLI = 1e6;

    private PreviewFigures() {
    }

    /** Adds the counters as they are now to {@code result.previews.<moment>}, or nothing where the server has none. */
    static void record(RunResult result, String moment) {
        now().ifPresent(figures -> result.putUnder(GROUP, moment, figures));
    }

    private static Optional<Map<String, Object>> now() {
        try {
            return Optional.of(figuresOf(PreviewStats.snapshot()));
        } catch (LinkageError notThere) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> figuresOf(PreviewStats.Snapshot totals) {
        Map<String, Object> figures = new LinkedHashMap<>();
        putComputed(figures, "computed_saved", totals.saved());
        putComputed(figures, "computed_seed", totals.seed());
        putComputed(figures, "computed_seed_cached", totals.seedFromCache());
        figures.put("saved_read_timeouts", totals.savedReadTimeouts());
        figures.put("sent", totals.sent());
        figures.put("sent_raw_mb", round((double) totals.sentRawBytes() / ServerState.BYTES_PER_MB));
        figures.put("dropped_on_arrival", totals.droppedOnArrival());
        figures.put("failed", totals.failed());
        figures.put("kept_on_client", totals.keptOnClient());
        totals.decorationSteps().forEach((step, computed) -> putComputed(figures, "step_" + step.getName(), computed));
        return figures;
    }

    private static void putComputed(Map<String, Object> figures, String name, PreviewStats.Computed computed) {
        figures.put(name, computed.count());
        figures.put(name + "_ms", round(computed.nanos() / NANOS_PER_MILLI));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
