package previewchunks;

import java.util.Arrays;

/** Order statistics over measured values (linear interpolation between ranks). */
final class Stats {
    private Stats() {
    }

    static double median(double[] values) {
        return percentile(values, 50);
    }

    static double percentile(double[] values, double percent) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double rank = percent / 100 * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(lower + 1, sorted.length - 1);
        return sorted[lower] + (rank - lower) * (sorted[upper] - sorted[lower]);
    }

    static double mean(double[] values) {
        return Arrays.stream(values).average().orElse(Double.NaN);
    }

    /** "p0 / p1 / p50 / p99 / p100" of the values, rounded to one decimal. */
    static String spread(double[] values) {
        return String.format("min %.1f, p1 %.1f, p50 %.1f, p99 %.1f, max %.1f", percentile(values, 0), percentile(values, 1),
            percentile(values, 50), percentile(values, 99), percentile(values, 100));
    }
}
