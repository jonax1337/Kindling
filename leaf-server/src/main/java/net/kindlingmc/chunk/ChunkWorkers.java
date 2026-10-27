package net.kindlingmc.chunk;

/**
 * The default number of chunk system worker threads. Paper's quarter of the cores cannot keep one elytra flight ahead of
 * world generation; five workers keep eight flying players out of the void on 8 and on 12 cores, and more only add load
 * (docs/MEASUREMENTS.md). Never fewer than Paper's quarter of the cores.
 */
public final class ChunkWorkers {

    private static final int ENOUGH_FOR_FAST_PLAYERS = 5;

    private ChunkWorkers() {
    }

    public static int defaultFor(final int cores) {
        return Math.max(1, Math.max(Math.min(ENOUGH_FOR_FAST_PLAYERS, cores * 5 / 8), cores / 4));
    }
}
