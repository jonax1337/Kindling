package net.kindlingmc.preview;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Counting, summing up chunks and the figures written to the bench result. */
class FidelityCountsTest {
    private static final double DELTA = 1e-9;
    private static final int EDGE = 0;
    private static final int NEAR_EDGE = 2;
    private static final int INTERIOR = 3;

    private final FidelityCounts counts = new FidelityCounts();

    @Test
    void onlyColumnsThreeOrMoreFromEveryEdgeCountAsInterior() {
        this.counts.recordTopBlockEqual(INTERIOR, 15 - INTERIOR);
        this.counts.recordTopBlockMismatch(TopBlockMismatch.TREE_MISSING, INTERIOR, INTERIOR);
        this.counts.recordTopBlockMismatch(TopBlockMismatch.TREE_MISSING, NEAR_EDGE, INTERIOR);
        this.counts.recordTopBlockMismatch(TopBlockMismatch.TREE_MISSING, INTERIOR, 15 - NEAR_EDGE);

        Map<String, Object> interior = group(this.counts.toFigures(), "top_block_interior");

        assertEquals(50.0, percentOf(interior, "equal"), DELTA);
        assertEquals(50.0, percentOf(interior, "tree_missing"), DELTA);
    }

    @Test
    void nothingCountedGivesZerosInsteadOfDividingByZero() {
        Map<String, Object> figures = this.counts.toFigures();

        assertEquals(0L, figures.get("columns"));
        assertEquals(0.0, percentOf(group(figures, "height"), "equal"), DELTA);
        assertEquals(0.0, group(figures, "light").get("band_mean_abs_difference"));
    }

    @Test
    void columnsAreCountedPerHeightBucketWithTheirShareOfAllColumns() {
        this.counts.recordHeightDifference(0);
        this.counts.recordHeightDifference(0);
        this.counts.recordHeightDifference(-1);
        this.counts.recordHeightDifference(12);

        Map<String, Object> figures = this.counts.toFigures();

        assertEquals(4L, figures.get("columns"));
        assertEquals(50.0, percentOf(group(figures, "height"), "equal"), DELTA);
        assertEquals(25.0, percentOf(group(figures, "height"), "off_by_1_to_2"), DELTA);
        assertEquals(0.0, percentOf(group(figures, "height"), "off_by_3_to_8"), DELTA);
        assertEquals(25.0, percentOf(group(figures, "height"), "off_by_more"), DELTA);
    }

    @Test
    void topBlocksAreCountedAsEqualOrByTheirMismatchCategory() {
        for (int column = 0; column < 4; column++) {
            this.counts.recordHeightDifference(0);
        }
        this.counts.recordTopBlockEqual(EDGE, EDGE);
        this.counts.recordTopBlockEqual(EDGE, EDGE);
        this.counts.recordTopBlockMismatch(TopBlockMismatch.TREE_MISSING, EDGE, EDGE);
        this.counts.recordTopBlockMismatch(TopBlockMismatch.SNOW_ICE, EDGE, EDGE);

        Map<String, Object> topBlock = group(this.counts.toFigures(), "top_block");

        assertEquals(50.0, percentOf(topBlock, "equal"), DELTA);
        assertEquals(25.0, percentOf(topBlock, "tree_missing"), DELTA);
        assertEquals(25.0, percentOf(topBlock, "snow_ice"), DELTA);
        assertEquals(0.0, percentOf(topBlock, "water"), DELTA);
    }

    @Test
    void lightAboveTheSurfaceCountsHowOftenItIsEqual() {
        this.counts.recordLightAboveSurface(15, 15);
        this.counts.recordLightAboveSurface(15, 12);

        Map<String, Object> light = group(this.counts.toFigures(), "light");

        assertEquals(2L, light.get("above_surface_columns"));
        assertEquals(50.0, light.get("above_surface_equal_percent"));
    }

    @Test
    void lightInTheBandGivesTheMeanDifferenceAndTheSharesAtTwoAndFive() {
        this.counts.recordLightInBand(15, 15);
        this.counts.recordLightInBand(15, 14);
        this.counts.recordLightInBand(10, 13);
        this.counts.recordLightInBand(0, 8);

        Map<String, Object> light = group(this.counts.toFigures(), "light");

        assertEquals(4L, light.get("band_blocks"));
        assertEquals(3.0, light.get("band_mean_abs_difference"));
        assertEquals(50.0, light.get("band_differ_by_2_plus_percent"));
        assertEquals(25.0, light.get("band_differ_by_5_plus_percent"));
    }

    @Test
    void addingSumsEveryCountAndChunk() {
        FidelityCounts other = new FidelityCounts();
        other.recordChunk();
        other.recordHeightDifference(0);
        other.recordTopBlockMismatch(TopBlockMismatch.PLANT, EDGE, EDGE);
        other.recordLightAboveSurface(15, 15);
        other.recordLightInBand(15, 10);
        this.counts.recordChunk();
        this.counts.recordHeightDifference(5);
        this.counts.recordTopBlockEqual(EDGE, EDGE);
        this.counts.recordLightAboveSurface(15, 3);
        this.counts.recordLightInBand(15, 15);

        this.counts.add(other);
        Map<String, Object> figures = this.counts.toFigures();

        assertEquals(2L, figures.get("chunks"));
        assertEquals(2L, figures.get("columns"));
        assertEquals(50.0, percentOf(group(figures, "height"), "equal"), DELTA);
        assertEquals(50.0, percentOf(group(figures, "top_block"), "equal"), DELTA);
        assertEquals(50.0, percentOf(group(figures, "top_block"), "plant"), DELTA);
        assertEquals(50.0, group(figures, "light").get("above_surface_equal_percent"));
        assertEquals(2.5, group(figures, "light").get("band_mean_abs_difference"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> group(Map<String, Object> figures, String name) {
        return (Map<String, Object>) figures.get(name);
    }

    private static double percentOf(Map<String, Object> group, String category) {
        return (double) group(group, category).get("percent");
    }
}
