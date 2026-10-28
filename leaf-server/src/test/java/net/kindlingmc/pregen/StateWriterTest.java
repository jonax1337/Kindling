package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        final StateWriter<PregenState> writer = StateWriter.forRuns(file);

        for (long next = 1; next <= 50; next++) {
            writer.writeLater(stateAt(next));
        }
        writer.writeAndClose(stateAt(99L));

        assertEquals(99L, PregenState.load(file).run(WORLD).orElseThrow().next());
    }

    @Test
    void aStateWrittenLaterReachesTheDiskWithoutClosing() throws InterruptedException {
        final Path file = this.folder.resolve("pregeneration.properties");
        final StateWriter<PregenState> writer = StateWriter.forRuns(file);

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
        final StateWriter<PregenState> writer = StateWriter.forRuns(file);
        writer.writeLater(stateAt(1L));

        writer.writeAndClose(new PregenState());

        assertTrue(Files.notExists(file));
    }

    @Test
    void anUnwritableLocationDoesNotThrow() {
        final StateWriter<PregenState> writer = StateWriter.forRuns(this.folder.resolve("missing-folder").resolve("pregeneration.properties"));

        writer.writeAndClose(stateAt(1L));
    }

    @Test
    void theLastStateIsNeverWrittenWhileAnEarlierWriteIsStillInProgress() throws InterruptedException {
        final AtomicInteger writing = new AtomicInteger();
        final AtomicInteger mostAtOnce = new AtomicInteger();
        final List<Long> written = new CopyOnWriteArrayList<>();
        final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        final CountDownLatch firstWriteMayEnd = new CountDownLatch(1);
        final CountDownLatch lastWritten = new CountDownLatch(1);
        final StateWriter<PregenState> writer = new StateWriter<>("pregeneration state", this.folder.resolve("pregeneration.properties"), (state, file) -> {
            mostAtOnce.accumulateAndGet(writing.incrementAndGet(), Math::max);
            final long next = state.run(WORLD).orElseThrow().next();
            written.add(next);
            if (next == 1L) {
                firstWriteStarted.countDown();
                await(firstWriteMayEnd);
            }
            writing.decrementAndGet();
            if (next == 99L) {
                lastWritten.countDown();
            }
        }, 50L);
        writer.writeLater(stateAt(1L));
        assertTrue(firstWriteStarted.await(5, TimeUnit.SECONDS));
        final Thread slowDisk = new Thread(() -> {
            sleep(300L);
            firstWriteMayEnd.countDown();
        });
        slowDisk.start();

        writer.writeAndClose(stateAt(99L));

        assertTrue(lastWritten.await(5, TimeUnit.SECONDS));
        slowDisk.join();
        assertEquals(List.of(1L, 99L), written);
        assertEquals(1, mostAtOnce.get());
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PregenState stateAt(final long next) {
        final PregenState state = new PregenState();
        state.put(WORLD, new PregenState.Run(0, 0, 10, next, PregenState.Phase.RUNNING, OptionalLong.of(1L)));
        return state;
    }
}
