package net.kindlingmc.chunk;

import org.junit.jupiter.api.Test;

import static net.kindlingmc.chunk.DemandState.QUIET_BELOW_WAITING;
import static net.kindlingmc.chunk.DemandState.START_ABOVE_WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DemandStateTest {

    private static final int FLOOR = 2;
    private static final int CEILING = 5;
    private static final int QUIET_SAMPLES = 10;
    private static final GovernorLimits LIMITS = GovernorLimits.of(FLOOR, CEILING, QUIET_SAMPLES);

    private static DemandState after(final DemandState start, final int times, final double averageWaiting) {
        DemandState state = start;
        for (int i = 0; i < times; i++) {
            state = state.next(averageWaiting, LIMITS);
        }
        return state;
    }

    private static DemandState atFloor() {
        return after(DemandState.startingAt(CEILING), QUIET_SAMPLES, 0);
    }

    @Test
    void everyConfiguredWorkerRunsBeforeTheFirstSample() {
        assertEquals(CEILING, DemandState.startingAt(CEILING).workers());
    }

    @Test
    void aQuietPeriodFallsBackToTheFloor() {
        assertEquals(CEILING, after(DemandState.startingAt(CEILING), QUIET_SAMPLES - 1, QUIET_BELOW_WAITING - 0.1).workers());
        assertEquals(FLOOR, atFloor().workers());
    }

    @Test
    void aBacklogAboveTheStartLevelStartsEveryWorkerAtOnce() {
        assertEquals(CEILING, atFloor().next(START_ABOVE_WAITING + 0.1, LIMITS).workers());
    }

    @Test
    void aBacklogUpToTheStartLevelKeepsTheFloor() {
        assertEquals(FLOOR, after(atFloor(), 50, START_ABOVE_WAITING).workers());
    }

    @Test
    void aBusySampleRestartsTheQuietPeriod() {
        final DemandState almostQuiet = after(DemandState.startingAt(CEILING), QUIET_SAMPLES - 1, 0);
        final DemandState busy = almostQuiet.next(QUIET_BELOW_WAITING, LIMITS);
        assertEquals(CEILING, after(busy, QUIET_SAMPLES - 1, 0).workers());
        assertEquals(FLOOR, after(busy, QUIET_SAMPLES, 0).workers());
    }

    @Test
    void theCountFollowsBoundsMovedByAReload() {
        final GovernorLimits fewer = GovernorLimits.of(FLOOR, 3, QUIET_SAMPLES);
        assertEquals(3, DemandState.startingAt(CEILING).next(QUIET_BELOW_WAITING, fewer).workers());
        assertEquals(3, atFloor().next(START_ABOVE_WAITING + 1, fewer).workers());
    }
}
