package net.kindlingmc.doctor;

import java.util.OptionalInt;

/**
 * The setting {@code performance.worker-priority} and what became of it.
 *
 * @param workerNice  the nice value a running chunk worker thread has; empty where it cannot be read (not on Linux)
 */
public record WorkerPriorityFacts(boolean enabled, int nice, OptionalInt workerNice) {
}
