package net.kindlingmc.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeightBucketTest {

    @Test
    void equalHeightsAreEqual() {
        assertEquals(HeightBucket.EQUAL, HeightBucket.of(0));
    }

    @Test
    void oneAndTwoBlocksOffAreTheFirstBucketInEitherDirection() {
        assertEquals(HeightBucket.OFF_BY_1_TO_2, HeightBucket.of(1));
        assertEquals(HeightBucket.OFF_BY_1_TO_2, HeightBucket.of(-2));
    }

    @Test
    void threeToEightBlocksOffAreTheSecondBucketInEitherDirection() {
        assertEquals(HeightBucket.OFF_BY_3_TO_8, HeightBucket.of(3));
        assertEquals(HeightBucket.OFF_BY_3_TO_8, HeightBucket.of(-8));
    }

    @Test
    void moreThanEightBlocksOffAreTheLastBucketInEitherDirection() {
        assertEquals(HeightBucket.OFF_BY_MORE, HeightBucket.of(9));
        assertEquals(HeightBucket.OFF_BY_MORE, HeightBucket.of(-300));
    }
}
