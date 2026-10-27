package net.kindlingmc.chunk;

/**
 * How many chunks one player may have in generation at the same time, from {@code player-max-concurrent-chunk-generates}:
 * 0 is automatic, a negative value is unlimited, any other value is used as it is.
 * <p>
 * Paper's automatic value is a fifth of the chunks in view distance, 88 at view distance 10. That many generations in
 * flight leave the directional chunk ordering nothing to re-sort, so a fast flier outruns the chunks being made and
 * flies into the void. Capping the automatic value keeps the queue short enough to follow the flight direction.
 * Measurements: docs/MEASUREMENTS.md.
 */
public final class ChunkGenerationLimit {

    private static final long AUTOMATIC = 0L;
    private static final long MINIMUM = 5L;
    private static final long MAXIMUM = 32L;
    private static final long SHARE_DENOMINATOR = 5L;

    private ChunkGenerationLimit() {
    }

    /**
     * @param configured the configured value
     * @param radiusChunks the number of chunks in the player's view distance square, {@code (2 * distance + 1)^2}
     */
    public static long resolve(final long configured, final long radiusChunks) {
        if (configured == AUTOMATIC) {
            return automatic(radiusChunks);
        }
        return configured < AUTOMATIC ? Integer.MAX_VALUE : configured;
    }

    static long automatic(final long radiusChunks) {
        return Math.max(MINIMUM, Math.min(radiusChunks / SHARE_DENOMINATOR, MAXIMUM));
    }
}
