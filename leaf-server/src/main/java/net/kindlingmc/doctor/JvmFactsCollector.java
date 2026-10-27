package net.kindlingmc.doctor;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** Reads how this JVM was started. */
final class JvmFactsCollector {

    private static final Map<String, GarbageCollector> COLLECTOR_FLAGS = Map.of(
        "UseG1GC", GarbageCollector.G1,
        "UseZGC", GarbageCollector.ZGC,
        "UseShenandoahGC", GarbageCollector.SHENANDOAH,
        "UseParallelGC", GarbageCollector.PARALLEL,
        "UseSerialGC", GarbageCollector.SERIAL);

    private JvmFactsCollector() {
    }

    static JvmFacts collect() {
        return new JvmFacts(
            Runtime.version().feature(),
            Runtime.getRuntime().maxMemory(),
            activeCollector(),
            vmOption("UseCompactObjectHeaders").map(Boolean::parseBoolean),
            trimNativeHeapInterval(),
            ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    private static Optional<GarbageCollector> activeCollector() {
        return COLLECTOR_FLAGS.entrySet().stream()
            .filter(flag -> vmOption(flag.getKey()).map(Boolean::parseBoolean).orElse(false))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    private static OptionalLong trimNativeHeapInterval() {
        return vmOption("TrimNativeHeapInterval").map(value -> OptionalLong.of(Long.parseLong(value))).orElse(OptionalLong.empty());
    }

    /** The value of a HotSpot flag; empty when this JVM does not have it. */
    private static Optional<String> vmOption(final String name) {
        try {
            final HotSpotDiagnosticMXBean diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return diagnostics == null ? Optional.empty() : Optional.of(diagnostics.getVMOption(name).getValue());
        } catch (final IllegalArgumentException | LinkageError e) {
            return Optional.empty();
        }
    }
}
