package net.kindlingmc.chunk;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.dreeam.leaf.config.modules.opt.ChunkWorkerDemand;
import org.dreeam.leaf.config.modules.opt.WorkerGovernor;
import org.slf4j.Logger;

/**
 * Sets the chunk worker count the {@link DemandState} chooses from the {@link GenerationBacklog}, once a second on a
 * daemon thread, while {@code performance.worker-demand} is on and the worker governor is off. The pool keeps every
 * queued task when it loses a worker ({@link WorkerPoolGovernor} explains {@code adjustThreadCount}); a config reload
 * sets the configured count again and the next sample sets the chosen one.
 */
public final class WorkerDemand {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long SAMPLE_INTERVAL_SECONDS = 1;
    private static final double SAMPLE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(SAMPLE_INTERVAL_SECONDS);

    /** Only touched by the main thread. */
    private static boolean started;

    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(WorkerDemand::daemonThread);
    /** Only touched by the sampling thread; empty while the setting is off. */
    private Optional<DemandState> state = Optional.empty();

    private WorkerDemand() {
    }

    public static void install() {
        if (started) {
            return;
        }
        started = true;
        new WorkerDemand().start();
    }

    private void start() {
        this.sampler.scheduleWithFixedDelay(this::sampleOrStop, SAMPLE_INTERVAL_SECONDS, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void sampleOrStop() {
        try {
            sample();
        } catch (final RuntimeException e) {
            // Losing the scaling must never disturb the server.
            LOGGER.warn("Kindling worker demand stopped", e);
            this.sampler.shutdown();
            release();
        }
    }

    private void sample() {
        final double averageWaiting = GenerationBacklog.takeWaitedNanos() / SAMPLE_INTERVAL_NANOS;
        if (!ChunkWorkerDemand.enabled || WorkerGovernor.enabled) {
            release();
            return;
        }
        final GovernorLimits limits = currentLimits();
        final DemandState next = this.state.orElseGet(() -> started(limits)).next(averageWaiting, limits);
        MoonriseCommon.WORKER_POOL.adjustThreadCount(next.workers());
        this.state = Optional.of(next);    }

    private static DemandState started(final GovernorLimits limits) {
        LOGGER.info("Kindling worker demand started: {} chunk worker threads while new terrain waits, otherwise {}",
            limits.ceiling(), limits.floor());
        return DemandState.startingAt(limits.ceiling());
    }

    /** Gives the pool its configured count back, unless the worker governor owns it now. */
    private void release() {
        if (this.state.isEmpty()) {
            return;
        }
        this.state = Optional.empty();
        if (!WorkerGovernor.enabled) {
            MoonriseCommon.WORKER_POOL.adjustThreadCount(MoonriseCommon.chunkSystemWorkerThreads);
        }
    }

    private static GovernorLimits currentLimits() {
        final long quietSamples = Math.ceilDiv(ChunkWorkerDemand.quietSeconds, SAMPLE_INTERVAL_SECONDS);
        return GovernorLimits.of(ChunkWorkers.paperDefaultFor(Runtime.getRuntime().availableProcessors()),
            MoonriseCommon.chunkSystemWorkerThreads, (int) quietSamples);
    }

    private static Thread daemonThread(final Runnable task) {
        final Thread thread = new Thread(task, "Kindling worker demand");
        thread.setDaemon(true);
        return thread;
    }
}
