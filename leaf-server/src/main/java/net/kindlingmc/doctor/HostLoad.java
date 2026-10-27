package net.kindlingmc.doctor;

import java.util.OptionalDouble;

/**
 * What the host monitor saw over one window.
 *
 * @param coveredNanos  how much of the window the samples really cover, which is less right after start-up
 * @param growth        the counters added up over those samples
 */
record HostLoad(long coveredNanos, HostCounters growth) {

    static final HostLoad NONE = new HostLoad(0, HostCounters.NONE);

    long coveredSeconds() {
        return this.coveredNanos / 1_000_000_000L;
    }

    OptionalDouble mainThreadWaitShare() {
        return this.growth.mainThread().map(SchedTime::waitShare).orElse(OptionalDouble.empty());
    }

    OptionalDouble chunkWorkerWaitShare() {
        return this.growth.chunkWorkers().map(SchedTime::waitShare).orElse(OptionalDouble.empty());
    }

    OptionalDouble stealShare() {
        return this.growth.cpu().map(CpuTimes::stealShare).orElse(OptionalDouble.empty());
    }

    OptionalDouble idleShare() {
        return this.growth.cpu().map(CpuTimes::idleShare).orElse(OptionalDouble.empty());
    }

    OptionalDouble throttledShare() {
        return this.growth.throttling().map(CpuThrottling::throttledShare).orElse(OptionalDouble.empty());
    }

    double throttledSeconds() {
        return this.growth.throttling().map(CpuThrottling::throttledMicros).orElse(0L) / 1_000_000.0;
    }
}
