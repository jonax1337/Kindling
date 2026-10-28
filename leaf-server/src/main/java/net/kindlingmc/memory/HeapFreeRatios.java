package net.kindlingmc.memory;

import java.util.Optional;
import net.kindlingmc.os.HotSpotFlags;

/**
 * Lowers the free-heap ratios the collector keeps after a full collection, so it hands back everything but a small
 * reserve instead of up to 70 percent of the heap, and restores them afterwards. Both flags are manageable; the
 * minimum must never exceed the maximum, which fixes the order of the changes.
 */
final class HeapFreeRatios {

    private static final String MINIMUM = "MinHeapFreeRatio";
    private static final String MAXIMUM = "MaxHeapFreeRatio";
    private static final int LOW_MINIMUM_PERCENT = 5;
    private static final int LOW_MAXIMUM_PERCENT = 10;

    private final Optional<String> previousMinimum = HotSpotFlags.read(MINIMUM);
    private final Optional<String> previousMaximum = HotSpotFlags.read(MAXIMUM);

    /** Runs the action with low ratios when the JVM allows it, and with the current ones otherwise. */
    static void lowered(final Runnable action) {
        final HeapFreeRatios ratios = new HeapFreeRatios();
        final boolean lowered = ratios.lower();
        try {
            action.run();
        } finally {
            if (lowered) {
                ratios.restore();
            }
        }
    }

    private boolean lower() {
        if (this.previousMinimum.isEmpty() || this.previousMaximum.isEmpty()) {
            return false;
        }
        if (!HotSpotFlags.set(MINIMUM, Integer.toString(LOW_MINIMUM_PERCENT))) {
            return false;
        }
        if (!HotSpotFlags.set(MAXIMUM, Integer.toString(LOW_MAXIMUM_PERCENT))) {
            HotSpotFlags.set(MINIMUM, this.previousMinimum.get());
            return false;
        }
        return true;
    }

    private void restore() {
        HotSpotFlags.set(MAXIMUM, this.previousMaximum.get());
        HotSpotFlags.set(MINIMUM, this.previousMinimum.get());
    }
}
