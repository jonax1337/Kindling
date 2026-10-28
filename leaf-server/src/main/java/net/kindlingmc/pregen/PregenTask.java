package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import net.kindlingmc.pregen.PregenState.Phase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Generates every chunk of a square around a centre, tile by tile from the centre outwards ({@link TileSpiral}), at the
 * chunk system's lowest priority: chunks players ask for always come first. Runs with as many chunks in flight as the
 * tick time allows: while the average tick takes longer than the budget, or the main thread waits too long for a CPU, it
 * halves them and schedules nothing; while both stay well below their limits it adds one a second. It also stops by itself when the disk would run out ({@link DiskGuard}).
 * The last tiles stay in memory ({@link KeptTiles}), so their neighbours are not generated halfway, saved and loaded
 * again. Runs on the main thread, where the chunk system also completes full chunk loads.
 */
final class PregenTask {

    /** The most indices one tick walks, so a vast area outside the border cannot stall the tick. */
    static final int MAX_STEPS_PER_PUMP = 4096;

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int TICKS_PER_SECOND = 20;
    private static final int DISK_CHECK_TICKS = 30 * TICKS_PER_SECOND;
    private static final int RATE_WINDOW_SECONDS = 60;
    private static final double NANOS_PER_MILLI = 1e6;
    private static final double PERCENT = 100;

    private final PregenWorld world;
    final int centerX;
    final int centerZ;
    final int radius;
    private final long total;
    private final KeptTiles kept;
    private final DiskGuard diskGuard;
    private final LongSortedSet inFlight = new LongRBTreeSet();
    private final AtomicLong completed = new AtomicLong();
    // chunks the chunk system generated or read from disk; the ones outside the area are only counted as completed
    private final AtomicLong delivered = new AtomicLong();
    private final RateWindow deliveryRate = new RateWindow(RATE_WINDOW_SECONDS + 1);
    private final long startIndex;
    private long next;
    private long inspectedTile = -1;
    private int parallel = 1;
    private boolean paused;
    private boolean closed;
    private boolean diskFull;
    private int ticksSinceDiskCheck = DISK_CHECK_TICKS;
    private @Nullable DiskForecast forecast;
    private Throttle throttle = Throttle.NONE;
    /** What the throttle measured and the limit it went over, in the unit of its message. */
    private double throttleMeasured;
    private double throttleLimit;

