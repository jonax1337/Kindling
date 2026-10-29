package net.kindlingmc.spawn;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssistedParallelLoopTest {

    private final AssistedParallelLoop loop = new AssistedParallelLoop(3, "test helper");

    @AfterEach
    void shutDown() {
        this.loop.shutdown();
    }

    @Test
    void runsEveryIndexExactlyOnce() {
        for (int round = 0; round < 200; round++) {
            final AtomicIntegerArray runs = new AtomicIntegerArray(97);
            this.loop.run(runs.length(), runs::incrementAndGet);
            for (int i = 0; i < runs.length(); i++) {
                assertEquals(1, runs.get(i), "index " + i + " in round " + round);
            }
        }
    }

    @Test
    void noTaskRunsAfterRunReturns() {
        for (int round = 0; round < 200; round++) {
            final AtomicBoolean returned = new AtomicBoolean();
            final AtomicBoolean ranLate = new AtomicBoolean();
            this.loop.run(16, index -> {
                if (returned.get()) {
                    ranLate.set(true);
                }
                Thread.onSpinWait();
            });
            returned.set(true);
            assertFalse(ranLate.get(), "round " + round);
        }
    }

    @Test
    void aSingleTaskRunsOnTheCaller() {
        final AtomicReference<Thread> runner = new AtomicReference<>();
        this.loop.run(1, index -> runner.set(Thread.currentThread()));
        assertSame(Thread.currentThread(), runner.get());
    }

    @Test
    void rethrowsTheFirstFailureAfterTheOtherTasksRan() {
        final AtomicIntegerArray runs = new AtomicIntegerArray(10);
        final IllegalStateException failure = new IllegalStateException("task 3");
        final IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> this.loop.run(runs.length(), index -> {
            runs.incrementAndGet(index);
            if (index == 3) {
                throw failure;
            }
        }));
        assertSame(failure, thrown);
        for (int i = 0; i < runs.length(); i++) {
            assertEquals(1, runs.get(i));
        }
    }
}
