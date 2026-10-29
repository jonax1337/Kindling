package net.kindlingmc.stress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressOrderTest {

    @Test
    void withoutArgumentsTheStatusIsMeant() {
        assertEquals(new StressOrder.Status(), StressOrder.parse(args()));
        assertEquals(new StressOrder.Status(), StressOrder.parse(args("status")));
    }

    @Test
    void playersStandUnlessToldToFly() {
        assertEquals(new StressOrder.Players(20, PlayerMotion.STANDING), StressOrder.parse(args("players", "20")));
        assertEquals(new StressOrder.Players(20, PlayerMotion.FLYING), StressOrder.parse(args("players", "20", "fly")));
        assertEquals(new StressOrder.Players(5, PlayerMotion.STANDING), StressOrder.parse(args("PLAYERS", "5", "Stand")));
    }

    @Test
    void mobsBasesAndStopAreRead() {
        assertEquals(new StressOrder.Mobs(500), StressOrder.parse(args("mobs", "500")));
        assertEquals(new StressOrder.Bases(4), StressOrder.parse(args("bases", "4")));
        assertEquals(new StressOrder.Stop(), StressOrder.parse(args("stop")));
    }

    @Test
    void zeroRemovesALoadAndTheLimitsAreAllowed() {
        assertEquals(new StressOrder.Mobs(0), StressOrder.parse(args("mobs", "0")));
        assertEquals(new StressOrder.Players(StressOrder.MAX_PLAYERS, PlayerMotion.STANDING),
            StressOrder.parse(args("players", String.valueOf(StressOrder.MAX_PLAYERS))));
    }

    @Test
    void countsBeyondTheLimitsAreRefused() {
        assertRefused("between 0 and " + StressOrder.MAX_MOBS, "mobs", String.valueOf(StressOrder.MAX_MOBS + 1));
        assertRefused("between 0 and " + StressOrder.MAX_BASES, "bases", "-1");
    }

    @Test
    void aMissingOrWrongCountIsNamed() {
        assertRefused("How many players", "players");
        assertRefused("whole number, not lots", "mobs", "lots");
    }

    @Test
    void unknownWordsAndExtraArgumentsAreRefused() {
        assertRefused("Unknown stress action", "explode");
        assertRefused("stand or fly", "players", "3", "swim");
        assertRefused("Too many arguments", "bases", "1", "2");
        assertRefused("Too many arguments", "stop", "now");
    }

    private static void assertRefused(final String reason, final String... words) {
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> StressOrder.parse(words));
        assertTrue(refused.getMessage().contains(reason), refused.getMessage());
    }

    private static String[] args(final String... words) {
        return words;
    }
}
