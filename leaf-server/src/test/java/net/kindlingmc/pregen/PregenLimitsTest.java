package net.kindlingmc.pregen;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a run throttles and when it may add chunks in flight. */
class PregenLimitsTest {

    private static final long BUDGET_NANOS = 40_000_000L;
    private static final long CALM_NANOS = 10_000_000L;
    private static final PregenLimits LIMITS = new PregenLimits(BUDGET_NANOS, 32, 0L, 0.10);
    private static final PregenLimits WITHOUT_WAIT_LIMIT = new PregenLimits(BUDGET_NANOS, 32, 0L, 0.0);
    private static final OptionalDouble NOT_MEASURED = OptionalDouble.empty();

    @Test
    void theTickIsTooSlowOnlyAboveTheBudget() {
        assertFalse(LIMITS.tickTooSlow(BUDGET_NANOS));
        assertTrue(LIMITS.tickTooSlow(BUDGET_NANOS + 1));
    }

    @Test
    void theMainThreadWaitsTooLongOnlyAboveTheLimit() {
        assertFalse(LIMITS.mainThreadWaitsTooLong(OptionalDouble.of(0.10)));
        assertTrue(LIMITS.mainThreadWaitsTooLong(OptionalDouble.of(0.11)));
    }

    @Test
    void aWaitThatIsNotMeasuredNeverThrottles() {
        assertFalse(LIMITS.mainThreadWaitsTooLong(NOT_MEASURED));
        assertTrue(LIMITS.relaxed(CALM_NANOS, NOT_MEASURED));
    }

    @Test
    void aLimitOfZeroTurnsTheWaitThrottleOff() {
        assertFalse(WITHOUT_WAIT_LIMIT.mainThreadWaitsTooLong(OptionalDouble.of(0.9)));
        assertTrue(WITHOUT_WAIT_LIMIT.relaxed(CALM_NANOS, OptionalDouble.of(0.9)));
    }

    @Test
    void aRunGrowsOnlyWhileTickTimeAndWaitStayBelowThreeQuartersOfTheirLimits() {
        assertTrue(LIMITS.relaxed(CALM_NANOS, OptionalDouble.of(0.07)));
        assertFalse(LIMITS.relaxed(CALM_NANOS, OptionalDouble.of(0.08)));
        assertFalse(LIMITS.relaxed(BUDGET_NANOS * 3 / 4, OptionalDouble.of(0.0)));
    }
}
