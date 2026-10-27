package net.kindlingmc.pregen;

/**
 * The chunks a world border lets through, as the inclusive chunk ranges {@code firstX..lastX} and {@code firstZ..lastZ}.
 * A chunk counts as inside when its far corner block is, which is the test of {@code WorldBorder#isChunkInBounds}.
 */
record ChunkBorder(int firstX, int lastX, int firstZ, int lastZ) {

    private static final int CHUNK_SHIFT = 4;
    private static final double FAR_CORNER = 15;
    private static final double BLOCKS_PER_CHUNK = 1 << CHUNK_SHIFT;

    /** The border as it spans the blocks from {@code minX} (inclusive) to {@code maxX} (exclusive), likewise in z. */
    static ChunkBorder ofBlocks(final double minX, final double maxX, final double minZ, final double maxZ) {
        return new ChunkBorder(firstChunk(minX), lastChunk(maxX), firstChunk(minZ), lastChunk(maxZ));
    }

    private static int firstChunk(final double minBlock) {
        return (int) Math.ceil((minBlock - FAR_CORNER) / BLOCKS_PER_CHUNK);
    }

    private static int lastChunk(final double maxBlock) {
        return (int) Math.ceil((maxBlock - FAR_CORNER) / BLOCKS_PER_CHUNK) - 1;
    }

    boolean contains(final int chunkX, final int chunkZ) {
        return this.firstX <= chunkX && chunkX <= this.lastX && this.firstZ <= chunkZ && chunkZ <= this.lastZ;
    }

    /** Whether any chunk of the given rectangle is inside. */
    boolean touches(final int minChunkX, final int minChunkZ, final int maxChunkX, final int maxChunkZ) {
        return Math.max(minChunkX, this.firstX) <= Math.min(maxChunkX, this.lastX)
            && Math.max(minChunkZ, this.firstZ) <= Math.min(maxChunkZ, this.lastZ);
    }

    /** The distance in chunks from the given chunk to the farthest chunk inside, along either axis. */
    int reachFrom(final int chunkX, final int chunkZ) {
        final int reachX = Math.max(Math.abs(this.firstX - chunkX), Math.abs(this.lastX - chunkX));
        final int reachZ = Math.max(Math.abs(this.firstZ - chunkZ), Math.abs(this.lastZ - chunkZ));
        return Math.max(reachX, reachZ);
    }
}
