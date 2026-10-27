package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkGenerationLimitTest {

    private static final long AUTOMATIC = 0L;
    private static final long UNLIMITED = -1L;

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

    @Test
    void zeroMeansAutomatic() {
        assertEquals(32L, ChunkGenerationLimit.resolve(AUTOMATIC, squareOf(10)));
        assertEquals(16L, ChunkGenerationLimit.resolve(AUTOMATIC, squareOf(4)));
    }

    @Test
    void aNegativeValueMeansUnlimited() {
        assertEquals(Integer.MAX_VALUE, ChunkGenerationLimit.resolve(UNLIMITED, squareOf(10)));
        assertEquals(Integer.MAX_VALUE, ChunkGenerationLimit.resolve(-50L, squareOf(2)));
    }

    @Test
    void anExplicitValueIsKeptEvenAboveTheAutomaticCap() {
        assertEquals(88L, ChunkGenerationLimit.resolve(88L, squareOf(10)));
        assertEquals(1L, ChunkGenerationLimit.resolve(1L, squareOf(10)));
    }

    private static long automaticAt(final int viewDistance) {
        return ChunkGenerationLimit.automatic(squareOf(viewDistance));
    }

    private static long squareOf(final int viewDistance) {
        final long side = 2L * viewDistance + 1L;
        return side * side;
    }
}
