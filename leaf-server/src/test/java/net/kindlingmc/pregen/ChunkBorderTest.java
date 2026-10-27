package net.kindlingmc.pregen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkBorderTest {

    /** A border of 10 000 blocks centred on the origin: blocks -5000 up to, not including, 5000. */
    private static final ChunkBorder TEN_THOUSAND = ChunkBorder.ofBlocks(-5000, 5000, -5000, 5000);

    @Test
    void aChunkIsInsideWhenItsFarCornerIs() {
        // chunk 311 spans blocks 4976..4991, chunk 312 spans 4992..5007 and ends beyond the border
        assertTrue(TEN_THOUSAND.contains(311, 0));
        assertFalse(TEN_THOUSAND.contains(312, 0));
        // chunk -313 spans -5008..-4993, its far corner -4993 is inside; chunk -314 ends at -5009
        assertTrue(TEN_THOUSAND.contains(-313, 0));
        assertFalse(TEN_THOUSAND.contains(-314, 0));
    }

    @Test
    void theChunkTestAgreesWithTheBlockTestOfTheWorldBorder() {
        for (int chunk = -330; chunk <= 330; chunk++) {
            final double farCorner = chunk * 16 + 15;
            final boolean vanilla = farCorner >= -5000 && farCorner < 5000;
            assertEquals(vanilla, TEN_THOUSAND.contains(chunk, 0), "chunk " + chunk);
            assertEquals(vanilla, TEN_THOUSAND.contains(0, chunk), "chunk " + chunk);
        }
    }

    @Test
    void theReachIsTheDistanceToTheFarthestChunkInside() {
        assertEquals(313, TEN_THOUSAND.reachFrom(0, 0));
        assertEquals(313 + 100, TEN_THOUSAND.reachFrom(100, 0));
        assertEquals(311 + 50, TEN_THOUSAND.reachFrom(0, -50));
    }

    @Test
    void aRectangleTouchesTheBorderWhenAnyChunkOfItIsInside() {
        assertTrue(TEN_THOUSAND.touches(300, 0, 315, 15));
        assertTrue(TEN_THOUSAND.touches(311, 0, 400, 15));
        assertFalse(TEN_THOUSAND.touches(312, 0, 330, 15));
        assertFalse(TEN_THOUSAND.touches(-400, 400, -380, 420));
    }

    @Test
    void aBorderSmallerThanOneChunkLetsNoChunkThrough() {
        final ChunkBorder tiny = ChunkBorder.ofBlocks(0, 10, 0, 10);

        assertFalse(tiny.contains(0, 0));
    }
}
