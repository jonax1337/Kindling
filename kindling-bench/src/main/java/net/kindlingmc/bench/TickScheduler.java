package net.kindlingmc.bench;

/** Runs actions on the main thread after a number of server ticks. */
@FunctionalInterface
interface TickScheduler {

    /** @param ticks how many ticks to wait; 0 runs the action in the next tick */
    void runLater(int ticks, Runnable action);
}
