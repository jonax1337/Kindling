package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookAheadDirectionTest {

    private static final int VIEW_DISTANCE = 10;
    private static final int LOOK_AHEAD_TICKS = 60;

    private final LookAheadDirection direction = new LookAheadDirection();

    @Test
    void withoutAnySampleTheOrderIsPlainDistance() {
        assertEquals(25.0, this.direction.priority(3, 4));
    }

    @Test
    void theFirstPositionOnlyStartsTheMeasurement() {
        this.update(0, 0, 0);

        assertPlainDistance();
    }

    @Test
    void aSteadyFlightPutsTheChunksAheadFirst() {
        this.fly(1, 5);

        assertTrue(this.direction.priority(5, 0) < this.direction.priority(-5, 0));
        assertTrue(this.direction.priority(5, 0) < 25.0);
        assertTrue(this.direction.priority(-5, 0) > 25.0);
    }

    @Test
    void theOrderAlongOneDirectionStaysByDistance() {
        this.fly(1, 5);

        for (int distance = 1; distance < 15; distance++) {
            assertTrue(this.direction.priority(distance, 0) < this.direction.priority(distance + 1, 0));
            assertTrue(this.direction.priority(-distance, 0) < this.direction.priority(-distance - 1, 0));
        }
    }

    @Test
    void aSlowerFlightBendsTheOrderLessInProportionToTheDistanceCoveredInTheLookAheadTime() {
        this.fly(1, 10);

        // one chunk per 10 ticks over 60 ticks: 6 chunks of 10 view distance, a weight of 0.6
        assertEquals(100.0 * (1 - 0.6), this.direction.priority(10, 0), 1e-9);
    }

    @Test
    void theWeightIsCappedSoSideChunksStillCount() {
        this.fly(1, 5);

        final double ahead = this.direction.priority(10, 0);
        final double behind = this.direction.priority(-10, 0);

        assertEquals(100.0 * (1 - LookAheadDirection.MAX_WEIGHT), ahead, 1e-9);
        assertEquals(100.0 * (1 + LookAheadDirection.MAX_WEIGHT), behind, 1e-9);
    }

    @Test
    void aTeleportDoesNotBendTheOrderAndTheNextFlightStartsFromTheNewPlace() {
        this.update(0, 0, 0);
        this.update(1, 0, 5);

        this.update(500, 500, 10);
        assertPlainDistance();

        this.update(501, 500, 20);
        assertTrue(this.direction.priority(5, 0) < 25.0);
    }

    @Test
    void aJumpWithinTheAlwaysAllowedDistanceCounts() {
        this.update(0, 0, 0);

        this.update(2, 0, 4);

        assertTrue(this.direction.priority(5, 0) < 25.0);
    }

    @Test
    void aJumpFasterThanAnyFlightIsATeleportEvenOverManyTicks() {
        this.update(0, 0, 0);

        this.update(40, 0, 50);

        assertPlainDistance();
    }

    @Test
    void hoveringOnAChunkBorderKeepsTheDirectionAndNeverMakesOneUp() {
        this.update(0, 0, 0);
        this.update(1, 0, 5);
        final double before = this.direction.priority(5, 0);

        this.update(0, 0, 6);
        this.update(1, 0, 7);

        assertEquals(before, this.direction.priority(5, 0));
    }

    @Test
    void hoveringOnAChunkBorderFromTheStartGivesNoDirection() {
        this.update(0, 0, 0);
        this.update(1, 0, 1);
        this.update(0, 0, 2);

        assertPlainDistance();
    }

    @Test
    void aLongPauseForgetsTheDirection() {
        this.update(0, 0, 0);
        this.update(1, 0, 5);

        this.update(2, 0, 5 + LookAheadDirection.STALE_TICKS + 1);

        assertPlainDistance();
    }

    @Test
    void standingStillForAWhileGivesNoDirection() {
        this.update(0, 0, 0);
        this.update(1, 0, 5);

        this.update(1, 0, 20);

        assertPlainDistance();
    }

    @Test
    void withoutLookAheadTicksTheOrderStaysPlainDistance() {
        this.direction.update(0, 0, 0, VIEW_DISTANCE, 0);
        this.direction.update(1, 0, 5, VIEW_DISTANCE, 0);

        assertPlainDistance();
    }

    @Test
    void theDirectionOfTravelDecidesWhichSideComesFirst() {
        this.update(0, 0, 0);
        this.update(0, -1, 5);

        assertTrue(this.direction.priority(0, -5) < this.direction.priority(0, 5));
        assertEquals(this.direction.priority(5, 0), this.direction.priority(-5, 0), 1e-9);
    }

    /** Six chunk crossings along x, one chunk and the given number of ticks apart. */
    private void fly(final int chunksPerCrossing, final int ticksPerCrossing) {
        for (int crossing = 0; crossing < 6; crossing++) {
            this.update(crossing * chunksPerCrossing, 0, (long) crossing * ticksPerCrossing);
        }
    }

    private void update(final int chunkX, final int chunkZ, final long tick) {
        this.direction.update(chunkX, chunkZ, tick, VIEW_DISTANCE, LOOK_AHEAD_TICKS);
    }

    private void assertPlainDistance() {
        assertEquals(25.0, this.direction.priority(3, 4));
        assertEquals(25.0, this.direction.priority(-3, -4));
    }
}
