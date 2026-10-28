package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import net.kindlingmc.doctor.HostMonitor;
import net.kindlingmc.doctor.HostWindow;
import net.kindlingmc.pregen.PregenState.Phase;
import net.kindlingmc.pregen.TaskSupervisor.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.dreeam.leaf.config.modules.opt.Pregeneration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Runs the pregenerations of all worlds from the server tick, keeps their progress in {@code pregeneration.properties}
 * in the world folder so a restart continues where it stopped, and starts the automatic ones from the configuration.
 * A world that is unloaded parks its run; a run waits for its world to appear (and is dropped if the world was
 * regenerated with another seed). While a run is working the server does not pause for lack of players.
 * Everything here runs on the main thread, except writing the state file ({@link StateWriter}); the file is read before
 * the first run is started or saved ({@link StateStore}).
 */
public final class Pregenerator {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final String STATE_FILE = "pregeneration.properties";
    private static final String FRONTIER_FILE = "pregeneration-frontier.properties"; // Kindling - frontier pregeneration
    private static final int SAVE_EVERY_TICKS = 600;
    private static final int VISIT_SAMPLE_TICKS = 20; // Kindling - frontier pregeneration
    private static final int WORLD_SYNC_TICKS = 20;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final int TICKS_PER_SECOND = 20;

    private static final Map<String, PregenTask> TASKS = new LinkedHashMap<>();
    // the worlds seen at the last sync: only a world that appears gets its saved or automatic run
    private static final Set<String> KNOWN_WORLDS = new HashSet<>();
    private static final Set<String> PRESENT_WORLDS = new HashSet<>();
    private static MinecraftServer server;
    private static StateStore store;
    private static TaskSupervisor supervisor;
    /** Created when the frontier pregeneration is first switched on. */
    private static @Nullable FrontierPregen frontier; // Kindling - frontier pregeneration
    private static boolean started;
    /** Read once a second: the host monitor publishes a new value every few seconds. */
    private static OptionalDouble mainThreadWait = OptionalDouble.empty();
    /** {@link #keepsServerAwake()} as of the last tick, for other threads. */
    private static volatile boolean working;

    private Pregenerator() {
    }

    public static void install(final MinecraftServer server) {
        Pregenerator.server = server;
        store = new StateStore(stateFile());
        supervisor = new TaskSupervisor(store);
        server.addTickable(Pregenerator::tick);
    }

    /** Whether a run is working, so the server must not pause for lack of players; stopped and finished runs do not count. */
    public static boolean keepsServerAwake() {
        for (final PregenTask task : TASKS.values()) {
            if (task.active()) {
                return true;
            }
        }
        return frontier != null && frontier.keepsServerAwake(); // Kindling - frontier pregeneration
    }

    /** Saves the exact progress of every run and waits for the state file to be written; called while the server stops. */
    public static void shutdown() {
        shutdownFrontier(); // Kindling - frontier pregeneration
        if (store == null || !store.isLoaded()) {
            return;
        }
        try {
            for (final PregenTask task : TASKS.values()) {
                store.state().put(task.world().name(), task.snapshot(task.phase(), task.resumeIndex()));
                TaskSupervisor.closeQuietly(task);
            }
            TASKS.clear();
            store.saveAndClose();
        } catch (final RuntimeException failure) {
            LOGGER.error("Cannot save the pregeneration state on shutdown", failure);
        }
    }

    private static void tick() {
        try {
            if (!started) {
                started = true;
                adoptNewWorlds();
            }
            final long tick = server.getTickCount();
            if (tick % WORLD_SYNC_TICKS == 0) {
                adoptNewWorlds();
            }
            refreshMainThreadWait(tick); // Kindling - the frontier pregeneration reads it too
            tickFrontier(tick); // Kindling - frontier pregeneration
            if (TASKS.isEmpty()) {
                return;
            }
            pumpTasks(tick);
            if (tick % SAVE_EVERY_TICKS == 0 && !TASKS.isEmpty()) {
                TASKS.values().forEach(task -> remember(task, task.phase()));
                saveState();
            }
        } catch (final RuntimeException failure) {
            LOGGER.error("Pregeneration failed to tick", failure);
        } finally {
            working = keepsServerAwake();
        }
    }

