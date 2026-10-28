package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Watches the free space of a pregeneration's disk: every {@value #CHECK_TICKS} ticks it compares the space with what
 * the rest of the work needs ({@link DiskGuard}) and says whether the work must stop, or may continue.
 */
final class DiskWatch {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int TICKS_PER_SECOND = 20;
    private static final int CHECK_TICKS = 30 * TICKS_PER_SECOND;

    private final PregenWorld world;
    private final String work;
    private final DiskGuard guard;
    private boolean full;
    private int ticksSinceCheck = CHECK_TICKS;
    private @Nullable DiskForecast forecast;

    /** @param work names what the disk limits in log messages, e.g. {@code Pregeneration} */
    DiskWatch(final PregenWorld world, final String work) {
        this.world = world;
        this.work = work;
        this.guard = new DiskGuard(world.freeDiskBytes(), 0L);
    }

    /** Counts a tick; whether it is time to {@link #check}. */
    boolean due() {
        return ++this.ticksSinceCheck >= CHECK_TICKS;
    }

    boolean isFull() {
        return this.full;
    }

    /** Starts measuring anew, e.g. after a pause during which other programs used the disk. */
    void rebase(final long chunksDelivered) {
        this.guard.rebase(this.world.freeDiskBytes(), chunksDelivered);
    }

    /**
     * Compares the free space with what the rest of the work needs.
     *
     * @return whether this check stopped the work; a check that lets it continue returns false
     */
    boolean check(final long chunksDelivered, final long chunksLeft, final long minFreeBytes) {
        this.ticksSinceCheck = 0;
        final DiskForecast current = this.guard.forecast(this.world.freeDiskBytes(), chunksDelivered, chunksLeft, minFreeBytes);
        this.forecast = current;
        if (current.runsOut() == this.full) {
            return false;
        }
        this.full = current.runsOut();
        if (this.full) {
            LOGGER.warn("{} of {} stops, the disk would run out: {}", this.work, this.world.name(), current.describe());
        } else {
            LOGGER.info("{} of {} continues, the disk has space again: {}", this.work, this.world.name(), current.describe());
        }
        return this.full;
    }

    String describe() {
        return this.forecast == null ? "disk: not checked yet" : "disk: " + this.forecast.describe();
    }
}
