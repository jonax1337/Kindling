package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;

/** Checks of the heap size and of the memory the JVM uses outside the heap. */
final class MemoryChecks {

    private static final double MAX_HEAP_SHARE_OF_MEMORY = 0.75;
    private static final long MIN_HEAP_BYTES = ByteSize.GIB;
    private static final long COMFORTABLE_HEAP_BYTES = 2 * ByteSize.GIB;
    private static final int DEMANDING_VIEW_DISTANCE = 12;
    private static final int DEMANDING_PLAYER_SLOTS = 50;
    private static final long TRIM_INTERVAL_MILLIS = 60_000;
    private static final int MIN_JAVA_WITH_TRIM = 25;
    private static final int GLIBC_ARENAS_PER_CORE = 8;
    private static final String HUGE_PAGES_ALWAYS = "always";
    /** Runtime.maxMemory() is one survivor space below -Xmx with the Parallel and Serial collectors. */
    private static final double HEAP_REPORTING_TOLERANCE = 0.95;
    private static final List<String> ALTERNATIVE_ALLOCATORS = List.of("jemalloc", "tcmalloc");

    private MemoryChecks() {
    }

    static Optional<Finding> heapHeadroom(final JvmFacts jvm, final MachineFacts machine) {
        if (machine.memoryBytes().isEmpty()) {
            return Optional.empty();
        }
        final long memory = machine.memoryBytes().getAsLong();
        final String sizes = "-Xmx is " + ByteSize.format(jvm.maxHeapBytes()) + " of " + ByteSize.format(memory);
        if (jvm.maxHeapBytes() <= memory * MAX_HEAP_SHARE_OF_MEMORY) {
            return Optional.of(Finding.ok(sizes + ", which leaves room for native memory"));
        }
        return Optional.of(Finding.warn(
            sizes + ": metaspace, thread stacks, the code cache and direct buffers need memory outside the heap, "
                + "so the system may swap or kill the process",
            "Lower it to " + ByteSize.asXmx((long) (memory * MAX_HEAP_SHARE_OF_MEMORY)) + " or less, or give the machine more memory",
            Basis.GENERAL_ADVICE));
    }

    static Optional<Finding> heapSize(final JvmFacts jvm, final ServerSettings server) {
        final long heap = jvm.maxHeapBytes();
        final boolean demanding = server.viewDistance() >= DEMANDING_VIEW_DISTANCE || server.maxPlayers() >= DEMANDING_PLAYER_SLOTS;
        final long wanted = demanding ? COMFORTABLE_HEAP_BYTES : MIN_HEAP_BYTES;
        if (heap >= wanted * HEAP_REPORTING_TOLERANCE) {
            return Optional.of(Finding.ok("-Xmx " + ByteSize.format(heap) + " is enough for view distance "
                + server.viewDistance() + " and " + server.maxPlayers() + " player slots"));
        }
        return Optional.of(Finding.warn(
            "-Xmx is only " + ByteSize.format(heap) + " for view distance " + server.viewDistance() + " and "
                + server.maxPlayers() + " player slots: the garbage collector will run constantly",
            "Set at least " + ByteSize.asXmx(wanted) + " (and the same value for -Xms)",
            Basis.GENERAL_ADVICE));
    }

    static Optional<Finding> mallocArenas(final MachineFacts machine) {
        if (!machine.glibc().orElse(false) || usesAlternativeAllocator(machine)) {
            return Optional.empty();
        }
        if (machine.mallocArenaMax().isPresent()) {
            return Optional.of(Finding.ok("MALLOC_ARENA_MAX is set to " + machine.mallocArenaMax().get()));
        }
        return Optional.of(Finding.warn(
            "MALLOC_ARENA_MAX is not set: glibc creates up to " + GLIBC_ARENAS_PER_CORE * machine.availableProcessors()
                + " malloc arenas (" + GLIBC_ARENAS_PER_CORE + " per processor) that each keep freed memory, so the process grows beyond the heap",
            "Set the environment variable MALLOC_ARENA_MAX=2 in the script that starts the server",
            Basis.GENERAL_ADVICE));
    }

    private static boolean usesAlternativeAllocator(final MachineFacts machine) {
        return machine.preloadedLibraries().filter(libraries -> ALTERNATIVE_ALLOCATORS.stream().anyMatch(libraries::contains)).isPresent();
    }

    static Optional<Finding> trimNativeHeap(final JvmFacts jvm, final MachineFacts machine) {
        if (jvm.javaFeatureVersion() < MIN_JAVA_WITH_TRIM || !machine.glibc().orElse(false)
            || jvm.trimNativeHeapIntervalMillis().isEmpty()) {
            return Optional.empty();
        }
        if (jvm.trimNativeHeapIntervalMillis().getAsLong() > 0) {
            return Optional.of(Finding.ok("-XX:TrimNativeHeapInterval is " + jvm.trimNativeHeapIntervalMillis().getAsLong() + " ms"));
        }
        return Optional.of(Finding.info(
            "The JVM never hands freed native memory back to the system (-XX:TrimNativeHeapInterval is off); "
                + "Kindling measured a single trim freeing about 100 MB per server JVM",
            "Add -XX:TrimNativeHeapInterval=" + TRIM_INTERVAL_MILLIS + " (the interval is a suggestion; only the one-off trim was measured)",
            Basis.KINDLING_MEASUREMENT));
    }

    static Optional<Finding> transparentHugePages(final MachineFacts machine) {
        if (machine.transparentHugePages().isEmpty()) {
            return Optional.empty();
        }
        final String mode = machine.transparentHugePages().get();
        if (!mode.equals(HUGE_PAGES_ALWAYS)) {
            return Optional.of(Finding.ok("Transparent huge pages are \"" + mode + "\""));
        }
        return Optional.of(Finding.warn(
            "Transparent huge pages are \"always\": the kernel backs every mapping with 2 MB pages, which inflates the memory the server keeps",
            "Run: echo madvise > /sys/kernel/mm/transparent_hugepage/enabled (as root), and add transparent_hugepage=madvise to the kernel command line to keep it after a reboot",
            Basis.GENERAL_ADVICE));
    }
}
