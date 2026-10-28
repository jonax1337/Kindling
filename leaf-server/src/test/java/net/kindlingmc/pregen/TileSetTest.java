package net.kindlingmc.pregen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileSetTest {

    @Test
    void chunksAreFlooredIntoTheTileOfTheFixedGrid() {
        assertEquals(TileSet.key(0, 0), TileSet.tileOfChunk(0, 15));
        assertEquals(TileSet.key(1, 0), TileSet.tileOfChunk(16, 0));
        assertEquals(TileSet.key(-1, -1), TileSet.tileOfChunk(-1, -16));
        assertEquals(TileSet.key(-2, -1), TileSet.tileOfChunk(-17, -1));
    }

    @Test
    void aTileKeepsItsCoordinates() {
        final long key = TileSet.key(-5, 7);

        assertEquals(-5, TileSet.x(key));
        assertEquals(7, TileSet.z(key));
    }

    @Test
    void addingATileTwiceCountsItOnce() {
        final TileSet tiles = new TileSet();

        assertTrue(tiles.add(TileSet.key(1, 2)));
        assertFalse(tiles.add(TileSet.key(1, 2)));

        assertEquals(1, tiles.size());
    }

    @Test
    void neighboursInARowAreWrittenAsOneRun() {
        final TileSet tiles = tilesOf(-2, -3, -1, -3, 0, -3, 1, -3, 9, -3, 0, 5);

        assertEquals("-3:-2..1,9;5:0", tiles.toText());
    }

    @Test
    void rowsAreWrittenFromTheLowestAndRunsFromTheLeftWhateverTheOrderOfInsertion() {
        final TileSet tiles = tilesOf(4, 2, -4, 2, 0, -2, -1, 2);

        assertEquals("-2:0;2:-4,-1,4", tiles.toText());
    }

    @Test
    void anEmptySetIsAnEmptyText() {
        assertEquals("", new TileSet().toText());
        assertEquals(0, TileSet.parse("").size());
        assertEquals(0, TileSet.parse("  ").size());
    }

    @Test
    void theTextReadsBackAsTheSameTiles() {
        final TileSet tiles = new TileSet();
        for (int z = -20; z <= 20; z += 3) {
            for (int x = -30; x <= 30; x++) {
                if ((x * 7 + z) % 5 != 0) {
                    tiles.add(TileSet.key(x, z));
                }
            }
        }

        final TileSet read = TileSet.parse(tiles.toText());

        assertEquals(tiles.size(), read.size());
        for (final long tile : tiles.toArray()) {
            assertTrue(read.contains(tile));
        }
        assertEquals(tiles.toText(), read.toText());
    }

    @Test
    void theTilesAtTheEdgeOfAnyWorldBorderSurviveTheText() {
        final TileSet tiles = tilesOf(-TileSet.MAX_COORDINATE, -TileSet.MAX_COORDINATE, TileSet.MAX_COORDINATE, TileSet.MAX_COORDINATE);

        final TileSet read = TileSet.parse(tiles.toText());

        assertTrue(read.contains(TileSet.key(-TileSet.MAX_COORDINATE, -TileSet.MAX_COORDINATE)));
        assertTrue(read.contains(TileSet.key(TileSet.MAX_COORDINATE, TileSet.MAX_COORDINATE)));
    }

    @Test
    void aCopyDoesNotFollowLaterChanges() {
        final TileSet tiles = tilesOf(0, 0);
        final TileSet copy = tiles.copy();

        tiles.add(TileSet.key(1, 1));

        assertEquals(1, copy.size());
    }

    @Test
    void textThatIsNoSetOfTilesIsRefusedWithTheReason() {
        assertRefused("0..3", "has no");
        assertRefused("x:1", "not a tile coordinate");
        assertRefused("1:a", "not a tile coordinate");
        assertRefused("1:5..2", "ends before it starts");
        assertRefused("1:0..9999999", "beyond any world border");
        assertRefused("99999999:0", "beyond any world border");
    }

    private static void assertRefused(final String text, final String reason) {
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> TileSet.parse(text));
        assertTrue(refused.getMessage().contains(reason), refused.getMessage());
    }

    /** The tiles of the given x, z pairs. */
    static TileSet tilesOf(final int... coordinates) {
        final TileSet tiles = new TileSet();
        for (int i = 0; i < coordinates.length; i += 2) {
            tiles.add(TileSet.key(coordinates[i], coordinates[i + 1]));
        }
        return tiles;
    }
}
