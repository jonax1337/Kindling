package densitycompiler.eval;

/**
 * Cost model of sub-volume leaf calls: a call costs callCost point evaluations on top of its points, and y-runs in a
 * column separated by at most runGap unneeded points are merged into one call.
 */
public record Tuning(int callCost, int runGap) {
    public static final Tuning DEFAULT = new Tuning(8, 1);
}
