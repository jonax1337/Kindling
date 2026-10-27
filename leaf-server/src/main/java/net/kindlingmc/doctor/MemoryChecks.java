package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;

/** Checks of the heap size and of the memory the JVM uses outside the heap. */
final class MemoryChecks {

    /** What the process needs outside the heap: Kindling measured about 500 MB at -Xmx2G; the rest is margin. */
    private static final long NATIVE_OVERHEAD_BYTES = 600 * ByteSize.MIB;
    private static final HeapTiers SURVIVAL_HEAP = new HeapTiers(1536 * ByteSize.MIB, 2 * ByteSize.GIB, Basis.KINDLING_MEASUREMENT);
    private static final HeapTiers DEMANDING_HEAP = new HeapTiers(2 * ByteSize.GIB, 3 * ByteSize.GIB, Basis.GENERAL_ADVICE);
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

    /** Heap sizes below which a heap is too small (a warning) or smaller than advised (a hint). */
    private record HeapTiers(long warnBelowBytes, long adviseBelowBytes, Basis basis) {
    }

    static Optional<Finding> heapHeadroom(final JvmFacts jvm, final MachineFacts machine) {
        if (machine.memoryBytes().isEmpty()) {
            return Optional.empty();
        }
        final long memory = machine.memoryBytes().getAsLong();
        final String sizes = "-Xmx is " + ByteSize.format(jvm.maxHeapBytes()) + " of " + ByteSize.format(memory);
        if (jvm.maxHeapBytes() + NATIVE_OVERHEAD_BYTES <= memory) {
            return Optional.of(Finding.ok(sizes + ", which leaves room for the " + ByteSize.format(NATIVE_OVERHEAD_BYTES) + " the process needs beyond the heap"));
        }
        final long affordableHeap = memory - NATIVE_OVERHEAD_BYTES;
        return Optional.of(Finding.warn(
            sizes + ": besides the heap the process needs about " + ByteSize.format(NATIVE_OVERHEAD_BYTES) + " (Kindling measured about 500 MiB at -Xmx2G) "
                + "for metaspace, thread stacks, the code cache and direct buffers, so the system may swap or kill it; "
                + "other servers on this machine take their share of the same memory",
            (affordableHeap > 0 ? "Lower it to " + ByteSize.asXmx(affordableHeap) + " or less, or give the machine more memory" : "Give the machine more memory")
                + "; with several servers on one machine, every heap plus " + ByteSize.format(NATIVE_OVERHEAD_BYTES) + " must fit together",
            Basis.KINDLING_MEASUREMENT));
    }

    static Optional<Finding> heapSize(final JvmFacts jvm, final ServerSettings server) {
        final long heap = jvm.maxHeapBytes();
        final boolean demanding = server.viewDistance() >= DEMANDING_VIEW_DISTANCE || server.maxPlayers() >= DEMANDING_PLAYER_SLOTS;
        final HeapTiers tiers = demanding ? DEMANDING_HEAP : SURVIVAL_HEAP;
        final String load = "view distance " + server.viewDistance() + " and " + server.maxPlayers() + " player slots";
        if (heap >= tiers.adviseBelowBytes() * HEAP_REPORTING_TOLERANCE) {
            return Optional.of(Finding.ok("-Xmx " + ByteSize.format(heap) + " is enough for " + load));
        }
        final String measurement = "Kindling measured about 1.2 GB of live data at view distance 10 with 8 players";
        final String fix = "Set at least " + ByteSize.asXmx(tiers.adviseBelowBytes()) + " (and the same value for -Xms)";
        if (heap >= tiers.warnBelowBytes() * HEAP_REPORTING_TOLERANCE) {
            return Optional.of(Finding.info("-Xmx is " + ByteSize.format(heap) + " for " + load + ": little room above the live data; " + measurement,
                fix, tiers.basis()));
        }
        return Optional.of(Finding.warn("-Xmx is only " + ByteSize.format(heap) + " for " + load + ": the garbage collector will run constantly; " + measurement,
            fix, tiers.basis()));
    }

    static Optional<Finding> mallocArenas(final MachineFacts machine) {
        if (!machine.glibc().orElse(false) || usesAlternativeAllocator(machine)) {
            return Optional.empty();
        }
        if (machine.mallocArenaMax().isPresent()) {
            return Optional.of(Finding.ok("The malloc arena limit is " + machine.mallocArenaMax().get() + " (MALLOC_ARENA_MAX or GLIBC_TUNABLES)"));
        }
        return Optional.of(Finding.warn(
            "The malloc arena limit is not set (neither MALLOC_ARENA_MAX nor glibc.malloc.arena_max in GLIBC_TUNABLES): glibc creates up to " + GLIBC_ARENAS_PER_CORE * machine.availableProcessors()
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
                + "Kindling measured a single trim freeing about 100 MB per server JVM (101 MB and 103 MB on the Cubyx VPS)",
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
