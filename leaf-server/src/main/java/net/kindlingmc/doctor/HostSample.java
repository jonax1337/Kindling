package net.kindlingmc.doctor;

/** What the host monitor measured during one sampling interval. */
record HostSample(long intervalNanos, HostCounters growth) {
}
