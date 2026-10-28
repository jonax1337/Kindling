package net.kindlingmc.memory;

import com.mojang.logging.LogUtils;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.config.modules.opt.IdleMemory;
import org.slf4j.Logger;

/**
 * Gives memory back while nobody is online. A daemon thread of its own watches the player count, because the server
 * stops ticking when it pauses for lack of players. The player list is a copy-on-write list and safe to read from
 * another thread.
 */
public final class IdleMemoryWatcher {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long CHECK_INTERVAL_SECONDS = 5;

    private final long afterSeconds;
    private final IdlePeriod period;
    private final IntSupplier playerCount;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(IdleMemoryWatcher::daemonThread);

    private IdleMemoryWatcher(final long afterSeconds, final IntSupplier playerCount) {
        this.afterSeconds = afterSeconds;
        this.period = new IdlePeriod(TimeUnit.SECONDS.toNanos(afterSeconds));
        this.playerCount = playerCount;
    }

    public static void install(final MinecraftServer server) {
        if (IdleMemory.enabled) {
            new IdleMemoryWatcher(IdleMemory.afterSeconds, server::getPlayerCount).start();
        }
    }

    private void start() {
        this.scheduler.scheduleWithFixedDelay(this::checkOrStop, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void checkOrStop() {
        try {
            if (this.period.shouldReclaim(System.nanoTime(), this.playerCount.getAsInt() > 0)) {
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
