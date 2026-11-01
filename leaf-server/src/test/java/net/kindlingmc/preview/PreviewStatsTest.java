package net.kindlingmc.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The counters are server-wide, so every test compares two snapshots instead of expecting absolute values. */
class PreviewStatsTest {

    private final PreviewStats.Snapshot before = PreviewStats.snapshot();

    @Test
    void aComputedPreviewCountsWithItsTimeForItsSourceOnly() {
        PreviewStats.recordComputed(PreviewStats.Source.SEED, 5_000);
        PreviewStats.recordComputed(PreviewStats.Source.SEED, 7_000);

        final PreviewStats.Snapshot after = PreviewStats.snapshot();

        assertEquals(new PreviewStats.Computed(2, 12_000), minus(after.seed(), this.before.seed()));
        assertEquals(new PreviewStats.Computed(0, 0), minus(after.saved(), this.before.saved()));
        assertEquals(new PreviewStats.Computed(0, 0), minus(after.seedFromCache(), this.before.seedFromCache()));
    }

    @Test
    void everySourceHasItsOwnTotals() {
        PreviewStats.recordComputed(PreviewStats.Source.SAVED, 3);
        PreviewStats.recordComputed(PreviewStats.Source.SEED_FROM_CACHE, 4);

        final PreviewStats.Snapshot after = PreviewStats.snapshot();

        assertEquals(new PreviewStats.Computed(1, 3), minus(after.saved(), this.before.saved()));
        assertEquals(new PreviewStats.Computed(1, 4), minus(after.seedFromCache(), this.before.seedFromCache()));
    }

    @Test
    void sentPreviewsAddUpTheirRawBytes() {
        PreviewStats.recordSent(1_000);
        PreviewStats.recordSent(250);

        final PreviewStats.Snapshot after = PreviewStats.snapshot();

        assertEquals(2, after.sent() - this.before.sent());
        assertEquals(1_250, after.sentRawBytes() - this.before.sentRawBytes());
    }

    @Test
    void theOtherEventsAreCountedOneByOne() {
        PreviewStats.recordSavedReadTimeout();
        PreviewStats.recordDroppedOnArrival();
        PreviewStats.recordDroppedOnArrival();
        PreviewStats.recordFailed();
        PreviewStats.recordKeptOnClient();

        final PreviewStats.Snapshot after = PreviewStats.snapshot();

        assertEquals(1, after.savedReadTimeouts() - this.before.savedReadTimeouts());
        assertEquals(2, after.droppedOnArrival() - this.before.droppedOnArrival());
        assertEquals(1, after.failed() - this.before.failed());
        assertEquals(1, after.keptOnClient() - this.before.keptOnClient());
    }

    @Test
    void theSummaryNamesEveryTotal() {
        final String summary = new PreviewStats.Snapshot(new PreviewStats.Computed(3, 2_000_000), new PreviewStats.Computed(4, 5_000_000),
            new PreviewStats.Computed(5, 0), 6, 7, 3 << 20, 8, 9, 10).toString();

        assertTrue(summary.contains("saved 3 in 2 ms"), summary);
        assertTrue(summary.contains("seed 4 in 5 ms"), summary);
        assertTrue(summary.contains("seed from cache 5 in 0 ms"), summary);
        assertTrue(summary.contains("saved read timeouts 6"), summary);
        assertTrue(summary.contains("sent 7 (3.0 MiB raw)"), summary);
        assertTrue(summary.contains("dropped on arrival 8"), summary);
        assertTrue(summary.contains("failed 9"), summary);
        assertTrue(summary.contains("kept on the client 10"), summary);
    }

    @Test
    void countsFromManyThreadsAreNotLost() throws InterruptedException {
        final int threads = 8;
        final int perThread = 10_000;
        final Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int n = 0; n < perThread; n++) {
                    PreviewStats.recordFailed();
                }
            });
            workers[i].start();
        }
        for (final Thread worker : workers) {
            worker.join();
        }

        assertEquals((long) threads * perThread, PreviewStats.snapshot().failed() - this.before.failed());
    }

    private static PreviewStats.Computed minus(final PreviewStats.Computed later, final PreviewStats.Computed earlier) {
        return new PreviewStats.Computed(later.count() - earlier.count(), later.nanos() - earlier.nanos());
    }
}
