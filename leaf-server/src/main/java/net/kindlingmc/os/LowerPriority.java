package net.kindlingmc.os;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dreeam.leaf.config.modules.opt.WorkerPriority;
import org.slf4j.Logger;

/**
 * Lets the chunk system's worker and I/O threads give way to the main thread when the CPUs are busy. Paper asks for
 * this with Java thread priorities, which Windows honours but HotSpot ignores on Linux; there a thread lowers its own
 * nice value instead ({@link LinuxNice}). A priority is only ever lowered, never raised. Off unless
 * {@code performance.worker-priority.enabled}; read when a thread is created, so a change applies to threads created later.
 */
public final class LowerPriority {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    /** The lowest priority a nice value can ask for. */
    public static final int MAX_NICE = 19;
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private LowerPriority() {
    }

    /** The task of a new worker thread, which first lowers the thread's priority when the setting is on. */
    public static Runnable onStart(final Runnable task) {
        final int nice = WorkerPriority.nice;
        if (!WorkerPriority.enabled || nice <= 0) {
            return task;
        }
        return () -> {
            lowerCurrentThread(nice);
            task.run();
        };
    }

    /** The Java priority that matches a nice value: 0 is normal, {@link #MAX_NICE} the lowest. */
    static int javaPriorityFor(final int nice) {
        final int steps = Thread.NORM_PRIORITY - Thread.MIN_PRIORITY;
        return Thread.NORM_PRIORITY - (int) Math.round((double) Math.min(nice, MAX_NICE) * steps / MAX_NICE);
    }

    /** The lower of a thread's current priority and the one the nice value asks for. */
    static int loweredJavaPriority(final int current, final int nice) {
        return Math.min(current, javaPriorityFor(nice));
    }

    private static void lowerCurrentThread(final int nice) {
        try {
            if (OperatingSystem.isLinux()) {
                LinuxNice.lowerCurrentThread(nice);
            } else {
                final Thread thread = Thread.currentThread();
                thread.setPriority(loweredJavaPriority(thread.getPriority(), nice));
            }
        } catch (final RuntimeException | LinkageError e) {
            // A worker that keeps its priority still works: say so once and go on.
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Kindling could not lower the priority of {} (performance.worker-priority); the workers keep their priority",
                    Thread.currentThread().getName(), e);
            }
        }
    }
}
