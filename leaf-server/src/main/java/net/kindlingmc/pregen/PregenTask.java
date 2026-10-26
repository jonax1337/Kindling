package net.kindlingmc.pregen;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Generates every chunk of a square around a centre, nearest first, at the chunk system's lowest priority: chunks
 * players ask for always come first. Runs with as many chunks in flight as the tick time allows, halving them when the
 * average tick takes longer than the budget and adding one a second while it stays well below.
 */
final class PregenTask {

    private static final int TICKS_PER_SECOND = 20;
    private static final double RELAXED_SHARE_OF_BUDGET = 0.75;

    final ServerLevel level;
    final int centerX;
    final int centerZ;
    final int radius;
    private final long total;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong completed = new AtomicLong();
    private final long startIndex;
    private final long startNanos = System.nanoTime();
    private long next;
    private int parallel = 1;
    private boolean paused;

    /** @param next the index to continue from, 0 for a new run */
    PregenTask(final ServerLevel level, final int centerX, final int centerZ, final int radius, final long next) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.total = SquareSpiral.size(radius);
        this.next = Math.min(next, this.total);
        this.startIndex = this.next;
    }

    void pump(final long tick, final long averageTickNanos, final long budgetNanos, final int maxParallel) {
        if (this.paused || this.next >= this.total) {
            return;
        }
        if (averageTickNanos > budgetNanos) {
            this.parallel = Math.max(1, this.parallel / 2);
            return;
        }
        if (tick % TICKS_PER_SECOND == 0 && averageTickNanos < budgetNanos * RELAXED_SHARE_OF_BUDGET) {
            this.parallel = Math.min(maxParallel, this.parallel + 1);
        }
        while (this.inFlight.get() < this.parallel && this.next < this.total) {
            this.schedule(SquareSpiral.offset(this.next++));
        }
    }

    private void schedule(final long offset) {
        final int chunkX = this.centerX + SquareSpiral.x(offset);
        final int chunkZ = this.centerZ + SquareSpiral.z(offset);
        if (!this.level.getWorldBorder().isChunkInBounds(chunkX, chunkZ)) {
            this.completed.incrementAndGet();
            return;
        }
        this.inFlight.incrementAndGet();
        ((ChunkSystemServerLevel) this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
            chunkX, chunkZ, ChunkStatus.FULL, true, Priority.IDLE, chunk -> {
                this.completed.incrementAndGet();
                this.inFlight.decrementAndGet();
            });
    }

    boolean finished() {
        return this.next >= this.total && this.inFlight.get() == 0;
    }

    /** Where a restart picks up: chunks still in flight are done again (they then load from disk). */
    long resumeIndex() {
        return Math.max(0L, this.next - this.inFlight.get());
    }

    void pause() {
        this.paused = true;
    }

    void resume() {
        this.paused = false;
    }

    boolean paused() {
        return this.paused;
    }

    String describe() {
        final long done = this.startIndex + this.completed.get();
        final double seconds = Math.max(1e-3, (System.nanoTime() - this.startNanos) / 1e9);
        final double perSecond = this.completed.get() / seconds;
        final long left = this.total - done;
        final String eta = perSecond > 0 ? Math.round(left / perSecond / 60) + " min" : "-";
        return String.format("%s: %,d / %,d chunks (%.1f %%), %.0f chunks/s, %d in flight, ETA %s%s",
            this.level.dimension().identifier(), done, this.total, 100.0 * done / this.total, perSecond, this.parallel, eta,
            this.paused ? ", paused" : "");
    }
}
