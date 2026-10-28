package net.kindlingmc.chunk;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Optional;
import org.dreeam.leaf.config.modules.opt.WorkerGovernor;
import org.slf4j.Logger;

/**
 * Sets the chunk worker count the {@link GovernorState} chooses, from the host monitor's thread after every sample, while
 * {@code performance.worker-governor} is on. {@code BalancedPrioritisedThreadPool.adjustThreadCount} is synchronized on the
 * pool, does nothing after the pool shut down and nothing when the count is unchanged; a worker it removes finishes the
 * task it runs and hands its queue back to the others, so no chunk task is lost, only generated more slowly. A config
 * reload sets the configured count again ({@code MoonriseCommon.adjustWorkerThreads}); the next sample sets the governor's.
 */
public final class WorkerPoolGovernor {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static volatile Optional<GovernorStatus> published = Optional.empty();

    private final long sampleIntervalSeconds;
    /** Only touched by the host monitor's thread; empty while the governor is off. */
    private Optional<GovernorState> state = Optional.empty();

    public WorkerPoolGovernor(final long sampleIntervalSeconds) {
        this.sampleIntervalSeconds = sampleIntervalSeconds;
    }

    /** What the governor did with the latest sample; empty while it is off or has not seen a sample. */
    public static Optional<GovernorStatus> status() {
        return published;
    }

    public void onSample(final HostPressure pressure) {
        if (!WorkerGovernor.enabled) {
            release();
            return;
        }
        final GovernorLimits limits = currentLimits();
        final GovernorState previous = this.state.orElseGet(() -> started(limits));
        final GovernorState next = previous.next(pressure, limits);
        logChange(previous, next, pressure);
        MoonriseCommon.WORKER_POOL.adjustThreadCount(next.workers());
        this.state = Optional.of(next);
        published = Optional.of(new GovernorStatus(next.workers(), limits, pressure));
    }

    private static GovernorState started(final GovernorLimits limits) {
        LOGGER.info("Kindling worker governor started: between {} and {} chunk worker threads", limits.floor(), limits.ceiling());
        return GovernorState.startingAt(limits.ceiling());
    }

    private static void logChange(final GovernorState previous, final GovernorState next, final HostPressure pressure) {
        if (next.workers() != previous.workers()) {
            LOGGER.info("Kindling worker governor: {} chunk worker threads, host pressure on the main thread is {}",
                next.workers(), pressure.name().toLowerCase(Locale.ROOT));
        }
    }

    /** Gives the pool its configured count back, when the governor is turned off or the host monitor stops. */
    public void release() {
        if (this.state.isEmpty()) {
            return;
        }
        final int configured = MoonriseCommon.chunkSystemWorkerThreads;
        MoonriseCommon.WORKER_POOL.adjustThreadCount(configured);
        this.state = Optional.empty();
        published = Optional.empty();
        LOGGER.info("Kindling worker governor stopped: back to {} chunk worker threads", configured);
    }

    private GovernorLimits currentLimits() {
        final long quietSamples = Math.ceilDiv(WorkerGovernor.quietSeconds, this.sampleIntervalSeconds);
        return GovernorLimits.of(ChunkWorkers.paperDefaultFor(Runtime.getRuntime().availableProcessors()),
            MoonriseCommon.chunkSystemWorkerThreads, (int) quietSamples);
    }
}
