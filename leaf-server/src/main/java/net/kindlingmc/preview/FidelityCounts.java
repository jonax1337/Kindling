package net.kindlingmc.preview;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What {@link PreviewFidelity} counted when it compared seed previews with the real chunks: columns by height
 * difference, columns by top block (equal, or why not), and sky light above and around the surface. One chunk's
 * comparison is an instance; {@link #add} sums instances up into totals. Not thread safe.
 */
public final class FidelityCounts {
    /** Sky light that differs by this much or more counts as a clearly visible difference. */
    private static final int NOTICEABLE_LIGHT_DIFFERENCE = 2;
    /** Sky light that differs by this much or more counts as a large difference. */
    private static final int LARGE_LIGHT_DIFFERENCE = 5;
    private static final double PERCENT = 100.0;
    private static final double FIGURE_ROUNDING = 100.0;
    /**
     * Columns at least this far from every edge of their chunk are its interior, out of reach of most tree crowns and
     * patches that neighbouring chunks' features spill in; the top block is also counted for them alone.
     */
    private static final int EDGE_WIDTH = 3;
    private static final int LAST_IN_CHUNK = 15;

    private long chunks;
    private final long[] heightColumns = new long[HeightBucket.values().length];
    private long topBlockEqualColumns;
    private final long[] mismatchColumns = new long[TopBlockMismatch.values().length];
    private long interiorColumns;
    private long interiorEqualColumns;
    private final long[] interiorMismatchColumns = new long[TopBlockMismatch.values().length];
    private long aboveSurfaceColumns;
    private long aboveSurfaceEqualColumns;
    private long bandBlocks;
    private long bandDifferenceSum;
    private long bandNoticeableBlocks;
    private long bandLargeBlocks;

    /** Totals of nothing yet; {@link PreviewFidelity#compare} returns the counts of one chunk. */
    public FidelityCounts() {
    }

    /** Adds all of {@code other}'s counts to these. */
    public void add(final FidelityCounts other) {
        this.chunks += other.chunks;
        addAll(this.heightColumns, other.heightColumns);
        this.topBlockEqualColumns += other.topBlockEqualColumns;
        addAll(this.mismatchColumns, other.mismatchColumns);
        this.interiorColumns += other.interiorColumns;
        this.interiorEqualColumns += other.interiorEqualColumns;
        addAll(this.interiorMismatchColumns, other.interiorMismatchColumns);
        this.aboveSurfaceColumns += other.aboveSurfaceColumns;
        this.aboveSurfaceEqualColumns += other.aboveSurfaceEqualColumns;
        this.bandBlocks += other.bandBlocks;
        this.bandDifferenceSum += other.bandDifferenceSum;
        this.bandNoticeableBlocks += other.bandNoticeableBlocks;
        this.bandLargeBlocks += other.bandLargeBlocks;
    }

    private static void addAll(final long[] totals, final long[] more) {
        for (int index = 0; index < totals.length; index++) {
            totals[index] += more[index];
        }
    }

    void recordChunk() {
        this.chunks++;
    }

    /** One column's surface height in the preview minus the real one. */
    void recordHeightDifference(final int previewMinusReal) {
        this.heightColumns[HeightBucket.of(previewMinusReal).ordinal()]++;
    }

    /** @param x the column's x in the chunk, 0 to 15 @param z its z */
    void recordTopBlockEqual(final int x, final int z) {
        this.topBlockEqualColumns++;
        if (isInterior(x, z)) {
            this.interiorColumns++;
            this.interiorEqualColumns++;
        }
    }

    /** @param x the column's x in the chunk, 0 to 15 @param z its z */
    void recordTopBlockMismatch(final TopBlockMismatch mismatch, final int x, final int z) {
        this.mismatchColumns[mismatch.ordinal()]++;
        if (isInterior(x, z)) {
            this.interiorColumns++;
            this.interiorMismatchColumns[mismatch.ordinal()]++;
        }
    }

    private static boolean isInterior(final int x, final int z) {
        return Math.min(Math.min(x, LAST_IN_CHUNK - x), Math.min(z, LAST_IN_CHUNK - z)) >= EDGE_WIDTH;
    }

    /** The sky light of the block right above the real surface, in the real chunk and in the preview. */
    void recordLightAboveSurface(final int real, final int preview) {
        this.aboveSurfaceColumns++;
        if (real == preview) {
            this.aboveSurfaceEqualColumns++;
        }
    }

    /** The sky light of one block of the band around the real surface, in the real chunk and in the preview. */
    void recordLightInBand(final int real, final int preview) {
        final int difference = Math.abs(real - preview);
        this.bandBlocks++;
        this.bandDifferenceSum += difference;
        if (difference >= NOTICEABLE_LIGHT_DIFFERENCE) {
            this.bandNoticeableBlocks++;
        }
        if (difference >= LARGE_LIGHT_DIFFERENCE) {
            this.bandLargeBlocks++;
        }
    }

    long columns() {
        long columns = 0;
        for (final long count : this.heightColumns) {
            columns += count;
        }
        return columns;
    }

    /**
     * The counts as a tree of maps for the bench's JSON: chunks and columns, then per height bucket and per top block
     * category the columns and their percentage of all columns, then the light figures.
     */
    public Map<String, Object> toFigures() {
        final long columns = columns();
        final Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("chunks", this.chunks);
        figures.put("columns", columns);
        figures.put("height", shares(HeightBucket.values(), this.heightColumns, columns));
        figures.put("top_block", topBlockFigures(this.topBlockEqualColumns, this.mismatchColumns, columns));
        figures.put("top_block_interior", topBlockFigures(this.interiorEqualColumns, this.interiorMismatchColumns, this.interiorColumns));
        figures.put("light", lightFigures());
        return figures;
    }

    private static Map<String, Object> topBlockFigures(final long equal, final long[] mismatches, final long columns) {
        final Map<String, Object> topBlock = new LinkedHashMap<>();
        topBlock.put("equal", share(equal, columns));
        topBlock.putAll(shares(TopBlockMismatch.values(), mismatches, columns));
        return topBlock;
    }

    private Map<String, Object> lightFigures() {
        final Map<String, Object> light = new LinkedHashMap<>();
        light.put("above_surface_columns", this.aboveSurfaceColumns);
        light.put("above_surface_equal_percent", percent(this.aboveSurfaceEqualColumns, this.aboveSurfaceColumns));
        light.put("band_blocks", this.bandBlocks);
        light.put("band_mean_abs_difference", rounded(ratio(this.bandDifferenceSum, this.bandBlocks)));
        light.put("band_differ_by_2_plus_percent", percent(this.bandNoticeableBlocks, this.bandBlocks));
        light.put("band_differ_by_5_plus_percent", percent(this.bandLargeBlocks, this.bandBlocks));
        return light;
    }

    private static Map<String, Object> shares(final Enum<?>[] categories, final long[] counts, final long total) {
        final Map<String, Object> shares = new LinkedHashMap<>();
        for (final Enum<?> category : categories) {
            shares.put(category.name().toLowerCase(Locale.ROOT), share(counts[category.ordinal()], total));
        }
        return shares;
    }

    private static Map<String, Object> share(final long count, final long total) {
        final Map<String, Object> share = new LinkedHashMap<>();
        share.put("columns", count);
        share.put("percent", percent(count, total));
        return share;
    }

    private static double percent(final long count, final long total) {
        return rounded(PERCENT * ratio(count, total));
    }

    private static double ratio(final long count, final long total) {
        return total == 0 ? 0 : (double) count / total;
    }

    private static double rounded(final double value) {
        return Math.round(value * FIGURE_ROUNDING) / FIGURE_ROUNDING;
    }
}
