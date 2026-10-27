package net.kindlingmc.save;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialWritesTest {

    private static final Duration STALL_INTERVAL = Duration.ofMillis(10);
    private static final long BLOCKED_MILLIS = 200L;
    private static final long FINISH_SECONDS = 10L;
    private static final String PLAYER = "player";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SerialWrites<String> writes = new SerialWrites<>(this.executor, STALL_INTERVAL);
    private final List<String> written = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopExecutor() {
        this.executor.shutdownNow();
    }

    @Test
    void readingWaitsForThePendingWriteLongerThanTheStallInterval() throws InterruptedException {
        final CountDownLatch disk = new CountDownLatch(1);
        this.writes.queue(PLAYER, this.blockedWrite("save", disk));

        final Thread reader = this.startThread(() -> this.writes.awaitWrite(PLAYER));

        assertStillWaiting(reader);
        disk.countDown();
        assertFinishes(reader);
        assertEquals(List.of("save"), this.written);
    }

    @Test
    void anOlderWriteFinishingDoesNotForgetTheNewerOne() throws InterruptedException {
        final CountDownLatch olderDisk = new CountDownLatch(1);
        final CountDownLatch newerDisk = new CountDownLatch(1);
        final CountDownLatch olderDone = new CountDownLatch(1);
        this.writes.queue(PLAYER, this.blockedWrite("older", olderDisk));
        this.writes.queue(() -> olderDone.countDown());
        this.writes.queue(PLAYER, this.blockedWrite("newer", newerDisk));
        olderDisk.countDown();
        assertTrue(olderDone.await(FINISH_SECONDS, TimeUnit.SECONDS));

        final Thread reader = this.startThread(() -> this.writes.awaitWrite(PLAYER));

        assertStillWaiting(reader);
        newerDisk.countDown();
        assertFinishes(reader);
        assertEquals(List.of("older", "newer"), this.written);
    }

    @Test
    void readingAKeyWithoutAPendingWriteDoesNotWait() {
        this.writes.awaitWrite(PLAYER);

        assertTrue(this.written.isEmpty());
    }

    @Test
    void theBarrierWaitsForEveryQueuedWrite() throws InterruptedException {
        final CountDownLatch disk = new CountDownLatch(1);
        this.writes.queue(this.blockedWrite("level", disk));
        this.writes.queue(PLAYER, this.recordingWrite("player"));

        final Thread flush = this.startThread(this.writes::awaitQueuedWrites);

        assertStillWaiting(flush);
        disk.countDown();
        assertFinishes(flush);
        assertEquals(List.of("level", "player"), this.written);
    }

    @Test
    void aWriteQueuedAfterShutdownRunsAfterTheQueuedOnes() throws InterruptedException {
        final CountDownLatch disk = new CountDownLatch(1);
        this.writes.queue(PLAYER, this.blockedWrite("before shutdown", disk));
        this.writes.shutdown(STALL_INTERVAL);

        final Thread lateSave = this.startThread(() -> this.writes.queue(PLAYER, this.recordingWrite("after shutdown")));

        assertStillWaiting(lateSave);
        disk.countDown();
        assertFinishes(lateSave);
        assertEquals(List.of("before shutdown", "after shutdown"), this.written);
    }

    @Test
    void aFailedWriteNeitherBlocksReadersNorLaterWrites() throws InterruptedException {
        this.writes.queue(PLAYER, () -> {
            throw new IllegalStateException("disk full");
        });
        this.writes.queue(PLAYER, this.recordingWrite("retry"));

        final Thread reader = this.startThread(() -> this.writes.awaitWrite(PLAYER));

        assertFinishes(reader);
        assertEquals(List.of("retry"), this.written);
    }

    private Runnable blockedWrite(final String name, final CountDownLatch disk) {
        return () -> {
            try {
                disk.await();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            this.written.add(name);
        };
    }

    private Runnable recordingWrite(final String name) {
        return () -> this.written.add(name);
    }

    private Thread startThread(final Runnable action) {
        final Thread thread = new Thread(action, "SerialWritesTest caller");
        thread.start();
        return thread;
    }

    private static void assertStillWaiting(final Thread caller) throws InterruptedException {
        caller.join(BLOCKED_MILLIS);
        assertTrue(caller.isAlive(), "returned before the write reached the disk");
    }

    private static void assertFinishes(final Thread caller) throws InterruptedException {
        caller.join(TimeUnit.SECONDS.toMillis(FINISH_SECONDS));
        assertFalse(caller.isAlive(), "still waiting after the write reached the disk");
    }
}
