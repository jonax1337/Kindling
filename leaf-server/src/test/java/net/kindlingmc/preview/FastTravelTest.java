package net.kindlingmc.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastTravelTest {

    private static final double FAST_ABOVE = 15.0;
    private static final double ELYTRA_BLOCKS_PER_SECOND = 20.0;
    private static final double WALKING_BLOCKS_PER_SECOND = 4.3;

    private final FastTravel travel = new FastTravel();
    private double x;
    private long tick;

    @Test
    void aPlayerJustArrivedIsNotTravellingFast() {
        this.travel.sample(0.0, 0.0, 0, FAST_ABOVE);

        assertFalse(this.travel.isFast());
    }

    @Test
    void secondsInARowAboveTheSpeedAreAFastJourney() {
        this.start();

        this.moveFor(FastTravel.START_SAMPLES, ELYTRA_BLOCKS_PER_SECOND);

        assertTrue(this.travel.isFast());
    }

    @Test
    void aPearlIsNoJourney() {
        this.start();

        this.x += 30.0;
        this.tick += FastTravel.SAMPLE_TICKS;
        this.travel.sample(this.x, 0.0, this.tick, FAST_ABOVE);
        this.moveFor(1, WALKING_BLOCKS_PER_SECOND);

        assertFalse(this.travel.isFast());
    }

    @Test
    void exactlyTheSpeedIsNotFaster() {
        this.start();

        this.moveFor(3, FAST_ABOVE);

        assertFalse(this.travel.isFast());
    }

    @Test
    void walkingIsNoFastJourney() {
        this.start();

        this.moveFor(10, WALKING_BLOCKS_PER_SECOND);

        assertFalse(this.travel.isFast());
    }

    @Test
    void aJourneyEndsOnlyAfterSeveralSlowSeconds() {
        this.start();
        this.moveFor(3, ELYTRA_BLOCKS_PER_SECOND);

        this.moveFor(FastTravel.CALM_SAMPLES - 1, WALKING_BLOCKS_PER_SECOND);
        assertTrue(this.travel.isFast());

        this.moveFor(1, WALKING_BLOCKS_PER_SECOND);
        assertFalse(this.travel.isFast());
    }

    @Test
    void aFastSecondBetweenSlowOnesStartsTheCalmOver() {
        this.start();
        this.moveFor(FastTravel.START_SAMPLES, ELYTRA_BLOCKS_PER_SECOND);
        this.moveFor(FastTravel.CALM_SAMPLES - 1, WALKING_BLOCKS_PER_SECOND);

        this.moveFor(1, ELYTRA_BLOCKS_PER_SECOND);
        this.moveFor(FastTravel.CALM_SAMPLES - 1, WALKING_BLOCKS_PER_SECOND);

        assertTrue(this.travel.isFast());
    }

    @Test
    void aTeleportIsNoJourney() {
        this.start();

        this.teleportAfterOneSecond();

        assertFalse(this.travel.isFast());
    }

    @Test
    void aTeleportEndsAJourney() {
        this.start();
        this.moveFor(2, ELYTRA_BLOCKS_PER_SECOND);

        this.teleportAfterOneSecond();

        assertFalse(this.travel.isFast());
    }

    @Test
    void samplesMissedForAWhileEndAJourney() {
        this.start();
        this.moveFor(2, ELYTRA_BLOCKS_PER_SECOND);

        this.tick += FastTravel.SAMPLE_TICKS * 3L;
        this.x += ELYTRA_BLOCKS_PER_SECOND * 3;
        this.travel.sample(this.x, 0.0, this.tick, FAST_ABOVE);

        assertFalse(this.travel.isFast());
    }

    @Test
    void ticksWithinASecondDoNotDecide() {
        this.start();

        for (int i = 1; i < FastTravel.SAMPLE_TICKS; i++) {
            this.travel.sample(this.x + i * ELYTRA_BLOCKS_PER_SECOND, 0.0, this.tick + i, FAST_ABOVE);
        }

        assertFalse(this.travel.isFast());
    }

    private void start() {
        this.travel.sample(this.x, 0.0, this.tick, FAST_ABOVE);
    }

    /** Moves along x at the given speed, sampled every tick as the chunk loader does. */
    private void moveFor(final int seconds, final double blocksPerSecond) {
        for (int i = 0; i < seconds * FastTravel.SAMPLE_TICKS; i++) {
            this.tick++;
            this.x += blocksPerSecond / FastTravel.SAMPLE_TICKS;
            this.travel.sample(this.x, 0.0, this.tick, FAST_ABOVE);
        }
    }

    private void teleportAfterOneSecond() {
        this.x += FastTravel.TELEPORT_BLOCKS + 1.0;
        this.tick += FastTravel.SAMPLE_TICKS;
        this.travel.sample(this.x, 0.0, this.tick, FAST_ABOVE);
    }
}
