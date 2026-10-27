package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.dreeam.leaf.config.modules.opt.Pregeneration;
import org.slf4j.Logger;

/**
 * Runs the pregenerations of all worlds from the server tick, keeps their progress in {@code pregeneration.properties}
 * in the world folder so a restart continues where it stopped, and starts the automatic ones from the configuration.
 * A world that is unloaded parks its run; a run waits for its world to appear (and is dropped if the world was
 * regenerated with another seed). While a run is working the server does not pause for lack of players.
 * Everything here runs on the main thread, except writing the state file ({@link StateWriter}).
 */
public final class Pregenerator {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final String STATE_FILE = "pregeneration.properties";
    private static final int SAVE_EVERY_TICKS = 600;
    private static final int WORLD_SYNC_TICKS = 20;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final int TICKS_PER_SECOND = 20;

    private static final Map<String, PregenTask> TASKS = new LinkedHashMap<>();
    // the worlds seen at the last sync: only a world that appears gets its saved or automatic run
    private static final Set<String> KNOWN_WORLDS = new HashSet<>();
    private static final Set<String> PRESENT_WORLDS = new HashSet<>();
    private static PregenState state = new PregenState();
    private static MinecraftServer server;
    private static StateWriter writer;
    private static boolean started;
    /** Read once a second: the host monitor publishes a new value every few seconds. */
    private static OptionalDouble mainThreadWait = OptionalDouble.empty();

    private Pregenerator() {
    }

    public static void install(final MinecraftServer server) {
        Pregenerator.server = server;
        writer = new StateWriter(stateFile());
        server.addTickable(Pregenerator::tick);
    }

    /** Whether a run is working, so the server must not pause for lack of players; stopped and finished runs do not count. */
    public static boolean keepsServerAwake() {
        if (TASKS.isEmpty()) {
            return false;
        }
        for (final PregenTask task : TASKS.values()) {
            if (task.active()) {
                return true;
            }
        }
        return false;
    }

    /** Saves the exact progress of every run and waits for the state file to be written; called while the server stops. */
    public static void shutdown() {
        if (!started) {
            return;
        }
        try {
            for (final PregenTask task : TASKS.values()) {
                state.put(task.world().name(), task.snapshot(phaseOf(task), task.resumeIndex()));
                closeQuietly(task);
            }
            TASKS.clear();
            writer.writeAndClose(state);
        } catch (final RuntimeException failure) {
            LOGGER.error("Cannot save the pregeneration state on shutdown", failure);
        }
    }

    private static void tick() {
        try {
            if (!started) {
                started = true;
                loadState();
                adoptNewWorlds();
            }
            final long tick = server.getTickCount();
            if (tick % WORLD_SYNC_TICKS == 0) {
                adoptNewWorlds();
            }
            if (TASKS.isEmpty()) {
                return;
            }
            pumpTasks(tick);
            if (tick % SAVE_EVERY_TICKS == 0 && !TASKS.isEmpty()) {
                TASKS.values().forEach(task -> remember(task, phaseOf(task)));
                saveState();
            }
        } catch (final RuntimeException failure) {
            LOGGER.error("Pregeneration failed to tick", failure);
        }
    }

    private static void pumpTasks(final long tick) {
        final PregenLimits limits = new PregenLimits((long) (Pregeneration.maxMspt * NANOS_PER_MILLI), Pregeneration.maxParallel,
            Pregeneration.minFreeDiskBytes(), Pregeneration.maxMainThreadWait);
        final long average = server.getAverageTickTimeNanos();
        if (tick % TICKS_PER_SECOND == 0) {
            mainThreadWait = HostMonitor.mainThreadWaitShare(HostWindow.LAST_MINUTE);
        }
        for (final Iterator<PregenTask> tasks = TASKS.values().iterator(); tasks.hasNext(); ) {
            if (!pumpOrRetire(tasks.next(), tick, average, limits)) {
                tasks.remove();
            }
        }
    }

    /** One failing run must not stop the others or the server tick: it is stopped, saved as paused and reported. */
    private static boolean pumpOrRetire(final PregenTask task, final long tick, final long average, final PregenLimits limits) {
        try {
            if (!task.world().isLoaded()) {
                parkUnloaded(task);
                return false;
            }
            task.pump(tick, average, mainThreadWait, limits);
            if (task.finished()) {
                finish(task);
                return false;
            }
            return true;
        } catch (final Throwable failure) {
            abandon(task, failure);
            return false;
        }
    }

    private static void finish(final PregenTask task) {
        LOGGER.info("Pregeneration finished: {}", task.describe());
        closeQuietly(task);
        remember(task, Phase.DONE);
        saveState();
    }

