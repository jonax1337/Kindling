package net.kindlingmc.pregen;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Generates every chunk of a square around a centre, tile by tile from the centre outwards ({@link TileSpiral}), at the
 * chunk system's lowest priority: chunks players ask for always come first. Runs with as many chunks in flight as the
 * tick time allows, halving them when the average tick takes longer than the budget and adding one a second while it
 * stays well below. The last tiles stay in memory ({@link KeptTiles}), so their neighbours are not generated halfway,
 * saved and loaded again. Runs on the main thread, where the chunk system also completes full chunk loads.
 */
final class PregenTask {

    private static final int TICKS_PER_SECOND = 20;
    private static final double RELAXED_SHARE_OF_BUDGET = 0.75;

    final ServerLevel level;
    final int centerX;
    final int centerZ;
    final int radius;
    private final long total;
    private final KeptTiles kept;
    private final LongSortedSet inFlight = new LongRBTreeSet();
    private final AtomicLong completed = new AtomicLong();
    private final long startIndex;
    private final long startNanos = System.nanoTime();
    private long next;
    private int parallel = 1;
    private boolean paused;
    private boolean closed;

    /** @param next the {@link TileSpiral} index to continue from, 0 for a new run */
    PregenTask(final ServerLevel level, final int centerX, final int centerZ, final int radius, final long next) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.total = TileSpiral.size(radius);
        this.kept = new KeptTiles(level);
        this.next = Math.min(next, this.total);
        this.startIndex = this.next;
    }

    void pump(final long tick, final long averageTickNanos, final long budgetNanos, final int maxParallel) {
        if (this.paused || this.next >= this.total) {
            return;
        }
        this.kept.releaseBefore(TileSpiral.tileOf(this.resumeIndex()));
        if (averageTickNanos > budgetNanos) {
            this.parallel = Math.max(1, this.parallel / 2);
            return;
        }
        if (tick % TICKS_PER_SECOND == 0 && averageTickNanos < budgetNanos * RELAXED_SHARE_OF_BUDGET) {
            this.parallel = Math.min(maxParallel, this.parallel + 1);
        }
        while (this.inFlight.size() < this.parallel && this.next < this.total) {
            this.schedule(this.next++);
        }
    }

    private void schedule(final long index) {
        final long offset = TileSpiral.offset(index);
        final int chunkX = this.centerX + SquareSpiral.x(offset);
        final int chunkZ = this.centerZ + SquareSpiral.z(offset);
        if (!TileSpiral.isInside(offset, this.radius) || !this.level.getWorldBorder().isChunkInBounds(chunkX, chunkZ)) {
            this.completed.incrementAndGet();
            return;
        }
        this.inFlight.add(index);
        ((ChunkSystemServerLevel) this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
            chunkX, chunkZ, ChunkStatus.FULL, true, Priority.IDLE, chunk -> {
                this.completed.incrementAndGet();
                this.inFlight.remove(index);
                if (chunk != null && !this.closed && !this.paused) {
                    this.kept.keep(TileSpiral.tileOf(index), chunkX, chunkZ);
                }
            });
    }

    /** Lets go of every chunk the task keeps in memory; chunks still in flight complete without being kept. */
    void close() {
        this.closed = true;
        this.kept.releaseAll();
    }

    boolean finished() {
        return this.next >= this.total && this.inFlight.isEmpty();
    }

    /** Where a restart picks up: the oldest chunk still in flight, so none is skipped (later ones load from disk). */
    long resumeIndex() {
        return this.inFlight.isEmpty() ? this.next : this.inFlight.firstLong();
    }

    void pause() {
        this.paused = true;
        this.kept.releaseAll();
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