    /**
     * Called instead of {@link #tick()} for every tick the server skips because it is paused for lack of players: a run
     * stopped by a full disk looks at the disk again, and the server wakes up once there is room.
     */
    public static void tickWhilePaused() {
        if (TASKS.isEmpty() && frontier == null) {
            return;
        }
        try {
            final long minFreeDiskBytes = Pregeneration.minFreeDiskBytes();
            for (final PregenTask task : TASKS.values()) {
                task.recheckFullDisk(minFreeDiskBytes);
            }
            if (frontier != null) { // Kindling - frontier pregeneration
                frontier.recheckFullDisk(minFreeDiskBytes);
            }
        } catch (final RuntimeException failure) {
            LOGGER.error("Pregeneration failed to check the disk while the server is paused", failure);
        } finally {
            working = keepsServerAwake();
        }
    }

    /** Whether a run was working at the last tick; unlike {@link #keepsServerAwake()} safe to call from any thread. */
    public static boolean isWorking() {
        return working;
    }

    private static PregenLimits currentLimits() {
        return new PregenLimits((long) (Pregeneration.maxMspt * NANOS_PER_MILLI), Pregeneration.maxParallel,
            Pregeneration.minFreeDiskBytes(), Pregeneration.maxMainThreadWait);
    }

    private static void refreshMainThreadWait(final long tick) {
        if (tick % TICKS_PER_SECOND == 0 && (!TASKS.isEmpty() || frontier != null)) {
            mainThreadWait = HostMonitor.mainThreadWaitShare(HostWindow.LAST_MINUTE);
        }
    }

    private static void pumpTasks(final long tick) {
        final PregenLimits limits = currentLimits();
        final long average = server.getAverageTickTimeNanos();
        for (final Iterator<PregenTask> tasks = TASKS.values().iterator(); tasks.hasNext(); ) {
            final PregenTask task = tasks.next();
            final Outcome outcome = supervisor.pump(task, tick, average, mainThreadWait, limits);
            if (outcome != Outcome.CONTINUES) {
                tasks.remove();
            }
            if (outcome == Outcome.PARKED) {
                KNOWN_WORLDS.remove(task.world().name());
            }
        }
    }

    /**
     * Starts (or restarts from the centre) the pregeneration of a world; replaces one already running there.
     *
     * @return what was started: the area, and the first status line
     */
    public static String start(final ServerLevel level, final BlockPos center, final int radiusBlocks) {
        final ServerPregenWorld world = new ServerPregenWorld(level);
        final int centerX = center.getX() >> 4;
        final int centerZ = center.getZ() >> 4;
        final int requestedRadius = Math.max(0, radiusBlocks / TileSpiral.BLOCKS_PER_CHUNK);
        final int radius = Math.min(requestedRadius, world.border().reachFrom(centerX, centerZ));
        final PregenTask task = new PregenTask(world, centerX, centerZ, radius, 0L);
        task.checkDisk(Pregeneration.minFreeDiskBytes());
        final PregenTask replaced = TASKS.put(world.name(), task);
        if (replaced != null) {
            replaced.close();
        }
        remember(task, Phase.RUNNING);
        saveState();
        final String limit = radius < requestedRadius ? " (limited by the world border from " + requestedRadius + " chunks)" : "";
        return task.describeArea() + limit + ". " + task.describe();
    }

    public static boolean pause(final ServerLevel level) {
        return withTask(level, task -> {
            task.pause();
            remember(task, Phase.PAUSED);
        });
    }

    public static boolean resume(final ServerLevel level) {
        return withTask(level, task -> {
            task.resume();
            remember(task, Phase.RUNNING);
        });
    }

    public static boolean cancel(final ServerLevel level) {
        final String name = ServerPregenWorld.nameOf(level);
        final PregenTask task = TASKS.remove(name);
        if (task == null) {
            return false;
        }
        task.close();
        store.state().remove(name);
        saveState();
        return true;
    }

    public static List<String> status() {
        final List<String> lines = new ArrayList<>();
        for (final PregenTask task : TASKS.values()) {
            lines.add(task.describe());
            lines.add("  " + task.describeDisk());
        }
        if (frontier != null) { // Kindling - frontier pregeneration
            lines.addAll(frontier.status());
        }
        return lines;
    }

    private static boolean withTask(final ServerLevel level, final Consumer<PregenTask> action) {
        final PregenTask task = TASKS.get(ServerPregenWorld.nameOf(level));
        if (task == null) {
            return false;
        }
        action.accept(task);
        saveState();
        return true;
    }

    /** Gives every world that has appeared since the last look its saved run, or its automatic one. */
    private static void adoptNewWorlds() {
        PRESENT_WORLDS.clear();
        for (final ServerLevel level : server.getAllLevels()) {
            final String name = ServerPregenWorld.nameOf(level);
            PRESENT_WORLDS.add(name);
            adoptFrontier(level, name); // Kindling - frontier pregeneration
            if (KNOWN_WORLDS.add(name) && !TASKS.containsKey(name)) {
                adopt(level);
            }
        }
        KNOWN_WORLDS.retainAll(PRESENT_WORLDS);
    }

