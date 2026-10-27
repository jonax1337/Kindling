package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalDouble;

/** How often a container's CPU limit stopped the server, from the cgroup's {@code cpu.stat}; a growing counter or its growth. */
record CpuThrottling(long periods, long throttledPeriods, long throttledMicros) {

    CpuThrottling plus(final CpuThrottling other) {
        return new CpuThrottling(this.periods + other.periods, this.throttledPeriods + other.throttledPeriods,
            this.throttledMicros + other.throttledMicros);
    }

    Optional<CpuThrottling> since(final CpuThrottling earlier) {
        final long periods = this.periods - earlier.periods;
        final long throttled = this.throttledPeriods - earlier.throttledPeriods;
        final long micros = this.throttledMicros - earlier.throttledMicros;
        return periods < 0 || throttled < 0 || micros < 0 ? Optional.empty() : Optional.of(new CpuThrottling(periods, throttled, micros));
    }

    /** The share of the scheduling periods in which the limit was hit. */
    OptionalDouble throttledShare() {
        return this.periods <= 0 ? OptionalDouble.empty() : OptionalDouble.of((double) this.throttledPeriods / this.periods);
    }
}
