package net.kindlingmc.memory;

import java.util.concurrent.atomic.AtomicReference;
import net.kindlingmc.os.HotSpotFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeapFreeRatiosTest {

    @Test
    void theRatiosAreLowOnlyWhileTheActionRunsAndThenRestored() {
        final String minimum = HotSpotFlags.read("MinHeapFreeRatio").orElseThrow();
        final String maximum = HotSpotFlags.read("MaxHeapFreeRatio").orElseThrow();
        final AtomicReference<String> maximumInside = new AtomicReference<>();

        HeapFreeRatios.lowered(() -> maximumInside.set(HotSpotFlags.read("MaxHeapFreeRatio").orElseThrow()));

        assertEquals("10", maximumInside.get());
        assertEquals(minimum, HotSpotFlags.read("MinHeapFreeRatio").orElseThrow());
        assertEquals(maximum, HotSpotFlags.read("MaxHeapFreeRatio").orElseThrow());
    }

    @Test
    void theRatiosAreRestoredWhenTheActionFails() {
        final String maximum = HotSpotFlags.read("MaxHeapFreeRatio").orElseThrow();

        assertThrows(IllegalStateException.class, () -> HeapFreeRatios.lowered(() -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals(maximum, HotSpotFlags.read("MaxHeapFreeRatio").orElseThrow());
    }
}