    // Kindling start - frontier pregeneration
    /** While no player is online, generates a margin around the tiles players have been in; see {@link FrontierPregen}. */
    private static void tickFrontier(final long tick) {
        if (!Pregeneration.frontierEnabled) {
            shutdownFrontier();
            return;
        }
        final FrontierPregen pregen = frontier();
        pregen.saveChanges(tick);
        if (server.getPlayerCount() == 0) {
            pregen.pumpWhileEmpty(new TickLoad(tick, server.getAverageTickTimeNanos(), mainThreadWait), currentLimits(), TASKS.keySet());
            return;
        }
        pregen.pauseForPlayers();
        if (tick % VISIT_SAMPLE_TICKS == 0) {
            sampleVisitors(pregen);
        }
    }

    private static void sampleVisitors(final FrontierPregen pregen) {
        final int marginTiles = frontierMarginTiles();
        final List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            final ServerPlayer player = players.get(i);
            pregen.visit(ServerPregenWorld.nameOf(player.level()), player.getBlockX() >> 4, player.getBlockZ() >> 4, marginTiles);
        }
    }

    private static void adoptFrontier(final ServerLevel level, final String name) {
        if (Pregeneration.frontierEnabled && !frontier().knows(name)) {
            frontier().adopt(new ServerPregenWorld(level), frontierMarginTiles());
        }
    }

    private static FrontierPregen frontier() {
        if (frontier == null) {
            frontier = new FrontierPregen(new FrontierStore(server.getWorldPath(LevelResource.ROOT).resolve(FRONTIER_FILE)));
        }
        return frontier;
    }

    private static int frontierMarginTiles() {
        return FrontierPlan.marginTiles(Pregeneration.frontierMarginChunks);
    }

    /** Saves what the frontier pregeneration learned and lets go of its chunks; it starts afresh if it is switched on again. */
    private static void shutdownFrontier() {
        if (frontier == null) {
            return;
        }
        try {
            frontier.shutdown();
        } catch (final RuntimeException failure) {
            LOGGER.error("Cannot save the frontier pregeneration state", failure);
        }
        frontier = null;
    }
    // Kindling end - frontier pregeneration

    private static void adopt(final ServerLevel level) {
        final ServerPregenWorld world = new ServerPregenWorld(level);
        discardRunOfAnotherWorld(world);
        resumeSaved(world);
        startAutomatic(level, world);
    }

    /** A world regenerated under the same name has another seed; the chunks its old run generated are gone. */
    private static void discardRunOfAnotherWorld(final ServerPregenWorld world) {
        store.state().run(world.name())
            .filter(run -> run.seed().isPresent() && run.seed().getAsLong() != world.seed())
            .ifPresent(run -> {
                LOGGER.warn("The saved pregeneration of {} belongs to a world with another seed and is dropped", world.name());
                store.state().remove(world.name());
                saveState();
            });
    }

    private static void resumeSaved(final ServerPregenWorld world) {
        store.state().run(world.name()).filter(run -> run.phase() != Phase.DONE).ifPresent(run -> {
            final PregenTask task = new PregenTask(world, run.centerX(), run.centerZ(), run.radius(), run.next());
            if (run.phase() == Phase.PAUSED) {
                task.pause();
            }
            TASKS.put(world.name(), task);
            LOGGER.info("Pregeneration continues: {}", task.describe());
        });
    }

    /** Starts the configured radius of a world that has neither a run nor a finished run covering it. */
    private static void startAutomatic(final ServerLevel level, final ServerPregenWorld world) {
        final int radiusBlocks = Pregeneration.radiusFor(level.dimension());
        if (!Pregeneration.automatic || radiusBlocks <= 0 || TASKS.containsKey(world.name())) {
            return;
        }
        final int configuredRadius = radiusBlocks / TileSpiral.BLOCKS_PER_CHUNK;
        final boolean doneBefore = store.state().run(world.name())
            .filter(run -> run.isDoneFor(configuredRadius, world.border()))
            .isPresent();
        if (!doneBefore) {
            final BlockPos center = level.dimension() == Level.OVERWORLD ? level.getRespawnData().pos() : BlockPos.ZERO;
            LOGGER.info("Automatic pregeneration starts: {}", start(level, center, radiusBlocks));
        }
    }

    private static void remember(final PregenTask task, final Phase phase) {
        store.remember(task, phase, Pregeneration.maxParallel);
    }

    private static Path stateFile() {
        return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
    }

    private static void saveState() {
        store.saveLater();
    }
}
