package net.kindlingmc.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdlePeriodTest {

    private static final long SECOND = 1_000_000_000L;

    private final IdlePeriod period = new IdlePeriod(300 * SECOND);

    @Test
    void reclaimsOnceTheServerHasBeenEmptyLongEnough() {
        assertFalse(period.shouldReclaim(0, false));
        assertFalse(period.shouldReclaim(299 * SECOND, false));
        assertTrue(period.shouldReclaim(300 * SECOND, false));
    }

    @Test
    void reclaimsOnlyOncePerIdlePeriod() {
        period.shouldReclaim(0, false);
        assertTrue(period.shouldReclaim(300 * SECOND, false));
        assertFalse(period.shouldReclaim(301 * SECOND, false));
        assertFalse(period.shouldReclaim(10_000 * SECOND, false));
    }

    @Test
    void neverReclaimsWhilePlayersAreOnline() {
        assertFalse(period.shouldReclaim(0, true));
        assertFalse(period.shouldReclaim(10_000 * SECOND, true));
    }

    @Test
    void aPlayerJoiningRestartsTheCountdown() {
        period.shouldReclaim(0, false);
        assertFalse(period.shouldReclaim(200 * SECOND, true));
        assertFalse(period.shouldReclaim(250 * SECOND, false));
        assertFalse(period.shouldReclaim(549 * SECOND, false));
        assertTrue(period.shouldReclaim(550 * SECOND, false));
    }

    @Test
    void aNewIdlePeriodReclaimsAgain() {
        period.shouldReclaim(0, false);
        assertTrue(period.shouldReclaim(300 * SECOND, false));
        assertFalse(period.shouldReclaim(400 * SECOND, true));
        assertFalse(period.shouldReclaim(500 * SECOND, false));
        assertTrue(period.shouldReclaim(800 * SECOND, false));
    }
}
