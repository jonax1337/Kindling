package net.kindlingmc.doctor;

/** What the host monitor measured during one sampling interval. */
record HostSample(long intervalNanos, HostCounters growth) {

    /** This sample alone, as a window of its own. */
    HostLoad load() {
        return new HostLoad(this.intervalNanos, this.growth);
    }
}
