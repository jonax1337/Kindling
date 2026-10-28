package net.kindlingmc.pregen;

import com.mojang.logging.LogUtils;
import java.util.OptionalDouble;
import net.kindlingmc.pregen.PregenState.Phase;
import org.slf4j.Logger;

/**
 * Pumps a {@link PregenTask} and settles what becomes of it: a finished run is saved as done, one whose world was
 * unloaded is parked, and one that fails is paused, so one failing run neither stops the others nor the server tick.
 */
final class TaskSupervisor {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    /** What the caller does with the task after a pump. */
    enum Outcome {
        /** Pump it again next tick; also after a failure, when it is paused. */
        CONTINUES,
        /** Drop it: the run is done and saved as such. */
        FINISHED,
        /** Drop it and look for its world again: the world was unloaded and the run is saved for when it is back. */
        PARKED
    }

    private final StateStore store;

    TaskSupervisor(final StateStore store) {
        this.store = store;
    }

    Outcome pump(final PregenTask task, final long tick, final long averageTickNanos, final OptionalDouble mainThreadWait,
        final PregenLimits limits) {
        try {
            if (!task.world().isLoaded()) {
                this.parkUnloaded(task, limits.maxParallel());
                return Outcome.PARKED;
            }
            task.pump(tick, averageTickNanos, mainThreadWait, limits);
            if (task.finished()) {
                this.finish(task, limits.maxParallel());
                return Outcome.FINISHED;
            }
            return Outcome.CONTINUES;
        } catch (final Throwable failure) {
            this.pauseAfterFailure(task, failure, limits.maxParallel());
            return Outcome.CONTINUES;
        }
    }

    private void finish(final PregenTask task, final int maxParallel) {
        LOGGER.info("Pregeneration finished: {}", task.describe());
        closeQuietly(task);
        this.store.remember(task, Phase.DONE, maxParallel);
        this.store.saveLater();
    }

    private void parkUnloaded(final PregenTask task, final int maxParallel) {
        LOGGER.warn("The world {} was unloaded; its pregeneration continues when the world is loaded again", task.world().name());
        this.store.remember(task, task.phase(), maxParallel);
        closeQuietly(task);
        this.store.saveLater();
    }

    /** The run stays known, as paused, so {@code /pregen resume} can try again once the cause is gone. */
    private void pauseAfterFailure(final PregenTask task, final Throwable failure, final int maxParallel) {
        LOGGER.error("Pregeneration of {} failed and is paused; '/pregen resume' continues it", task.world().name(), failure);
        try {
            task.pause();
        } catch (final Throwable releaseFailure) {
            LOGGER.warn("Cannot release the chunks kept by the pregeneration of {}", task.world().name(), releaseFailure);
        }
        this.store.remember(task, Phase.PAUSED, maxParallel);
        this.store.saveLater();
    }

    static void closeQuietly(final PregenTask task) {
        try {
            task.close();
        } catch (final Throwable failure) {
            LOGGER.warn("Cannot release the chunks kept by the pregeneration of {}", task.world().name(), failure);
        }
    }
}
