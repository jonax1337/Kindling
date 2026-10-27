package net.kindlingmc.pregen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateWindowTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void hasNoRateBeforeTheSecondSample() {
        final RateWindow window = new RateWindow(5);
        assertEquals(0.0, window.perSecond());
        window.sample(0L, 100L);
        assertEquals(0.0, window.perSecond());
    }

    @Test
    void measuresTheCountGainedPerSecond() {
        final RateWindow window = new RateWindow(5);
        window.sample(0L, 100L);
        window.sample(2 * SECOND, 300L);
        assertEquals(100.0, window.perSecond(), 1e-9);
    }

    @Test
    void forgetsSamplesOlderThanTheWindow() {
        final RateWindow window = new RateWindow(3);
        window.sample(0L, 0L);
        window.sample(SECOND, 1_000L);
        window.sample(2 * SECOND, 1_010L);
        window.sample(3 * SECOND, 1_020L);
        assertEquals(10.0, window.perSecond(), 1e-9);
    }

    @Test
    void dropsToZeroWhileTheCountStands() {
        final RateWindow window = new RateWindow(3);
        window.sample(0L, 0L);
        window.sample(SECOND, 50L);
        window.sample(2 * SECOND, 50L);
        window.sample(3 * SECOND, 50L);
        assertEquals(0.0, window.perSecond());
    }
}
