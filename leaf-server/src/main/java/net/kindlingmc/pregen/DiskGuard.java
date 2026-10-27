package net.kindlingmc.pregen;

/**
 * Estimates what a pregeneration still writes: the space the disk lost per delivered chunk since the run (re)started,
 * once enough chunks were delivered to tell; until then an assumed size. Anything else writing to the disk counts
 * against the run, which errs on the safe side.
 */
final class DiskGuard {

    /** A region file stores every chunk in whole sectors of this size. */
    static final long SECTOR_BYTES = 4096L;
    static final long ASSUMED_BYTES_PER_CHUNK = 16 * 1024L;
    static final long CHUNKS_TO_MEASURE = 2048L;

    private long baselineFreeBytes;
    private long baselineChunks;

    DiskGuard(final long freeBytes, final long chunksDelivered) {
        this.rebase(freeBytes, chunksDelivered);
    }

    /** Starts measuring anew, e.g. after a pause during which other programs used the disk. */
    void rebase(final long freeBytes, final long chunksDelivered) {
        this.baselineFreeBytes = freeBytes;
        this.baselineChunks = chunksDelivered;
    }

    DiskForecast forecast(final long freeBytes, final long chunksDelivered, final long chunksLeft, final long reserveBytes) {
        final long sample = chunksDelivered - this.baselineChunks;
        final boolean measured = sample >= CHUNKS_TO_MEASURE && this.baselineFreeBytes > freeBytes;
        final long bytesPerChunk = measured
            ? Math.max(SECTOR_BYTES, (this.baselineFreeBytes - freeBytes) / sample)
            : ASSUMED_BYTES_PER_CHUNK;
        return new DiskForecast(freeBytes, saturatingProduct(bytesPerChunk, chunksLeft), bytesPerChunk, measured, reserveBytes);
    }

    private static long saturatingProduct(final long bytesPerChunk, final long chunks) {
        return chunks > Long.MAX_VALUE / bytesPerChunk ? Long.MAX_VALUE : bytesPerChunk * chunks;
    }
}
