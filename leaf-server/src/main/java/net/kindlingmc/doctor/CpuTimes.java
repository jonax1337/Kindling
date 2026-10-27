package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalDouble;

/** CPU time of the whole machine in clock ticks, from the first line of {@code /proc/stat}; a growing counter or its growth. */
record CpuTimes(long totalTicks, long idleTicks, long stealTicks) {

    CpuTimes plus(final CpuTimes other) {
        return new CpuTimes(this.totalTicks + other.totalTicks, this.idleTicks + other.idleTicks, this.stealTicks + other.stealTicks);
    }

    Optional<CpuTimes> since(final CpuTimes earlier) {
        final long total = this.totalTicks - earlier.totalTicks;
        final long idle = this.idleTicks - earlier.idleTicks;
        final long steal = this.stealTicks - earlier.stealTicks;
        return total < 0 || idle < 0 || steal < 0 ? Optional.empty() : Optional.of(new CpuTimes(total, idle, steal));
    }

    /** The share of all CPU time that the hypervisor gave to other virtual machines. */
    OptionalDouble stealShare() {
        return share(this.stealTicks);
    }

    OptionalDouble idleShare() {
        return share(this.idleTicks);
    }

    private OptionalDouble share(final long ticks) {
        return this.totalTicks <= 0 ? OptionalDouble.empty() : OptionalDouble.of((double) ticks / this.totalTicks);
    }
}
