package net.kindlingmc.pregen;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeptTilesTest {

    private final FakeWorld world = new FakeWorld();
    private final KeptTiles kept = new KeptTiles(this.world);

    @Test
    void aKeptChunkHoldsATicketUntilItsTileIsReleased() {
        this.kept.keep(0L, 3, -4);

        assertTrue(this.world.tickets.contains(CoordinateUtils.getChunkKey(3, -4)));

        this.kept.releaseAll();

        assertTrue(this.world.tickets.isEmpty());
        assertEquals(0, this.kept.tileCount());
    }

    @Test
    void theCurrentAndThePreviousTileStayAndOlderOnesAreReleased() {
        for (int tile = 0; tile <= 5; tile++) {
            this.kept.keep(tile, tile, 0);
        }

        this.kept.releaseBefore(5L);

        assertEquals(2, this.kept.tileCount());
        assertFalse(this.world.tickets.contains(CoordinateUtils.getChunkKey(3, 0)));
        assertTrue(this.world.tickets.contains(CoordinateUtils.getChunkKey(4, 0)));
        assertTrue(this.world.tickets.contains(CoordinateUtils.getChunkKey(5, 0)));
    }

    @Test
    void releasingBeforeAnEarlyTileKeepsEverything() {
        this.kept.keep(0L, 0, 0);
        this.kept.keep(1L, 1, 0);

        this.kept.releaseBefore(1L);

        assertEquals(2, this.kept.tileCount());
    }

    @Test
    void everyChunkOfAReleasedTileLosesItsTicket() {
        for (int x = 0; x < 16; x++) {
            this.kept.keep(0L, x, 0);
        }
        this.kept.keep(9L, 100, 100);
        assertEquals(17, this.world.tickets.size());

        this.kept.releaseBefore(9L);

        assertEquals(1, this.world.tickets.size());
    }
}
