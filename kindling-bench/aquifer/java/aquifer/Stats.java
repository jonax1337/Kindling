package aquifer;

import java.util.Arrays;

/** Median, min and max of per-round measurements. */
record Stats(double median, double min, double max) {
    static Stats of(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double median = n % 2 == 1 ? sorted[n / 2] : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
        return new Stats(median, sorted[0], sorted[n - 1]);
    }
}
