package net.kindlingmc.memory;

import com.mojang.logging.LogUtils;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import net.kindlingmc.doctor.ByteSize;
import net.kindlingmc.os.HotSpotFlags;
import net.kindlingmc.os.OperatingSystem;
import org.slf4j.Logger;

/** Gives memory back to the system: a full collection that shrinks the heap, then a trim of the native heap. */
final class IdleReclaim {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int MIN_JAVA_WITH_TRIM = 25;
    /** The collector uncommits the freed heap in the background; the resident set only drops after it is done. */
    private static final long SETTLE_MILLIS = TimeUnit.SECONDS.toMillis(10);

    private IdleReclaim() {
    }

    /** Runs on the idle watcher's thread and returns after logging what it gave back. */
    static void run(final long idleSeconds) throws InterruptedException {
        final OptionalLong residentBefore = ResidentMemory.currentBytes();
        final long committedBefore = Runtime.getRuntime().totalMemory();
        HeapFreeRatios.lowered(() -> DiagnosticCommands.run("gcRun"));
        final boolean trimmed = canTrimNativeHeap() && DiagnosticCommands.run("systemTrimNativeHeap");
        Thread.sleep(SETTLE_MILLIS);
        LOGGER.info("No players for {} s: ran a full collection{}; heap {} -> {}{}", idleSeconds, trimmed ? " and trimmed the native heap" : "",
            ByteSize.format(committedBefore), ByteSize.format(Runtime.getRuntime().totalMemory()), residentChange(residentBefore));
    }

    private static String residentChange(final OptionalLong before) {
        final OptionalLong after = ResidentMemory.currentBytes();
        return before.isPresent() && after.isPresent()
            ? ", resident memory " + ByteSize.format(before.getAsLong()) + " -> " + ByteSize.format(after.getAsLong())
            : "";
    }

    /** Only glibc has the trim, and the JVM's own periodic trim (set by the operator) makes ours redundant. */
    private static boolean canTrimNativeHeap() {
        return OperatingSystem.isLinux() && !OperatingSystem.usesMusl() && Runtime.version().feature() >= MIN_JAVA_WITH_TRIM
            && !HotSpotFlags.read("TrimNativeHeapInterval").map(IdleReclaim::isPositive).orElse(false);
    }

    private static boolean isPositive(final String interval) {
        return Long.parseLong(interval) > 0;
    }
}
