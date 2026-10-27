package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkGenerationLimitTest {

    @Test
    void aSmallViewDistanceKeepsTheMinimum() {
        assertEquals(5L, automaticAt(2));
    }

    @Test
    void belowTheCapItIsPapersValue() {
        assertEquals(16L, automaticAt(4));
    }

    @Test
    void theFirstViewDistanceAboveTheCapIsCappedExactly() {
        assertEquals(32L, automaticAt(6));
    }

    @Test
    void largerViewDistancesStayAtTheCap() {
        assertEquals(32L, automaticAt(10));
        assertEquals(32L, automaticAt(12));
        assertEquals(32L, automaticAt(32));
    }

    private static long automaticAt(final int viewDistance) {
        final long side = 2L * viewDistance + 1L;
        return ChunkGenerationLimit.automatic(side * side);
    }
}
