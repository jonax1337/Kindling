package net.kindlingmc.worldgen.density;

/**
 * Cost model of sub-volume leaf calls: a call costs callCost point evaluations on top of its points, and y-runs in a
 * column separated by at most runGap unneeded points are merged into one call. The default is the one measured on
 * the bench host; tests use the extremes to reach the box and the run plans.
 */
record Tuning(int callCost, int runGap) {
    static final Tuning DEFAULT = new Tuning(8, 1);
}
