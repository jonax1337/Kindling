package org.dreeam.leaf.async;

import net.minecraft.util.Util;
import net.kindlingmc.save.SerialWrites; // Kindling - safe async saving
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;

import java.time.Duration; // Kindling - safe async saving
import java.util.UUID; // Kindling - safe async saving
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncPlayerDataSaving {

    public static ExecutorService IO_POOL = null;
    // Kindling start - safe async saving
    private static final Duration STALL_WARNING_INTERVAL = Duration.ofSeconds(10);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(60);
    private static volatile SerialWrites<UUID> writes = null;
    // Kindling end - safe async saving

    private AsyncPlayerDataSaving() {
    }

    public static void init() {
        if (IO_POOL == null) {
            IO_POOL = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new com.google.common.util.concurrent.ThreadFactoryBuilder()
                    .setPriority(Thread.NORM_PRIORITY - 2)
                    .setNameFormat("Leaf IO Thread")
                    .setUncaughtExceptionHandler(Util::onThreadException)
                    .build(),
                new ThreadPoolExecutor.AbortPolicy() // Kindling - a write queued after shutdown runs on the caller's thread instead of vanishing
            );
            writes = new SerialWrites<>(IO_POOL, STALL_WARNING_INTERVAL); // Kindling - safe async saving
        } else {
            // Temp no-op
            //throw new IllegalStateException();
        }
    }

    // Kindling start - safe async saving
    public static void submit(Runnable runnable) {
        if (!AsyncPlayerDataSave.enabled) {
            runAfterQueuedWrites(runnable);
        } else {
            writes.queue(runnable);
        }
    }

    public static void submit(UUID player, Runnable write) {
        if (!AsyncPlayerDataSave.enabled) {
            runAfterQueuedWrites(write);
        } else {
            writes.queue(player, write);
        }
    }

    private static void runAfterQueuedWrites(Runnable write) {
        awaitQueuedWrites();
        write.run();
    }

    public static void awaitWrite(UUID player) {
        if (writes != null) {
            writes.awaitWrite(player);
        }
    }

    public static void awaitQueuedWrites() {
        if (writes != null) {
            writes.awaitQueuedWrites();
        }
    }

    public static void shutdown() {
        if (writes != null) {
            ShutdownExecutors.LOGGER.info("Waiting for player I/O executor to shutdown...");
            writes.shutdown(SHUTDOWN_TIMEOUT);
        }
    }
    // Kindling end - safe async saving
}
