package net.kindlingmc.bench;

/**
 * A contraption driven by a clock that steps every {@value #STEP_TICKS} ticks, as redstone clocks and farm timers
 * drive the real ones. The run steps it; everything the step sets off is vanilla.
 */
interface Clocked {

    int STEP_TICKS = 10;

    void clockStep();
}
