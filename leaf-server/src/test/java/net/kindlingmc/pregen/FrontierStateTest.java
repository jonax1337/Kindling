package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierStateTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path folder;

    @Test
    void aMissingFileIsAnEmptyStateWithoutProblems() {
        final FrontierState state = FrontierState.load(file());

        assertTrue(state.area(OVERWORLD).isEmpty());
        assertTrue(state.problems().isEmpty());
    }

    @Test
    void theAreasOfAllWorldsReadBackAsWritten() throws IOException {
        final FrontierState state = new FrontierState();
        state.put(OVERWORLD, new FrontierState.Area(-5L, TileSetTest.tilesOf(0, 0, -3, 4), TileSetTest.tilesOf(0, 0, 1, 0, 2, 0, -1, -1)));
        state.put(NETHER, new FrontierState.Area(9L, TileSetTest.tilesOf(7, -7), new TileSet()));
        state.save(file());

        final FrontierState read = FrontierState.load(file());

        assertTrue(read.problems().isEmpty(), read.problems().toString());
        final FrontierState.Area overworld = read.area(OVERWORLD).orElseThrow();
        assertEquals(-5L, overworld.seed());
        assertEquals(TileSetTest.tilesOf(0, 0, -3, 4).toText(), overworld.visited().toText());
        assertEquals(TileSetTest.tilesOf(0, 0, 1, 0, 2, 0, -1, -1).toText(), overworld.done().toText());
        final FrontierState.Area nether = read.area(NETHER).orElseThrow();
        assertEquals(9L, nether.seed());
        assertTrue(nether.visited().contains(TileSet.key(7, -7)));
        assertEquals(0, nether.done().size());
    }

    @Test
    void theFileIsReplacedAtomicallyWithoutLeavingATemporaryFile() throws IOException {
        final FrontierState state = new FrontierState();
        state.put(OVERWORLD, new FrontierState.Area(1L, TileSetTest.tilesOf(0, 0), new TileSet()));

        state.save(file());
        state.save(file());

        try (var files = Files.list(this.folder)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void aStateWithoutAnyAreaDeletesTheFile() throws IOException {
        final FrontierState state = new FrontierState();
        state.put(OVERWORLD, new FrontierState.Area(1L, TileSetTest.tilesOf(0, 0), new TileSet()));
        state.save(file());
        state.remove(OVERWORLD);

        state.save(file());

        assertFalse(Files.exists(file()));
    }

    @Test
    void aSnapshotDoesNotFollowLaterChanges() {
        final FrontierState state = new FrontierState();
        state.put(OVERWORLD, new FrontierState.Area(1L, new TileSet(), new TileSet()));

        final FrontierState snapshot = state.snapshot();
        state.remove(OVERWORLD);
        state.put(NETHER, new FrontierState.Area(2L, new TileSet(), new TileSet()));

        assertTrue(snapshot.area(OVERWORLD).isPresent());
        assertTrue(snapshot.area(NETHER).isEmpty());
    }

    @Test
    void anAreaWithoutTilesStillNeedsItsSeed() throws IOException {
        Files.writeString(file(), "minecraft\\:overworld.visited=0:0\n");

        final FrontierState state = FrontierState.load(file());

        assertTrue(state.area(OVERWORLD).isEmpty());
        assertEquals(1, state.problems().size());
        assertTrue(state.problems().get(0).contains("seed is missing"), state.problems().toString());
    }

    @Test
    void aBrokenWorldDoesNotCostTheOthers() throws IOException {
        final FrontierState state = new FrontierState();
        state.put(NETHER, new FrontierState.Area(3L, TileSetTest.tilesOf(1, 1), new TileSet()));
        state.save(file());
        Files.writeString(file(), Files.readString(file()) + "minecraft\\:overworld.seed=1\nminecraft\\:overworld.visited=0:5..2\n");

        final FrontierState read = FrontierState.load(file());

        assertTrue(read.area(NETHER).isPresent());
        assertTrue(read.area(OVERWORLD).isEmpty());
        assertEquals(1, read.problems().size());
        assertTrue(read.problems().get(0).contains("visited"), read.problems().toString());
    }

    @Test
    void unusableFilesAreReportedAndNeverThrow() throws IOException {
        for (final String text : new String[] {"", "   \n", "just text", "other.key=1", "minecraft\\:overworld.seed=x"}) {
            Files.writeString(file(), text);

            final FrontierState state = FrontierState.load(file());

            assertFalse(state.problems().isEmpty(), "no problem reported for '" + text + "'");
            assertTrue(state.area(OVERWORLD).isEmpty());
        }
    }

    @Test
    void theStoreKeepsADamagedFileAndStartsEmpty() throws IOException {
        Files.writeString(file(), "garbage");

        final FrontierStore store = new FrontierStore(file());

        assertTrue(store.state().area(OVERWORLD).isEmpty());
        assertEquals("garbage", Files.readString(this.folder.resolve("pregeneration-frontier.properties.corrupt")));
        store.saveAndClose();
    }

    @Test
    void theStoreWritesWhatWasPutIntoItAndKeepsTheOtherWorlds() throws IOException {
        final FrontierState saved = new FrontierState();
        saved.put(NETHER, new FrontierState.Area(3L, TileSetTest.tilesOf(1, 1), TileSetTest.tilesOf(1, 1)));
        saved.save(file());
        final FrontierStore store = new FrontierStore(file());

        store.state().put(OVERWORLD, new FrontierState.Area(4L, TileSetTest.tilesOf(0, 0), new TileSet()));
        store.saveLater();
        store.saveAndClose();

        final FrontierState read = FrontierState.load(file());
        assertTrue(read.area(NETHER).orElseThrow().done().contains(TileSet.key(1, 1)));
        assertTrue(read.area(OVERWORLD).orElseThrow().visited().contains(TileSet.key(0, 0)));
    }

    private Path file() {
        return this.folder.resolve("pregeneration-frontier.properties");
    }
}
