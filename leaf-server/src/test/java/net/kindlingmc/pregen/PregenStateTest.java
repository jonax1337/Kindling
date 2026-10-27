package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PregenStateTest {

    private static final String WORLD = "minecraft:overworld";
    private static final PregenState.Run RUN = new PregenState.Run(-3, 7, 500, 1234L, PregenState.Phase.PAUSED);

    @TempDir
    Path folder;

    @Test
    void aSavedStateIsReadBackUnchanged() throws IOException {
        final PregenState saved = new PregenState();
        saved.put(WORLD, RUN);
        saved.put("minecraft:the_nether", new PregenState.Run(0, 0, 10, 0L, PregenState.Phase.DONE));
        saved.save(file());

        final PregenState loaded = PregenState.load(file());

        assertTrue(loaded.problems().isEmpty());
        assertEquals(RUN, loaded.run(WORLD).orElseThrow());
        assertEquals(PregenState.Phase.DONE, loaded.run("minecraft:the_nether").orElseThrow().phase());
    }

    @Test
    void savingLeavesNoTemporaryFileAndReplacesTheOldOne() throws IOException {
        final PregenState state = new PregenState();
        state.put(WORLD, RUN);
        state.save(file());
        state.put(WORLD, new PregenState.Run(1, 2, 3, 4L, PregenState.Phase.RUNNING));
        state.save(file());

        assertEquals(List.of("pregeneration.properties"), fileNames());
        assertEquals(4L, PregenState.load(file()).run(WORLD).orElseThrow().next());
    }

    @Test
    void aRemovedWorldIsNotWritten() throws IOException {
        final PregenState state = new PregenState();
        state.put(WORLD, RUN);
        state.remove(WORLD);
        state.save(file());

        assertTrue(PregenState.load(file()).run(WORLD).isEmpty());
    }

    @Test
    void aMissingFileIsAnEmptyStateWithoutProblems() {
        final PregenState loaded = PregenState.load(file());

        assertTrue(loaded.problems().isEmpty());
        assertTrue(loaded.run(WORLD).isEmpty());
    }

    @Test
    void anEmptyFileIsAProblem() throws IOException {
        Files.writeString(file(), "");

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.run(WORLD).isEmpty());
    }

    @Test
    void aNumberThatIsNoNumberDropsOnlyThatWorld() throws IOException {
        Files.writeString(file(), run(WORLD, "12x", "5") + run("minecraft:the_end", "3", "9"));

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.problems().get(0).contains(WORLD));
        assertTrue(loaded.run(WORLD).isEmpty());
        assertEquals(9L, loaded.run("minecraft:the_end").orElseThrow().next());
    }

    @Test
    void aNegativeOrHugeNumberIsAProblem() throws IOException {
        Files.writeString(file(), run(WORLD, "-1", "5") + run("a:b", "3", "-9") + run("c:d", "99999999999", "1"));

        final PregenState loaded = PregenState.load(file());

        assertEquals(3, loaded.problems().size());
        assertFalse(loaded.run(WORLD).isPresent() || loaded.run("a:b").isPresent() || loaded.run("c:d").isPresent());
    }

    @Test
    void aFileCutOffBeforeTheLastKeysIsAProblem() throws IOException {
        Files.writeString(file(), escaped(WORLD) + ".center-x=0\n" + escaped(WORLD) + ".center-z=0\n" + escaped(WORLD) + ".radius=5\n");

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.run(WORLD).isEmpty());
    }

    @Test
    void aMissingStateKeyIsAProblem() throws IOException {
        Files.writeString(file(), run(WORLD, "5", "1").replaceAll("(?m)^.*\\.state=.*\\R", ""));

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.run(WORLD).isEmpty());
    }

    @Test
    void anUnknownStateIsAProblem() throws IOException {
        Files.writeString(file(), run(WORLD, "5", "1").replace("running", "exploding"));

        assertEquals(1, PregenState.load(file()).problems().size());
    }

    @Test
    void garbageWithoutAnyRunIsAProblem() throws IOException {
        Files.writeString(file(), "garbage");

        assertFalse(PregenState.load(file()).problems().isEmpty());
    }

    @Test
    void aHeaderOnlyFileIsAProblem() throws IOException {
        Files.writeString(file(), "#Progress of the chunk pregeneration\n#Mon Oct 27 10:00:00 CET 2026\n");

        assertFalse(PregenState.load(file()).problems().isEmpty());
    }

    @Test
    void aFileCutInTheMiddleOfTheFirstKeyIsAProblem() throws IOException {
        Files.writeString(file(), "minecraft\\:overwor");

        assertFalse(PregenState.load(file()).problems().isEmpty());
    }

    @Test
    void aPartlyWrittenSuffixIsAProblemEvenIfTheWorldSurvives() throws IOException {
        Files.writeString(file(), run(WORLD, "5", "1") + escaped(WORLD) + ".stat");

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.run(WORLD).isPresent());
    }

    @Test
    void anUnknownOrderIsAProblem() throws IOException {
        Files.writeString(file(), run(WORLD, "5", "1").replace("tiles-16", "tiles-32"));

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.run(WORLD).isEmpty());
    }

    @Test
    void aStateWithoutRunsDeletesTheFile() throws IOException {
        final PregenState state = new PregenState();
        state.put(WORLD, RUN);
        state.save(file());
        state.remove(WORLD);
        state.save(file());

        assertFalse(Files.exists(file()));
        assertTrue(PregenState.load(file()).problems().isEmpty());
    }

    @Test
    void aFailedWriteLeavesThePreviousFileIntact() throws IOException {
        final PregenState state = new PregenState();
        state.put(WORLD, RUN);
        state.save(file());
        Files.createDirectory(folder.resolve("pregeneration.properties.tmp"));
        state.put(WORLD, new PregenState.Run(1, 2, 3, 4L, PregenState.Phase.RUNNING));

        assertThrows(IOException.class, () -> state.save(file()));

        assertEquals(RUN, PregenState.load(file()).run(WORLD).orElseThrow());
    }

    @Test
    void aBrokenEscapeSequenceIsAProblemInsteadOfAnException() throws IOException {
        Files.writeString(file(), "a=\\uZZZZ\n");

        final PregenState loaded = PregenState.load(file());

        assertEquals(1, loaded.problems().size());
    }

    @Test
    void bytesThatAreNoTextAreAProblemInsteadOfAnException() throws IOException {
        Files.write(file(), new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd});

        assertEquals(1, PregenState.load(file()).problems().size());
    }

    @Test
    void aRunFromTheRingOrderIsConvertedToTheTileOrder() throws IOException {
        final long ringIndex = 100_000L;
        Files.writeString(file(), run(WORLD, "5", Long.toString(ringIndex)).replaceAll("(?m)^.*\\.order=.*\\R", ""));

        final PregenState loaded = PregenState.load(file());

        assertTrue(loaded.problems().isEmpty());
        assertTrue(TileSpiral.fromRingIndex(ringIndex) > 0);
        assertEquals(TileSpiral.fromRingIndex(ringIndex), loaded.run(WORLD).orElseThrow().next());
    }

    @Test
    void theDamagedFileIsKeptNextToTheOriginal() throws IOException {
        Files.writeString(file(), "garbage");

        PregenState.keepCorrupt(file());

        assertEquals("garbage", Files.readString(folder.resolve("pregeneration.properties.corrupt")));
        assertTrue(Files.exists(file()));
    }

    private Path file() {
        return folder.resolve("pregeneration.properties");
    }

    private List<String> fileNames() throws IOException {
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    /** A colon ends a key in a properties file, so the writer escapes it. */
    private static String escaped(final String world) {
        return world.replace(":", "\\:");
    }

    private static String run(final String world, final String radius, final String next) {
        final String key = escaped(world);
        return key + ".center-x=0\n" + key + ".center-z=0\n" + key + ".radius=" + radius + "\n"
            + key + ".next=" + next + "\n" + key + ".order=tiles-16\n" + key + ".state=running\n";
    }
}
