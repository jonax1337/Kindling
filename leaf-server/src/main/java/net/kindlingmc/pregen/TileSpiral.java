package net.kindlingmc.pregen;

/**
 * Numbers the chunks of a square around a centre tile by tile: square tiles of {@value #TILE_SIDE}×{@value #TILE_SIDE}
 * chunks, the tiles ring by ring from the centre tile ({@link SquareSpiral}), the chunks of a tile row by row.
 * Consecutive indices are neighbours, so the half-generated chunks around the chunks just done are still in memory
 * when their turn comes, instead of having been saved and unloaded. The centre tile still comes first.
 *
 * <p>The tiles cover the square of the given radius and a little more; indices of chunks outside it are skipped.
 */
final class TileSpiral {

    static final int TILE_SIDE = 16;
    static final int TILE_CHUNKS = TILE_SIDE * TILE_SIDE;
    static final int BLOCKS_PER_CHUNK = 16;
    /**
     * The largest radius a run can need: the world border spans at most 60 million blocks, so no chunk is farther than
     * 3.75 million chunks from a centre on the opposite edge. Far larger radii overflow {@link #size}.
     */
    static final int MAX_RADIUS = 3_750_000;
    private static final int HALF_TILE = TILE_SIDE / 2;

    private TileSpiral() {
    }

    /** The number of indices covering the square of the given radius in chunks. */
    static long size(final int radius) {
        return SquareSpiral.size(tileRadius(radius)) * TILE_CHUNKS;
    }

    static long tileOf(final long index) {
        return index / TILE_CHUNKS;
    }

    /** The chunk offset from the centre for the given index, packed like {@link SquareSpiral#offset(long)}. */
    static long offset(final long index) {
        final long tile = SquareSpiral.offset(index / TILE_CHUNKS);
        final int inTile = (int) (index % TILE_CHUNKS);
        return SquareSpiral.pack(
            SquareSpiral.x(tile) * TILE_SIDE - HALF_TILE + inTile % TILE_SIDE,
            SquareSpiral.z(tile) * TILE_SIDE - HALF_TILE + inTile / TILE_SIDE);
    }

    static boolean isInside(final long offset, final int radius) {
        return Math.abs(SquareSpiral.x(offset)) <= radius && Math.abs(SquareSpiral.z(offset)) <= radius;
    }

    /**
     * Where to continue a run that was saved with the ring-by-ring order of earlier versions: at the first tile ring that
     * reaches beyond the rings already finished. Chunks generated twice this way only load from disk.
     */
    static long fromRingIndex(final long ringIndex) {
        final int finishedRings = SquareSpiral.ringOf(ringIndex) - 1;
        final int firstOpenTileRing = finishedRings < HALF_TILE ? 0 : (finishedRings - HALF_TILE) / TILE_SIDE + 1;
        final long tilesBefore = firstOpenTileRing == 0 ? 0 : SquareSpiral.size(firstOpenTileRing - 1);
        return tilesBefore * TILE_CHUNKS;
    }

    /** The smallest tile ring whose tiles reach the edge of the square: tile ring k spans -(k·side + side/2) … k·side + side/2 - 1. */
    private static int tileRadius(final int radius) {
        return Math.max(0, Math.ceilDiv(radius - HALF_TILE + 1, TILE_SIDE));
    }
}