    /** @param next the {@link TileSpiral} index to continue from, 0 for a new run */
    PregenTask(final PregenWorld world, final int centerX, final int centerZ, final int radius, final long next) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.total = TileSpiral.size(radius);
        this.kept = new KeptTiles(world.tickets());
        this.diskGuard = new DiskGuard(world.freeDiskBytes(), 0L);
        this.next = Math.min(next, this.total);
        this.startIndex = this.next;
    }

    PregenWorld world() {
        return this.world;
    }

    /** @param mainThreadWait the main thread's share of waiting for a CPU over the last minute; empty when not measured */
    void pump(final long tick, final long averageTickNanos, final OptionalDouble mainThreadWait, final PregenLimits limits) {
        if (tick % TICKS_PER_SECOND == 0) {
            this.deliveryRate.sample(System.nanoTime(), this.delivered.get());
        }
        this.checkDiskWhenDue(limits.minFreeDiskBytes());
        if (this.paused || this.diskFull || this.next >= this.total) {
            return;
        }
        this.kept.releaseBefore(TileSpiral.tileOf(this.resumeIndex()));
        if (limits.tickTooSlow(averageTickNanos)) {
            this.throttle(Throttle.TICK_TIME, averageTickNanos / NANOS_PER_MILLI, limits.budgetNanos() / NANOS_PER_MILLI);
            return;
        }
        if (limits.mainThreadWaitsTooLong(mainThreadWait)) {
            this.throttle(Throttle.MAIN_THREAD_WAIT, mainThreadWait.getAsDouble() * PERCENT, limits.maxMainThreadWait() * PERCENT);
            return;
        }
        this.throttle = Throttle.NONE;
        if (tick % TICKS_PER_SECOND == 0 && limits.relaxed(averageTickNanos, mainThreadWait)) {
            this.parallel = Math.min(limits.maxParallel(), this.parallel + 1);
        }
        this.scheduleUpTo(this.world.border());
    }

    private void throttle(final Throttle cause, final double measured, final double limit) {
        this.parallel = Math.max(1, this.parallel / 2);
        this.throttle = cause;
        this.throttleMeasured = measured;
        this.throttleLimit = limit;
    }

    private void scheduleUpTo(final ChunkBorder border) {
        for (int steps = 0; steps < MAX_STEPS_PER_PUMP && this.next < this.total && this.inFlight.size() < this.parallel; steps++) {
            this.scheduleNext(border);
        }
    }

    private void scheduleNext(final ChunkBorder border) {
        final long index = this.next;
        if (this.skipTileOutside(index, border)) {
            return;
        }
        this.next++;
        final long offset = TileSpiral.offset(index);
        final int chunkX = this.centerX + SquareSpiral.x(offset);
        final int chunkZ = this.centerZ + SquareSpiral.z(offset);
        if (!TileSpiral.isInside(offset, this.radius) || !border.contains(chunkX, chunkZ)) {
            this.completed.incrementAndGet();
            return;
        }
        this.request(index, chunkX, chunkZ);
    }

    /** Jumps over the rest of the tile of {@code index} if no chunk of it can be inside the border; checks each tile once. */
    private boolean skipTileOutside(final long index, final ChunkBorder border) {
        final long tile = TileSpiral.tileOf(index);
        if (tile == this.inspectedTile) {
            return false;
        }
        this.inspectedTile = tile;
        final long corner = TileSpiral.offset(tile * TileSpiral.TILE_CHUNKS);
        final int minChunkX = this.centerX + SquareSpiral.x(corner);
        final int minChunkZ = this.centerZ + SquareSpiral.z(corner);
        if (border.touches(minChunkX, minChunkZ, minChunkX + TileSpiral.TILE_SIDE - 1, minChunkZ + TileSpiral.TILE_SIDE - 1)) {
            return false;
        }
        final long tileEnd = (tile + 1) * TileSpiral.TILE_CHUNKS;
        this.completed.addAndGet(tileEnd - index);
        this.next = tileEnd;
        return true;
    }

    private void request(final long index, final int chunkX, final int chunkZ) {
        this.inFlight.add(index);
        try {
            this.world.load(chunkX, chunkZ, loaded -> {
                this.completed.incrementAndGet();
                this.delivered.incrementAndGet();
                this.inFlight.remove(index);
                if (loaded && !this.stopped()) {
                    this.kept.keep(TileSpiral.tileOf(index), chunkX, chunkZ);
                }
            });
        } catch (final RuntimeException | Error failure) {
            // no answer will come: take the chunk back, so a run that continues after the failure still gets it
            this.inFlight.remove(index);
            this.next = index;
            throw failure;
        }
    }

    private boolean stopped() {
        return this.closed || this.paused || this.diskFull;
    }

    /**
     * For a server that is paused for lack of players and therefore does not pump: looks again at a disk that stopped the
     * run, so the run (and with it the server) wakes up once there is room. Costs nothing for any other task.
     */
    void recheckFullDisk(final long minFreeBytes) {
        if (this.diskFull) {
            this.checkDiskWhenDue(minFreeBytes);
        }
    }

    private void checkDiskWhenDue(final long minFreeBytes) {
        if (++this.ticksSinceDiskCheck >= DISK_CHECK_TICKS) {
            this.checkDisk(minFreeBytes);
        }
    }

    /** Compares the free space with what the rest of the run needs and stops or continues the run accordingly. */
    void checkDisk(final long minFreeBytes) {
        this.ticksSinceDiskCheck = 0;
        final DiskForecast current = this.diskGuard.forecast(
            this.world.freeDiskBytes(), this.delivered.get(), this.total - this.done(), minFreeBytes);
        this.forecast = current;
        if (current.runsOut() == this.diskFull) {
            return;
        }
        this.diskFull = current.runsOut();
        if (this.diskFull) {
            this.kept.releaseAll();
            LOGGER.warn("Pregeneration of {} stops, the disk would run out: {}", this.world.name(), current.describe());
        } else {
            LOGGER.info("Pregeneration of {} continues, the disk has space again: {}", this.world.name(), current.describe());
        }
    }

    /** Lets go of every chunk the task keeps in memory; chunks still in flight complete without being kept. */
    void close() {
        this.closed = true;
        this.kept.releaseAll();
    }

    boolean finished() {
        return this.next >= this.total && this.inFlight.isEmpty();
    }

    /** Whether the task is working or waiting for the server to be fast enough, and not stopped by anyone or by the disk. */
    boolean active() {
        return !this.paused && !this.diskFull && !this.finished();
    }

    /** Where a restart picks up: the oldest chunk still in flight, so none is skipped (later ones load from disk). */
    long resumeIndex() {
        return this.inFlight.isEmpty() ? this.next : this.inFlight.firstLong();
    }

    /**
     * Where to continue after a crash: {@link #resumeIndex()} stepped back over the tiles kept in memory and the chunks
     * that may be in flight, which the server has not saved yet. Chunks passed again load from disk.
     */
    long crashSafeResumeIndex(final int maxParallel) {
        final long unsaved = ((long) this.kept.tileCount() + 1) * TileSpiral.TILE_CHUNKS + maxParallel;
        return Math.max(0L, this.resumeIndex() - unsaved);
    }

    Phase phase() {
        return this.paused ? Phase.PAUSED : Phase.RUNNING;
    }

    PregenState.Run snapshot(final Phase phase, final long resumeIndex) {
        return new PregenState.Run(this.centerX, this.centerZ, this.radius, resumeIndex, phase, OptionalLong.of(this.world.seed()));
    }

    void pause() {
        this.paused = true;
        this.kept.releaseAll();
    }

    void resume() {
        this.paused = false;
        this.diskGuard.rebase(this.world.freeDiskBytes(), this.delivered.get());
    }

    boolean paused() {
        return this.paused;
    }

    private long done() {
        return this.startIndex + this.completed.get();
    }

    /** {@code radius 312 chunks (4992 blocks) around chunk (12, -5)} */
    String describeArea() {
        return String.format(Locale.ROOT, "radius %d chunks (%d blocks) around chunk (%d, %d)",
            this.radius, this.radius * TileSpiral.BLOCKS_PER_CHUNK, this.centerX, this.centerZ);
    }

    String describe() {
        final long done = this.done();
        final double perSecond = this.deliveryRate.perSecond();
        final long left = this.total - done;
        final String eta = perSecond > 0 ? Math.round(left / perSecond / 60) + " min" : "-";
        return String.format(Locale.ROOT,
            "%s: %,d / %,d chunks (%.1f %%), %.0f chunks/s over the last minute, %d in flight (up to %d), ETA %s%s",
            this.world.name(), done, this.total, 100.0 * done / this.total, perSecond,
            this.inFlight.size(), this.parallel, eta, this.waitingReason());
    }

    private String waitingReason() {
        if (this.paused) {
            return ", paused";
        }
        if (this.diskFull) {
            return ", paused: the disk would run out";
        }
        return this.throttle.describe(this.throttleMeasured, this.throttleLimit);
    }

    String describeDisk() {
        return this.forecast == null ? "disk: not checked yet" : "disk: " + this.forecast.describe();
    }

    /** Why a task schedules nothing for now, as the status line says it. */
    private enum Throttle {
        NONE(""),
        TICK_TIME(", throttled: %.0f ms average > %.0f ms"),
        MAIN_THREAD_WAIT(", throttled: the main thread waited for a CPU %.0f %% of the last minute > %.0f %%");

        private final String format;

        Throttle(final String format) {
            this.format = format;
        }

        String describe(final double measured, final double limit) {
            return this == NONE ? "" : String.format(Locale.ROOT, this.format, measured, limit);
        }
    }
}
