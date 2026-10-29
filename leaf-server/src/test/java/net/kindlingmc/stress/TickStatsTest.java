package net.kindlingmc.stress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickStatsTest {

    @Test
    void averagePeakAndLowestTpsComeFromAllSamples() {
        final TickStats stats = new TickStats();
        stats.add(10, 20);
        stats.add(60, 16.5);
        stats.add(20, 19);

        assertEquals(30, stats.averageMspt(), 1e-9);
        assertEquals(60, stats.peakMspt());
        assertEquals(16.5, stats.lowestTps());
    }

    @Test
    void withoutSamplesEveryFigureIsZero() {
        final TickStats stats = new TickStats();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.averageMspt());
        assertEquals(0, stats.peakMspt());
        assertEquals(0, stats.lowestTps());
    }
}
