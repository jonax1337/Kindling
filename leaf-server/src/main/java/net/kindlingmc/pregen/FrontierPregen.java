package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Frontier pregeneration of all worlds: while no player is online it generates a margin around the tiles players have
 * been in, so that later flights arrive over generated terrain. Players' tiles are noted by {@link #visit}, which the
 * caller samples now and then instead of hooking every move. A task works only while the server is empty, pauses at
 * once when a player joins, and leaves a world to an explicit pregeneration run of the same world. What it learned is
 * saved beside the pregeneration state. Everything here runs on the main thread, except writing the file.
 */
final class FrontierPregen {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int PLAN_CHECK_TICKS = 20;
    private static final int SAVE_EVERY_TICKS = 100;

    private final FrontierStore store;
    private final Map<String, WorldFrontier> worlds = new LinkedHashMap<>();
    private long lastSaveTick;
    private boolean workingSinceLastPause;

    FrontierPregen(final FrontierStore store) {
        this.store = store;
    }

    boolean knows(final String worldName) {
        return this.worlds.containsKey(worldName);
    }

    /** Starts watching a world with the tiles saved for it; tiles saved for another seed belong to a world that is gone. */
    void adopt(final PregenWorld world, final int marginTiles) {
        final Optional<FrontierState.Area> saved = this.store.state().area(world.name());
        final boolean sameWorld = saved.filter(area -> area.seed() == world.seed()).isPresent();
        if (saved.isPresent() && !sameWorld) {
            LOGGER.warn("The saved frontier tiles of {} belong to a world with another seed and are dropped", world.name());
            this.store.state().remove(world.name());
            this.store.saveLater();
        }
        final FrontierPlan plan = sameWorld
            ? new FrontierPlan(saved.get().visited().copy(), saved.get().done().copy(), marginTiles)
            : new FrontierPlan(new TileSet(), new TileSet(), marginTiles);
        this.worlds.put(world.name(), new WorldFrontier(world, plan));
    }

    /** Notes that a player is in the chunk. Cheap enough to call for every player once a second. */
    void visit(final String worldName, final int chunkX, final int chunkZ, final int marginTiles) {
        final WorldFrontier frontier = this.worlds.get(worldName);
        if (frontier != null) {
            frontier.plan.visit(TileSet.tileOfChunk(chunkX, chunkZ), marginTiles);
        }
    }

    /** Works on the worlds' tiles; for ticks at which no player is online. */
    void pumpWhileEmpty(final TickLoad load, final PregenLimits limits, final Set<String> worldsWithRun) {
        this.workingSinceLastPause = true;
        for (final Iterator<WorldFrontier> frontiers = this.worlds.values().iterator(); frontiers.hasNext(); ) {
            final WorldFrontier frontier = frontiers.next();
            if (!frontier.world.isLoaded()) {
                this.release(frontier);
                frontiers.remove();
            } else if (!frontier.failed) {
                this.pumpSafely(frontier, load, limits, worldsWithRun.contains(frontier.name));
            }
        }
    }

    /** Stops all work at once, for ticks at which a player is online; costs nothing once everything is paused. */
    void pauseForPlayers() {
        if (!this.workingSinceLastPause) {
            return;
        }
        this.workingSinceLastPause = false;
        for (final WorldFrontier frontier : this.worlds.values()) {
            if (frontier.task != null) {
                frontier.task.pause();
            }
        }
    }

    /** Whether a task is working, so the server must not pause for lack of players. */
    boolean keepsServerAwake() {
        for (final WorldFrontier frontier : this.worlds.values()) {
            if (frontier.task != null && frontier.task.active()) {
                return true;
            }
        }
        return false;
    }

    /** For a server that is paused for lack of players: lets a task stopped by a full disk look at the disk again. */
    void recheckFullDisk(final long minFreeBytes) {
        for (final WorldFrontier frontier : this.worlds.values()) {
            if (frontier.task != null) {
                frontier.task.recheckFullDisk(minFreeBytes);
            }
        }
    }

    /** Hands what changed since the last save to the writer, at most every {@value #SAVE_EVERY_TICKS} ticks. */
    void saveChanges(final long tick) {
        if (tick - this.lastSaveTick < SAVE_EVERY_TICKS) {
            return;
        }
        this.lastSaveTick = tick;
        if (this.rememberChanges()) {
            this.store.saveLater();
        }
    }

