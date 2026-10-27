package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateWriterTest {

    private static final String WORLD = "minecraft:overworld";

    @TempDir
    Path folder;

    @Test
    void theLastStateIsOnDiskWhenTheWriterIsClosed() {
        final Path file = this.folder.resolve("pregeneration.properties");
        final StateWriter writer = new StateWriter(file);

        for (long next = 1; next <= 50; next++) {
            writer.writeLater(stateAt(next));
        }
        writer.writeAndClose(stateAt(99L));

        assertEquals(99L, PregenState.load(file).run(WORLD).orElseThrow().next());
    }

    @Test
    void aStateWrittenLaterReachesTheDiskWithoutClosing() throws InterruptedException {
        final Path file = this.folder.resolve("pregeneration.properties");
        final StateWriter writer = new StateWriter(file);

        writer.writeLater(stateAt(7L));
        for (int waited = 0; waited < 200 && !Files.exists(file); waited++) {
            Thread.sleep(25);
        }

        assertTrue(Files.exists(file));
        writer.writeAndClose(stateAt(8L));
    }

    @Test
    void closingWithoutAnyRunLeavesNoFile() {
        final Path file = this.folder.resolve("pregeneration.properties");
        final StateWriter writer = new StateWriter(file);
        writer.writeLater(stateAt(1L));

        writer.writeAndClose(new PregenState());

        assertTrue(Files.notExists(file));
    }

    @Test
    void anUnwritableLocationDoesNotThrow() {
        final StateWriter writer = new StateWriter(this.folder.resolve("missing-folder").resolve("pregeneration.properties"));

        writer.writeAndClose(stateAt(1L));
    }

    private static PregenState stateAt(final long next) {
        final PregenState state = new PregenState();
        state.put(WORLD, new PregenState.Run(0, 0, 10, next, PregenState.Phase.RUNNING, OptionalLong.of(1L)));
        return state;
    }
}
