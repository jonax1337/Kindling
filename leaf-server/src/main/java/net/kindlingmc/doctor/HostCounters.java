package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

/**
 * What the host monitor reads from the kernel, as growing counters or as the growth between two readings. A source
 * the system does not offer, or that could not be read, stays empty.
 *
 * @param mainThread    the server's main thread
 * @param chunkWorkers  all chunk worker threads together
 * @param cpu           the whole machine
 * @param throttling    the container's CPU limit
 */
record HostCounters(
    Optional<SchedTime> mainThread,
    Optional<SchedTime> chunkWorkers,
    Optional<CpuTimes> cpu,
    Optional<CpuThrottling> throttling
) {

    static final HostCounters NONE = new HostCounters(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    HostCounters since(final HostCounters earlier) {
        return new HostCounters(
            difference(this.mainThread, earlier.mainThread, SchedTime::since),
            difference(this.chunkWorkers, earlier.chunkWorkers, SchedTime::since),
            difference(this.cpu, earlier.cpu, CpuTimes::since),
            difference(this.throttling, earlier.throttling, CpuThrottling::since));
    }

    HostCounters plus(final HostCounters other) {
        return new HostCounters(
            merge(this.mainThread, other.mainThread, SchedTime::plus),
            merge(this.chunkWorkers, other.chunkWorkers, SchedTime::plus),
            merge(this.cpu, other.cpu, CpuTimes::plus),
            merge(this.throttling, other.throttling, CpuThrottling::plus));
    }

    private static <T> Optional<T> difference(final Optional<T> now, final Optional<T> before, final BiFunction<T, T, Optional<T>> since) {
        return now.isPresent() && before.isPresent() ? since.apply(now.get(), before.get()) : Optional.empty();
    }

    private static <T> Optional<T> merge(final Optional<T> first, final Optional<T> second, final BinaryOperator<T> sum) {
        return first.isPresent() && second.isPresent() ? Optional.of(sum.apply(first.get(), second.get())) : first.or(() -> second);
    }
}
