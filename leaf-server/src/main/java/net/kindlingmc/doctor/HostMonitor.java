package net.kindlingmc.doctor;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import com.mojang.logging.LogUtils;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.config.modules.opt.StartupDoctor;
import org.slf4j.Logger;

/**
 * Tells whether lag comes from this server or from the machine under it. Every few seconds a daemon thread reads how
 * long the main thread and the chunk workers waited for a CPU, how much CPU time the hypervisor took (steal) and
 * whether a container limit throttled the server (Linux only; elsewhere nothing is sampled). {@code /kindling doctor}
 * reports the result over the last minute and the last 15 minutes.
 */
public final class HostMonitor {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long SAMPLE_INTERVAL_SECONDS = 5;
    private static final long SAMPLE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(SAMPLE_INTERVAL_SECONDS);

    private static volatile HostLoads published = HostLoads.NONE;
    /** Only touched by the main thread. */
    private static boolean started;

    private final HostReader reader;
    private final HostHistory history = new HostHistory(SAMPLE_INTERVAL_NANOS);
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(HostMonitor::daemonThread);
    /** Only touched by the sampling thread. */
    private HostCounters previousReading;
    private long previousReadingNanos;

    private HostMonitor(final HostReader reader) {
        this.reader = reader;
    }

    /** Starts the monitor on the first tick, because the sampler must learn which thread is the main thread. */
    public static void install(final MinecraftServer server) {
        if (StartupDoctor.hostMonitor && LinuxFiles.runsOnLinux()) {
            server.addTickable(HostMonitor::startOnce);
        }
    }

    /**
     * The share of the time the main thread wanted a CPU in which it had to wait for one, over a window: 0 is a
     * machine with CPUs to spare, 0.3 means other threads or processes take a third of the time from the main
     * thread. Empty when nothing is sampled (not on Linux, monitor off, or not yet running). Cheap enough to call
     * every tick; the value changes every few seconds.
     */
    public static OptionalDouble mainThreadWaitShare(final HostWindow window) {
        return published.of(window).mainThreadWaitShare();
    }

    static HostLoads loads() {
        return published;
    }

    private static void startOnce() {
        if (started) {
            return;
        }
        started = true;
        HostReader.forCurrentThread(PlatformHooks.get().getBrand()).ifPresent(reader -> new HostMonitor(reader).start());
    }

    private void start() {
        this.sampler.scheduleWithFixedDelay(this::sampleOrStop, 0, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void sampleOrStop() {
        try {
            sample();
        } catch (final RuntimeException e) {
            // A diagnostic must never disturb the server.
            LOGGER.warn("Kindling host monitor stopped", e);
            this.sampler.shutdown();
        }
    }

    private void sample() {
        final long nowNanos = System.nanoTime();
        final HostCounters reading = this.reader.read();
        if (this.previousReading != null) {
            this.history.add(new HostSample(nowNanos - this.previousReadingNanos, reading.since(this.previousReading)));
            published = this.history.loads();
        }
        this.previousReading = reading;
        this.previousReadingNanos = nowNanos;
    }

    private static Thread daemonThread(final Runnable task) {
        final Thread thread = new Thread(task, "Kindling host monitor");
        thread.setDaemon(true);
        return thread;
    }
}
