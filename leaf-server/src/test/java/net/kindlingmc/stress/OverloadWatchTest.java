package net.kindlingmc.stress;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverloadWatchTest {

    private static final double OVER = OverloadWatch.LIMIT_MSPT + 1;
    private static final Duration SECOND = Duration.ofSeconds(1);

    @Test
    void anOverloadCountsOnlyAfterTheLimitInARow() {
        final OverloadWatch watch = new OverloadWatch();
        for (long second = 1; second < OverloadWatch.LIMIT.toSeconds(); second++) {
            assertFalse(watch.isOverloaded(OVER, SECOND), "second " + second);
        }
        assertTrue(watch.isOverloaded(OVER, SECOND));
    }

    @Test
    void oneGoodSampleStartsTheCountAgain() {
        final OverloadWatch watch = new OverloadWatch();
        watch.isOverloaded(OVER, OverloadWatch.LIMIT.minus(SECOND));

        assertFalse(watch.isOverloaded(OverloadWatch.LIMIT_MSPT, SECOND));
        assertFalse(watch.isOverloaded(OVER, SECOND));
    }

    @Test
    void slowTicksCountByRealTimeNotBySamples() {
        final OverloadWatch watch = new OverloadWatch();

        assertFalse(watch.isOverloaded(5000, Duration.ofSeconds(15)));
        assertTrue(watch.isOverloaded(5000, Duration.ofSeconds(5)));
    }
}
