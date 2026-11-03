package previewchunks;

/**
 * How a preview's band is sized: blocks above the highest and below the lowest preliminary surface level to start with,
 * the blocks of terrain every column must have inside the band, and the blocks the band grows by when that fails.
 */
record Margins(int above, int below, int skinDepth, int growStep) {
    static final Margins DEFAULT = new Margins(24, 24, 16, 16);
}
