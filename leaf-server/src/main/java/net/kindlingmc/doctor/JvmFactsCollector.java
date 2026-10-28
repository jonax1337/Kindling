package net.kindlingmc.doctor;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import net.kindlingmc.os.HotSpotFlags;

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
            HotSpotFlags.read("InitialHeapSize").map(Long::parseLong).orElse(0L),
            activeCollector(),
            HotSpotFlags.read("UseCompactObjectHeaders").map(Boolean::parseBoolean),
            trimNativeHeapInterval(),
            ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    private static Optional<GarbageCollector> activeCollector() {
        return COLLECTOR_FLAGS.entrySet().stream()
            .filter(flag -> HotSpotFlags.read(flag.getKey()).map(Boolean::parseBoolean).orElse(false))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    private static OptionalLong trimNativeHeapInterval() {
        return HotSpotFlags.read("TrimNativeHeapInterval").map(value -> OptionalLong.of(Long.parseLong(value))).orElse(OptionalLong.empty());
    }
}
