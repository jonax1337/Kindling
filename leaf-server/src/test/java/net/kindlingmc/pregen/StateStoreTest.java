package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    private static final String SAVED_WORLD = "minecraft:the_nether";
    private static final String NEW_WORLD = "minecraft:overworld";
    private static final PregenState.Run SAVED_RUN = new PregenState.Run(1, 2, 300, 4000L, PregenState.Phase.PAUSED, OptionalLong.of(7L));
    private static final PregenState.Run NEW_RUN = new PregenState.Run(0, 0, 50, 0L, PregenState.Phase.RUNNING, OptionalLong.of(8L));

    @TempDir
    Path folder;

    @Test
    void theFileIsNotReadBeforeTheStateIsNeeded() {
        final StateStore store = new StateStore(file());

        assertFalse(store.isLoaded());
        store.state();
        assertTrue(store.isLoaded());
    }

    @Test
    void aRunStartedBeforeAnythingWasReadKeepsTheSavedRunsOfOtherWorlds() throws IOException {
        final PregenState saved = new PregenState();
        saved.put(SAVED_WORLD, SAVED_RUN);
        saved.save(file());
        final StateStore store = new StateStore(file());

        store.state().put(NEW_WORLD, NEW_RUN);
        store.saveAndClose();

        final PregenState written = PregenState.load(file());
        assertEquals(SAVED_RUN, written.run(SAVED_WORLD).orElseThrow());
        assertEquals(NEW_RUN, written.run(NEW_WORLD).orElseThrow());
    }

    @Test
    void aDamagedFileIsKeptAndTheStateStartsEmpty() throws IOException {
        Files.writeString(file(), "garbage");
        final StateStore store = new StateStore(file());

        assertTrue(store.state().run(SAVED_WORLD).isEmpty());

        assertEquals("garbage", Files.readString(this.folder.resolve("pregeneration.properties.corrupt")));
    }

    private Path file() {
        return this.folder.resolve("pregeneration.properties");
    }
}
