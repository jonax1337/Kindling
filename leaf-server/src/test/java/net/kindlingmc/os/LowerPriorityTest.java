package net.kindlingmc.os;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowerPriorityTest {

    @Test
    void niceValuesMapOntoTheJavaPrioritiesBelowNormal() {
        assertEquals(Thread.NORM_PRIORITY, LowerPriority.javaPriorityFor(0));
        assertEquals(3, LowerPriority.javaPriorityFor(10));
        assertEquals(Thread.MIN_PRIORITY, LowerPriority.javaPriorityFor(LowerPriority.MAX_NICE));
    }

    @Test
    void aThreadWithALowerPriorityAlreadyKeepsIt() {
        assertEquals(2, LowerPriority.loweredJavaPriority(2, 10));
        assertEquals(3, LowerPriority.loweredJavaPriority(Thread.NORM_PRIORITY, 10));
        assertEquals(3, LowerPriority.loweredJavaPriority(Thread.MAX_PRIORITY, 10));
    }

    @Test
    void aTaskStaysUnwrappedWhileTheSettingIsOff() {
        final Runnable task = () -> { };

        assertEquals(task, LowerPriority.onStart(task));
    }
}
