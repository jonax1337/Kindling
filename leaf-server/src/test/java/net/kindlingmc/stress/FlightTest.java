package net.kindlingmc.stress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightTest {

    @Test
    void aPlayerFliesOutwardOneStepFromWhereItIs() {
        final Flight flight = new Flight();

        assertEquals(Flight.BLOCKS_PER_TICK, flight.next(0));
        assertEquals(10 + Flight.BLOCKS_PER_TICK, flight.next(10));
    }

    @Test
    void aMoveTheServerHeldBackIsNotFollowedByAJump() {
        final Flight flight = new Flight();
        flight.next(40);

        // The server kept the player at 40; the next step starts there again instead of further out.
        assertEquals(40 + Flight.BLOCKS_PER_TICK, flight.next(40));
    }

    @Test
    void atTheRangeThePlayerTurnsAndAtTheStartItTurnsAgain() {
        final Flight flight = new Flight();

        assertEquals(PlayerLayout.FLIGHT_RANGE - Flight.BLOCKS_PER_TICK, flight.next(PlayerLayout.FLIGHT_RANGE));
        assertEquals(500 - Flight.BLOCKS_PER_TICK, flight.next(500));
        assertEquals(Flight.BLOCKS_PER_TICK, flight.next(0));
    }

    @Test
    void theNextOffsetStaysOnTheLine() {
        final Flight flight = new Flight();

        assertEquals(PlayerLayout.FLIGHT_RANGE, flight.next(PlayerLayout.FLIGHT_RANGE - Flight.BLOCKS_PER_TICK / 2));
    }
}
