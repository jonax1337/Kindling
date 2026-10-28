package net.kindlingmc.memory;

import com.mojang.logging.LogUtils;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.kindlingmc.pregen.Pregenerator;
import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.config.modules.opt.IdleMemory;
import org.slf4j.Logger;

/**
 * Gives memory back while nobody is online and no pregeneration is working. A daemon thread of its own watches both,
 * because the server stops ticking when it pauses for lack of players. The player list is a copy-on-write list and the
 * pregenerator publishes its state for other threads, so both are safe to read here.
 */
public final class IdleMemoryWatcher {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long CHECK_INTERVAL_SECONDS = 5;

    private final long afterSeconds;
    private final IdlePeriod period;
    private final BooleanSupplier busy;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(IdleMemoryWatcher::daemonThread);

    private IdleMemoryWatcher(final long afterSeconds, final BooleanSupplier busy) {
        this.afterSeconds = afterSeconds;
        this.period = new IdlePeriod(TimeUnit.SECONDS.toNanos(afterSeconds));
        this.busy = busy;
    }

    public static void install(final MinecraftServer server) {
        if (IdleMemory.enabled) {
            new IdleMemoryWatcher(IdleMemory.afterSeconds, () -> server.getPlayerCount() > 0 || Pregenerator.isWorking()).start();
        }
    }

    private void start() {
        this.scheduler.scheduleWithFixedDelay(this::checkOrStop, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void checkOrStop() {
        try {
            if (this.period.shouldReclaim(System.nanoTime(), this.busy.getAsBoolean())) {
                IdleReclaim.run(this.afterSeconds);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final RuntimeException | LinkageError e) {
            // Giving memory back is optional and must never disturb the server.
            LOGGER.warn("Kindling idle memory stopped", e);
            this.scheduler.shutdown();
        }
    }

    private static Thread daemonThread(final Runnable task) {
        final Thread thread = new Thread(task, "Kindling idle memory");
        thread.setDaemon(true);
        return thread;
    }
}
