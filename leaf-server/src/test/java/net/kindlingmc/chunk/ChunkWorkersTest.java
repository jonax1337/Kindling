package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkWorkersTest {

    @Test
    void aSingleCoreStillGetsAWorker() {
        assertEquals(1, ChunkWorkers.defaultFor(1));
    }

    @Test
    void twoFourAndEightCoresGetFiveEighthsRoundedDownButNotBelowAQuarter() {
        assertEquals(1, ChunkWorkers.defaultFor(2));
        assertEquals(2, ChunkWorkers.defaultFor(4));
        assertEquals(5, ChunkWorkers.defaultFor(8));
    }

    @Test
    void twelveAndSixteenCoresAreCappedAtFive() {
        assertEquals(5, ChunkWorkers.defaultFor(12));
        assertEquals(5, ChunkWorkers.defaultFor(16));
    }

    @Test
    void manyCoresGetAtLeastPapersQuarter() {
        assertEquals(16, ChunkWorkers.defaultFor(64));
        assertEquals(32, ChunkWorkers.defaultFor(128));
    }

    @Test
    void neverFewerThanAQuarterOfTheCoresAndNeverZero() {
        for (int cores = 1; cores <= 256; cores++) {
            final int workers = ChunkWorkers.defaultFor(cores);
            assertTrue(workers >= 1 && workers >= cores / 4, cores + " cores gave " + workers);
        }
    }
}