    private static void parkUnloaded(final PregenTask task) {
        LOGGER.warn("The world {} was unloaded; its pregeneration continues when the world is loaded again", task.world().name());
        remember(task, phaseOf(task));
        closeQuietly(task);
        KNOWN_WORLDS.remove(task.world().name());
        saveState();
    }

    private static void abandon(final PregenTask task, final Throwable failure) {
        LOGGER.error("Pregeneration of {} failed and is stopped; it stays paused in the saved state", task.world().name(), failure);
        remember(task, Phase.PAUSED);
        closeQuietly(task);
        saveState();
    }

    private static void closeQuietly(final PregenTask task) {
        try {
            task.close();
        } catch (final Throwable failure) {
            LOGGER.warn("Cannot release the chunks kept by the pregeneration of {}", task.world().name(), failure);
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
        state.remove(name);
        saveState();
        return true;
    }

    public static List<String> status() {
        final List<String> lines = new ArrayList<>();
        for (final PregenTask task : TASKS.values()) {
            lines.add(task.describe());
            lines.add("  " + task.describeDisk());
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
            if (KNOWN_WORLDS.add(name) && !TASKS.containsKey(name)) {
                adopt(level);
            }
        }
        KNOWN_WORLDS.retainAll(PRESENT_WORLDS);
    }

    private static void adopt(final ServerLevel level) {
        final ServerPregenWorld world = new ServerPregenWorld(level);
        discardRunOfAnotherWorld(world);
        resumeSaved(world);
        startAutomatic(level, world.name());
    }

    /** A world regenerated under the same name has another seed; the chunks its old run generated are gone. */
    private static void discardRunOfAnotherWorld(final ServerPregenWorld world) {
        state.run(world.name())
            .filter(run -> run.seed().isPresent() && run.seed().getAsLong() != world.seed())
            .ifPresent(run -> {
                LOGGER.warn("The saved pregeneration of {} belongs to a world with another seed and is dropped", world.name());
                state.remove(world.name());
                saveState();
            });
    }

    private static void resumeSaved(final ServerPregenWorld world) {
        state.run(world.name()).filter(run -> run.phase() != Phase.DONE).ifPresent(run -> {
            final PregenTask task = new PregenTask(world, run.centerX(), run.centerZ(), run.radius(), run.next());
            if (run.phase() == Phase.PAUSED) {
                task.pause();
            }
            TASKS.put(world.name(), task);
            LOGGER.info("Pregeneration continues: {}", task.describe());
        });
    }

    /** Starts the configured radius of a world that has neither a run nor a finished run at least as large. */
    private static void startAutomatic(final ServerLevel level, final String name) {
        final int radiusBlocks = Pregeneration.radiusFor(level.dimension());
        if (!Pregeneration.automatic || radiusBlocks <= 0 || TASKS.containsKey(name)) {
            return;
        }
        final boolean doneBefore = state.run(name)
            .filter(run -> run.phase() == Phase.DONE && run.radius() >= radiusBlocks / TileSpiral.BLOCKS_PER_CHUNK)
            .isPresent();
        if (!doneBefore) {
            final BlockPos center = level.dimension() == Level.OVERWORLD ? level.getRespawnData().pos() : BlockPos.ZERO;
            LOGGER.info("Automatic pregeneration starts: {}", start(level, center, radiusBlocks));
        }
    }

    private static Phase phaseOf(final PregenTask task) {
        return task.paused() ? Phase.PAUSED : Phase.RUNNING;
    }

    /** The saved index steps back over what a crash could lose; {@link #shutdown()} saves the exact one. */
    private static void remember(final PregenTask task, final Phase phase) {
        state.put(task.world().name(), task.snapshot(phase, task.crashSafeResumeIndex(Pregeneration.maxParallel)));
    }

    private static Path stateFile() {
        return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
    }

    /** An unusable file is kept as {@code .corrupt}; what in it is still valid is resumed, the rest starts fresh. */
    private static void loadState() {
        final Path file = stateFile();
        state = PregenState.load(file);
        if (state.problems().isEmpty()) {
            return;
        }
        LOGGER.warn("The pregeneration state {} is damaged ({}); the affected worlds start over",
            file, String.join("; ", state.problems()));
        try {
            PregenState.keepCorrupt(file);
        } catch (final IOException e) {
            LOGGER.error("Cannot keep a copy of the damaged pregeneration state {}", file, e);
        }
    }

    private static void saveState() {
        writer.writeLater(state.snapshot());
    }
}
