package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static net.kindlingmc.chunk.HostPressure.HIGH;
import static net.kindlingmc.chunk.HostPressure.LOW;
import static net.kindlingmc.chunk.HostPressure.MODERATE;
import static net.kindlingmc.chunk.HostPressure.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GovernorStateTest {

    private static final int FLOOR = 2;
    private static final int CEILING = 5;
    private static final int QUIET_SAMPLES = 12;
    private static final GovernorLimits LIMITS = GovernorLimits.of(FLOOR, CEILING, QUIET_SAMPLES);

    private static GovernorState after(final GovernorState start, final HostPressure... samples) {
        GovernorState state = start;
        for (final HostPressure sample : samples) {
            state = state.next(sample, LIMITS);
        }
        return state;
    }

    private static GovernorState after(final GovernorState start, final int times, final HostPressure sample) {
        GovernorState state = start;
        for (int i = 0; i < times; i++) {
            state = state.next(sample, LIMITS);
        }
        return state;
    }

    private static GovernorState full() {
        return GovernorState.startingAt(CEILING);
    }

    @Test
    void oneSampleOfHighPressureIsNotEnough() {
        assertEquals(CEILING, after(full(), HIGH).workers());
    }

    @Test
    void twoSamplesOfHighPressureInARowTakeOneWorkerAway() {
        assertEquals(CEILING - 1, after(full(), HIGH, HIGH).workers());
    }

    @Test
    void anInterruptedRunOfHighPressureCountsAgain() {
        assertEquals(CEILING, after(full(), HIGH, MODERATE, HIGH).workers());
        assertEquals(CEILING, after(full(), HIGH, UNKNOWN, HIGH).workers());
    }

    @Test
    void lastingPressureTakesOneWorkerPerTwoSamplesDownToTheFloor() {
        assertEquals(CEILING - 2, after(full(), 4, HIGH).workers());
        assertEquals(FLOOR, after(full(), 100, HIGH).workers());
    }

    @Test
    void aQuietPeriodAddsOneWorkerBack() {
        final GovernorState reduced = after(full(), HIGH, HIGH);

        assertEquals(CEILING - 1, after(reduced, QUIET_SAMPLES - 1, LOW).workers());
        assertEquals(CEILING, after(reduced, QUIET_SAMPLES, LOW).workers());
    }

    @Test
    void neverMoreThanTheCeiling() {
        assertEquals(CEILING, after(full(), 1000, LOW).workers());
    }

    @Test
    void anythingButLowStartsTheQuietPeriodOver() {
        final GovernorState reduced = after(full(), HIGH, HIGH);
        final GovernorState interrupted = after(after(reduced, QUIET_SAMPLES - 1, LOW), MODERATE);

        assertEquals(CEILING - 1, after(interrupted, QUIET_SAMPLES - 1, LOW).workers());
        assertEquals(CEILING, after(interrupted, QUIET_SAMPLES, LOW).workers());
    }

    @Test
    void unknownSamplesHoldTheCount() {
        final GovernorState reduced = after(full(), HIGH, HIGH);

        assertEquals(CEILING - 1, after(reduced, 1000, UNKNOWN).workers());
    }

    @Test
    void aWorkerTakenBackWithinTheQuietPeriodDoublesTheNextWait() {
        final GovernorState reduced = after(full(), HIGH, HIGH);
        final GovernorState probed = after(reduced, QUIET_SAMPLES, LOW);
        final GovernorState takenBack = after(probed, HIGH, HIGH);

        assertEquals(CEILING - 1, takenBack.workers());
        assertEquals(CEILING - 1, after(takenBack, 2 * QUIET_SAMPLES - 1, LOW).workers());
        assertEquals(CEILING, after(takenBack, 2 * QUIET_SAMPLES, LOW).workers());
    }

    @Test
    void theWaitDoublesUpToEightQuietPeriods() {
        GovernorState state = after(full(), HIGH, HIGH);
        for (int failedTries = 0; failedTries < 10; failedTries++) {
            state = after(after(state, QUIET_SAMPLES * state.backoff(), LOW), HIGH, HIGH);
        }

        assertEquals(GovernorState.MAX_BACKOFF, state.backoff());
        assertEquals(CEILING - 1, after(state, QUIET_SAMPLES * GovernorState.MAX_BACKOFF - 1, LOW).workers());
        assertEquals(CEILING, after(state, QUIET_SAMPLES * GovernorState.MAX_BACKOFF, LOW).workers());
    }

    @Test
    void aWorkerThatStaysForAQuietPeriodBringsTheWaitBackToNormal() {
        final GovernorState reduced = after(full(), 4, HIGH);
        final GovernorState takenBack = after(after(reduced, QUIET_SAMPLES, LOW), HIGH, HIGH);
        final GovernorState heldAfterBackoff = after(takenBack, 2 * QUIET_SAMPLES, LOW);
        final GovernorState settled = after(heldAfterBackoff, QUIET_SAMPLES, MODERATE);

        assertEquals(1, settled.backoff());
        assertEquals(CEILING, after(settled, QUIET_SAMPLES, LOW).workers());
    }

    @Test
    void pressureLongAfterAStepUpDoesNotDoubleTheWait() {
        final GovernorState reduced = after(full(), 4, HIGH);
        final GovernorState heldStepUp = after(after(reduced, QUIET_SAMPLES, LOW), QUIET_SAMPLES, MODERATE);

        assertEquals(1, after(heldStepUp, HIGH, HIGH).backoff());
    }

    @Test
    void aLowerCeilingAfterAReloadLowersTheCount() {
        final GovernorLimits lower = GovernorLimits.of(FLOOR, 3, QUIET_SAMPLES);

        assertEquals(3, full().next(MODERATE, lower).workers());
    }

    @Test
    void theFloorIsNeverAboveTheCeiling() {
        final GovernorLimits pinned = GovernorLimits.of(4, 2, QUIET_SAMPLES);

        assertEquals(2, pinned.floor());
        assertEquals(2, GovernorState.startingAt(2).next(HIGH, pinned).next(HIGH, pinned).workers());
        assertFalse(pinned.leavesRoom());
    }
}
