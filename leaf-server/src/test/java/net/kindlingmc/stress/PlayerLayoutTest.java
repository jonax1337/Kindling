package net.kindlingmc.stress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLayoutTest {

    private static final double MIN_DISTANCE = 100;

    @Test
    void playersStartApartSoEachLoadsChunksOfItsOwn() {
        for (int a = 0; a < StressOrder.MAX_PLAYERS; a++) {
            for (int b = a + 1; b < StressOrder.MAX_PLAYERS; b++) {
                final double distance = distance(PlayerLayout.spot(a, 0), PlayerLayout.spot(b, 0));
                assertTrue(distance > MIN_DISTANCE, "players " + a + " and " + b + " are " + distance + " blocks apart");
            }
        }
    }

    @Test
    void noPlayerStartsOnTheCentreWhereTheViewerArrives() {
        final PlayerLayout.Spot first = PlayerLayout.spot(0, 0);

        assertTrue(Math.hypot(first.x(), first.z()) > MIN_DISTANCE);
    }

    @Test
    void anOffsetMovesAPlayerStraightOutwardAlongItsLine() {
        final PlayerLayout.Spot start = PlayerLayout.spot(7, 0);
        final PlayerLayout.Spot later = PlayerLayout.spot(7, 100);

        assertEquals(100, Math.hypot(later.x(), later.z()) - Math.hypot(start.x(), start.z()), 1e-9);
        assertEquals(Math.atan2(start.z(), start.x()), Math.atan2(later.z(), later.x()), 1e-9);
    }

    @Test
    void theOffsetIsReadBackFromAPosition() {
        final PlayerLayout.Spot spot = PlayerLayout.spot(3, 250);

        assertEquals(250, PlayerLayout.offsetAt(3, spot.x(), spot.z()), 1e-9);
    }

    @Test
    void anOffsetReadOutsideTheLineIsKeptWithinIt() {
        assertEquals(0, PlayerLayout.offsetAt(3, 0, 0));
        assertEquals(PlayerLayout.FLIGHT_RANGE, PlayerLayout.offsetAt(3, 1_000_000, 0));
    }

    private static double distance(final PlayerLayout.Spot a, final PlayerLayout.Spot b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
