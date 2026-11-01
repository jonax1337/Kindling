package net.kindlingmc.preview;

/** How far the preview's surface height is from the real chunk's, in the bands {@link FidelityCounts} counts. */
enum HeightBucket {
    EQUAL(0),
    OFF_BY_1_TO_2(2),
    OFF_BY_3_TO_8(8),
    OFF_BY_MORE(Integer.MAX_VALUE);

    private final int largestDifference;

    HeightBucket(final int largestDifference) {
        this.largestDifference = largestDifference;
    }

    /** The bucket of a height difference, in either direction. */
    static HeightBucket of(final int difference) {
        final int distance = Math.abs(difference);
        for (final HeightBucket bucket : values()) {
            if (distance <= bucket.largestDifference) {
                return bucket;
            }
        }
        return OFF_BY_MORE;
    }
}
