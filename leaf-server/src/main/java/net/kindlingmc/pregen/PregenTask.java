package net.kindlingmc.pregen;

import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import net.kindlingmc.pregen.PregenState.Phase;

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

    private static final int TICKS_PER_SECOND = 20;
    private static final int RATE_WINDOW_SECONDS = 60;

    private final PregenWorld world;
    final int centerX;
    final int centerZ;
    final int radius;
    private final long total;
    private final KeptTiles kept;
    private final DiskWatch disk;
    private final Pacing pacing = new Pacing();
    private final LongSortedSet inFlight = new LongRBTreeSet();
    private final AtomicLong completed = new AtomicLong();
    // chunks the chunk system generated or read from disk; the ones outside the area are only counted as completed
    private final AtomicLong delivered = new AtomicLong();
    private final RateWindow deliveryRate = new RateWindow(RATE_WINDOW_SECONDS + 1);
    private final long startIndex;
    private long next;
    private long inspectedTile = -1;
    private boolean paused;
    private boolean closed;

    /** @param next the {@link TileSpiral} index to continue from, 0 for a new run */
    PregenTask(final PregenWorld world, final int centerX, final int centerZ, final int radius, final long next) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.total = TileSpiral.size(radius);
        this.kept = new KeptTiles(world.tickets());
        this.disk = new DiskWatch(world, "Pregeneration");
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
        if (this.paused || this.disk.isFull() || this.next >= this.total) {
            return;
        }
        this.kept.releaseBefore(TileSpiral.tileOf(this.resumeIndex()));
        final int allowedInFlight = this.pacing.allowedInFlight(tick, averageTickNanos, mainThreadWait, limits);
        if (allowedInFlight > 0) {
            this.scheduleUpTo(allowedInFlight, this.world.border());
        }
    }

    private void scheduleUpTo(final int allowedInFlight, final ChunkBorder border) {
        for (int steps = 0; steps < MAX_STEPS_PER_PUMP && this.next < this.total && this.inFlight.size() < allowedInFlight; steps++) {
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
        return this.closed || this.paused || this.disk.isFull();
    }

    /**
     * For a server that is paused for lack of players and therefore does not pump: looks again at a disk that stopped the
     * run, so the run (and with it the server) wakes up once there is room. Costs nothing for any other task.
     */
    void recheckFullDisk(final long minFreeBytes) {
        if (this.disk.isFull()) {
            this.checkDiskWhenDue(minFreeBytes);
        }
    }

    private void checkDiskWhenDue(final long minFreeBytes) {
        if (this.disk.due()) {
            this.checkDisk(minFreeBytes);
        }
    }

    /** Compares the free space with what the rest of the run needs and stops or continues the run accordingly. */
    void checkDisk(final long minFreeBytes) {
        if (this.disk.check(this.delivered.get(), this.total - this.done(), minFreeBytes)) {
            this.kept.releaseAll();
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
        return !this.paused && !this.disk.isFull() && !this.finished();
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
        this.disk.rebase(this.delivered.get());
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
            this.inFlight.size(), this.pacing.parallel(), eta, this.waitingReason());
    }

    private String waitingReason() {
        if (this.paused) {
            return ", paused";
        }
        if (this.disk.isFull()) {
            return ", paused: the disk would run out";
        }
        return this.pacing.describeThrottle();
    }

    String describeDisk() {
        return this.disk.describe();
    }
}
