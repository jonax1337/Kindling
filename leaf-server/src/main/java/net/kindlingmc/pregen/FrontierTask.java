package net.kindlingmc.pregen;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * Generates the tiles a {@link FrontierPlan} offers, one at a time, nearest to a visited tile first, at the chunk
 * system's lowest priority like {@link PregenTask}: the same pacing ({@link Pacing}), disk guard ({@link DiskWatch}) and
 * kept tiles ({@link KeptTiles}). A tile is marked done once all its chunks inside the border were delivered; a task
 * that is paused or closed leaves the tile open, and the chunks that were requested already finish on their own.
 * Runs on the main thread, where the chunk system also completes full chunk loads.
 */
final class FrontierTask {

    private static final int TICKS_PER_SECOND = 20;
    private static final int RATE_WINDOW_SECONDS = 60;
    private static final int TILE_SIDE = TileSpiral.TILE_SIDE;

    private final PregenWorld world;
    private final FrontierPlan plan;
    private final KeptTiles kept;
    private final DiskWatch disk;
    private final Pacing pacing = new Pacing();
    private final RateWindow deliveryRate = new RateWindow(RATE_WINDOW_SECONDS + 1);
    private long delivered;
    private int inFlight;
    private boolean working;
    private long tile;
    private int nextChunk;
    private long tileSequence;
    private boolean exhausted;
    private boolean paused;
    private boolean closed;

    FrontierTask(final PregenWorld world, final FrontierPlan plan) {
        this.world = world;
        this.plan = plan;
        this.kept = new KeptTiles(world.tickets());
        this.disk = new DiskWatch(world, "Frontier pregeneration");
    }

    void pump(final TickLoad load, final PregenLimits limits) {
        if (load.tick() % TICKS_PER_SECOND == 0) {
            this.deliveryRate.sample(System.nanoTime(), this.delivered);
        }
        this.checkDiskWhenDue(limits.minFreeDiskBytes());
        if (this.paused || this.disk.isFull()) {
            return;
        }
        this.finishTileWhenDelivered();
        this.kept.releaseBefore(this.tileSequence);
        final int allowedInFlight = this.pacing.allowedInFlight(load.tick(), load.averageTickNanos(), load.mainThreadWait(), limits);
        if (allowedInFlight > 0) {
            this.scheduleUpTo(allowedInFlight, this.world.border());
        }
    }

    private void finishTileWhenDelivered() {
        if (this.working && this.nextChunk == TileSpiral.TILE_CHUNKS && this.inFlight == 0) {
            this.plan.markDone(this.tile);
            this.working = false;
        }
    }

    private void scheduleUpTo(final int allowedInFlight, final ChunkBorder border) {
        while (this.inFlight < allowedInFlight) {
            if (!this.working && !this.beginNextTile(border)) {
                this.exhausted = true;
                return;
            }
            if (this.nextChunk == TileSpiral.TILE_CHUNKS) {
                return;
            }
            this.scheduleNextChunk(border);
        }
    }

    private boolean beginNextTile(final ChunkBorder border) {
        final OptionalLong nearest = this.plan.nearest(border);
        if (nearest.isEmpty()) {
            return false;
        }
        this.tile = nearest.getAsLong();
        this.nextChunk = 0;
        this.working = true;
        this.tileSequence++;
        return true;
    }

    private void scheduleNextChunk(final ChunkBorder border) {
        final int index = this.nextChunk++;
        final int chunkX = TileSet.x(this.tile) * TILE_SIDE + index % TILE_SIDE;
        final int chunkZ = TileSet.z(this.tile) * TILE_SIDE + index / TILE_SIDE;
        if (border.contains(chunkX, chunkZ)) {
            this.request(chunkX, chunkZ);
        }
    }

    private void request(final int chunkX, final int chunkZ) {
        final long sequence = this.tileSequence;
        this.inFlight++;
        try {
            this.world.load(chunkX, chunkZ, loaded -> {
                this.inFlight--;
                this.delivered++;
                if (loaded && !this.stopped()) {
                    this.kept.keep(sequence, chunkX, chunkZ);
                }
            });
        } catch (final RuntimeException | Error failure) {
            // no answer will come: take the chunk back, so a task that continues after the failure still gets it
            this.inFlight--;
            this.nextChunk--;
            throw failure;
        }
    }

    private boolean stopped() {
        return this.closed || this.paused || this.disk.isFull();
    }

    /** For a server that is paused for lack of players: looks again at a disk that stopped the task, so the server wakes once there is room. */
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

    private void checkDisk(final long minFreeBytes) {
        final long chunksLeft = (long) this.plan.pendingCount(this.world.border()) * TileSpiral.TILE_CHUNKS;
        if (this.disk.check(this.delivered, chunksLeft, minFreeBytes)) {
            this.kept.releaseAll();
        }
    }

    /** Stops scheduling at once and lets go of the kept chunks; chunks already requested finish without being kept. */
    void pause() {
        this.paused = true;
        this.kept.releaseAll();
    }

    void resume() {
        if (this.paused) {
            this.paused = false;
            this.disk.rebase(this.delivered);
        }
    }

    /** Lets go of every chunk the task keeps in memory. */
    void close() {
        this.closed = true;
        this.kept.releaseAll();
    }

    boolean paused() {
        return this.paused;
    }

    /** Whether no tile is left to generate and none is being worked on. */
    boolean finished() {
        return this.exhausted && !this.working && this.inFlight == 0;
    }

    /** Whether the task is working or waiting for the server to be fast enough, and not stopped by anyone or by the disk. */
    boolean active() {
        return !this.paused && !this.disk.isFull() && !this.finished();
    }

    String describe() {
        final double perSecond = this.deliveryRate.perSecond();
        return String.format(Locale.ROOT, "%d tiles pending, %.0f chunks/s over the last minute, %d in flight (up to %d)%s",
            this.plan.pendingCount(this.world.border()), perSecond, this.inFlight, this.pacing.parallel(), this.waitingReason());
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
