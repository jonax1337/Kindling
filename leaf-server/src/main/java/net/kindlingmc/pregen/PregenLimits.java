package net.kindlingmc.pregen;

/** The settings a {@link PregenTask} obeys on every tick. */
record PregenLimits(long budgetNanos, int maxParallel, long minFreeDiskBytes) {
}
