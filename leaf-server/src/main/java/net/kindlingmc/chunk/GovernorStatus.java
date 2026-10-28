package net.kindlingmc.chunk;

/** What the worker governor did with the latest host sample, for {@code /kindling doctor}. */
public record GovernorStatus(int workers, GovernorLimits limits, HostPressure pressure) {
}
