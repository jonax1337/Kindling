package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * What the machine or container offers. An empty value means "could not be read here" (for example any Linux-only
 * value on Windows); a check that needs it is skipped.
 *
 * @param availableProcessors   what the JVM sees
 * @param cgroupCpuLimit        CPUs granted by the container, fractions allowed
 * @param memoryBytes           physical memory, or the container limit when that is smaller
 * @param glibc                 whether the C library is glibc (as opposed to musl)
 * @param mallocArenaMax        the environment variable {@code MALLOC_ARENA_MAX}
 * @param transparentHugePages  the selected mode of {@code /sys/kernel/mm/transparent_hugepage/enabled}
 */
public record MachineFacts(
    int availableProcessors,
    OptionalDouble cgroupCpuLimit,
    OptionalLong memoryBytes,
    Optional<Boolean> glibc,
    Optional<String> mallocArenaMax,
    Optional<String> transparentHugePages
) {

    /** The processors the server can really use: the container limit counts when it is below what the JVM sees. */
    public int usableProcessors() {
        if (this.cgroupCpuLimit.isEmpty()) {
            return this.availableProcessors;
        }
        final int wholeLimit = (int) Math.max(1, Math.ceil(this.cgroupCpuLimit.getAsDouble()));
        return Math.min(wholeLimit, this.availableProcessors);
    }
}
