package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kindlingmc.pregen.PregenState.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.dreeam.leaf.config.modules.opt.Pregeneration;
import org.slf4j.Logger;

/**
 * Runs the pregenerations of all worlds from the server tick, keeps their progress in {@code pregeneration.properties}
 * in the world folder so a restart continues where it stopped, and starts the automatic ones from the configuration.
 * Everything here runs on the main thread.
 */
public final class Pregenerator {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final String STATE_FILE = "pregeneration.properties";
    private static final int SAVE_EVERY_TICKS = 600;
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final Map<ResourceKey<Level>, PregenTask> TASKS = new LinkedHashMap<>();
    private static PregenState state = new PregenState();
    private static MinecraftServer server;
    private static boolean started;

    private Pregenerator() {
    }

    public static void install(final MinecraftServer server) {
        Pregenerator.server = server;
        server.addTickable(Pregenerator::tick);
    }

    private static void tick() {
        if (!started) {
            started = true;
            loadState();
            resumeSaved();
            startAutomatic();
        }
        final long tick = server.getTickCount();
        final long budget = (long) (Pregeneration.maxMspt * NANOS_PER_MILLI);
        final long average = server.getAverageTickTimeNanos();
        for (final Iterator<PregenTask> tasks = TASKS.values().iterator(); tasks.hasNext(); ) {
            final PregenTask task = tasks.next();
            task.pump(tick, average, budget, Pregeneration.maxParallel);
            if (task.finished()) {
                LOGGER.info("Pregeneration finished: {}", task.describe());
                task.close();
                remember(task, Phase.DONE);
                tasks.remove();
                saveState();
            }
        }
        if (tick % SAVE_EVERY_TICKS == 0 && !TASKS.isEmpty()) {
            TASKS.values().forEach(task -> remember(task, task.paused() ? Phase.PAUSED : Phase.RUNNING));
            saveState();
        }
    }

    /** Starts (or restarts from the centre) the pregeneration of a world; replaces one already running there. */
    public static String start(final ServerLevel level, final BlockPos center, final int radiusBlocks) {
        final PregenTask task = new PregenTask(level, center.getX() >> 4, center.getZ() >> 4,
            Math.max(0, radiusBlocks / BLOCKS_PER_CHUNK), 0L);
        final PregenTask replaced = TASKS.put(level.dimension(), task);
        if (replaced != null) {
            replaced.close();
        }
        remember(task, Phase.RUNNING);
        saveState();
        return task.describe();
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
        final PregenTask task = TASKS.remove(level.dimension());
        if (task == null) {
            return false;
        }
        task.close();
        forget(level.dimension());
        saveState();
        return true;
    }

    public static List<String> status() {
        final List<String> lines = new ArrayList<>();
        TASKS.values().forEach(task -> lines.add(task.describe()));
        return lines;
    }

    private static boolean withTask(final ServerLevel level, final Consumer<PregenTask> action) {
        final PregenTask task = TASKS.get(level.dimension());
        if (task == null) {
            return false;
        }
        action.accept(task);
        saveState();
        return true;
    }

    private static void resumeSaved() {
        for (final ServerLevel level : server.getAllLevels()) {
            state.run(keyOf(level.dimension())).filter(run -> run.phase() != Phase.DONE).ifPresent(run -> {
                final PregenTask task = new PregenTask(level, run.centerX(), run.centerZ(), run.radius(), run.next());
                if (run.phase() == Phase.PAUSED) {
                    task.pause();
                }
                TASKS.put(level.dimension(), task);
                LOGGER.info("Pregeneration continues: {}", task.describe());
            });
        }
    }

    /** Starts the configured radius of every world that has neither a run nor a finished run at least as large. */
    private static void startAutomatic() {
        if (!Pregeneration.automatic) {
            return;
        }
        for (final ServerLevel level : server.getAllLevels()) {
            final int radiusBlocks = Pregeneration.radiusFor(level.dimension());
            final boolean doneBefore = state.run(keyOf(level.dimension()))
                .filter(run -> run.phase() == Phase.DONE && run.radius() >= radiusBlocks / BLOCKS_PER_CHUNK)
                .isPresent();
            if (radiusBlocks > 0 && !TASKS.containsKey(level.dimension()) && !doneBefore) {
                final BlockPos center = level.dimension() == Level.OVERWORLD ? level.getRespawnData().pos() : BlockPos.ZERO;
                LOGGER.info("Automatic pregeneration starts: {}", start(level, center, radiusBlocks));
            }
        }
    }

    private static void remember(final PregenTask task, final Phase phase) {
        state.put(keyOf(task.level.dimension()),
            new PregenState.Run(task.centerX, task.centerZ, task.radius, task.resumeIndex(), phase));
    }

    private static void forget(final ResourceKey<Level> dimension) {
        state.remove(keyOf(dimension));
    }

    private static String keyOf(final ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
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
        final Path file = stateFile();
        try {
            state.save(file);
        } catch (final IOException e) {
            LOGGER.error("Cannot write the pregeneration state {}", file, e);
        }
    }
}
