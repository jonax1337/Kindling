package net.kindlingmc.pregen;

import java.util.OptionalDouble;

/**
 * How busy the server is at a tick, which decides how many chunks a pregeneration may have in flight.
 *
 * @param mainThreadWait the main thread's share of waiting for a CPU over the last minute; empty when not measured
 */
record TickLoad(long tick, long averageTickNanos, OptionalDouble mainThreadWait) {
}