    /** Lets go of the kept chunks of every task, e.g. because the feature was switched off. */
    void stop() {
        this.workingSinceLastPause = false;
        for (final WorldFrontier frontier : this.worlds.values()) {
            this.closeTask(frontier);
        }
    }

    /** Saves the exact state and waits for the file to be written; called while the server stops. */
    void shutdown() {
        this.stop();
        this.rememberChanges();
        this.store.saveAndClose();
    }

    List<String> status() {
        final List<String> lines = new ArrayList<>();
        for (final WorldFrontier frontier : this.worlds.values()) {
            lines.add(frontier.describe());
            if (frontier.task != null) {
                lines.add("  " + frontier.task.describe());
                lines.add("  " + frontier.task.describeDisk());
            }
        }
        return lines;
    }

    private void pumpSafely(final WorldFrontier frontier, final TickLoad load, final PregenLimits limits, final boolean hasRun) {
        try {
            this.pumpWorld(frontier, load, limits, hasRun);
        } catch (final RuntimeException | Error failure) {
            LOGGER.error("Frontier pregeneration of {} failed and stays off until the next restart", frontier.name, failure);
            frontier.failed = true;
            this.closeTask(frontier);
        }
    }

    private void pumpWorld(final WorldFrontier frontier, final TickLoad load, final PregenLimits limits, final boolean hasRun) {
        if (frontier.task != null && frontier.task.finished()) {
            this.finish(frontier);
        }
        if (hasRun) {
            this.pauseTask(frontier);
            return;
        }
        if (frontier.task == null && load.tick() % PLAN_CHECK_TICKS == 0) {
            this.startWhenTilesArePending(frontier);
        }
        if (frontier.task != null) {
            frontier.task.resume();
            frontier.task.pump(load, limits);
        }
    }

    private void startWhenTilesArePending(final WorldFrontier frontier) {
        final ChunkBorder border = frontier.world.border();
        if (frontier.plan.nearest(border).isPresent()) {
            frontier.task = new FrontierTask(frontier.world, frontier.plan);
            LOGGER.info("Frontier pregeneration of {} starts: {} tiles around the {} that players visited",
                frontier.name, frontier.plan.pendingCount(border), frontier.plan.visitedCount());
        }
    }

    private void finish(final WorldFrontier frontier) {
        LOGGER.info("Frontier pregeneration of {} finished: {} tiles generated so far", frontier.name, frontier.plan.doneCount());
        this.closeTask(frontier);
    }

    private void pauseTask(final WorldFrontier frontier) {
        if (frontier.task != null) {
            frontier.task.pause();
        }
    }

    private void closeTask(final WorldFrontier frontier) {
        final FrontierTask task = frontier.task;
        frontier.task = null;
        if (task != null) {
            try {
                task.close();
            } catch (final RuntimeException | Error failure) {
                LOGGER.warn("Cannot release the chunks kept by the frontier pregeneration of {}", frontier.name, failure);
            }
        }
    }

    private void release(final WorldFrontier frontier) {
        LOGGER.warn("The world {} was unloaded; its frontier pregeneration continues when the world is loaded again", frontier.name);
        this.closeTask(frontier);
        if (this.remember(frontier)) {
            this.store.saveLater();
        }
    }

    private boolean rememberChanges() {
        boolean changed = false;
        for (final WorldFrontier frontier : this.worlds.values()) {
            changed |= this.remember(frontier);
        }
        return changed;
    }

    /** @return whether the plan changed since it was last put into the state */
    private boolean remember(final WorldFrontier frontier) {
        if (frontier.plan.revision() == frontier.rememberedRevision) {
            return false;
        }
        frontier.rememberedRevision = frontier.plan.revision();
        this.store.state().put(frontier.name, frontier.plan.snapshot(frontier.world.seed()));
        return true;
    }

    /** One world: its plan, and the task while there is something to generate. */
    private static final class WorldFrontier {

        final PregenWorld world;
        /** {@link PregenWorld#name()} builds a string each time; this is asked on every tick. */
        final String name;
        final FrontierPlan plan;
        @Nullable FrontierTask task;
        long rememberedRevision;
        boolean failed;

        WorldFrontier(final PregenWorld world, final FrontierPlan plan) {
            this.world = world;
            this.name = world.name();
            this.plan = plan;
            this.rememberedRevision = plan.revision();
        }

        String describe() {
            return String.format(Locale.ROOT, "frontier %s: %,d tiles visited, %,d generated%s", this.name, this.plan.visitedCount(),
                this.plan.doneCount(), this.failed ? ", off after a failure" : "");
        }
    }
}
