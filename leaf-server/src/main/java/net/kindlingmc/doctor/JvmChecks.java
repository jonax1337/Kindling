package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;

/** Checks of the JVM flags. */
final class JvmChecks {

    private static final int MIN_JAVA_WITH_COMPACT_HEADERS = 25;
    private static final int MIN_JAVA_WITH_SIMPLE_AOT_CACHE = 25;
    private static final List<String> AOT_ARGUMENT_PREFIXES = List.of("-XX:AOTCache=", "-XX:AOTCacheOutput=", "-XX:AOTMode=");

    private JvmChecks() {
    }

    static Optional<Finding> garbageCollector(final JvmFacts jvm) {
        if (jvm.garbageCollector().isEmpty()) {
            return Optional.empty();
        }
        final GarbageCollector collector = jvm.garbageCollector().get();
        return Optional.of(switch (collector) {
            case G1 -> Finding.ok("Garbage collector: G1");
            case ZGC -> Finding.info(
                "Garbage collector: ZGC, which saves about 22 % peak memory and returns memory to the system, but Kindling measured +11 % CPU and worse tick spikes",
                "Use -XX:+UseG1GC unless the memory matters more to you than the tick spikes",
                Basis.KINDLING_MEASUREMENT);
            case SHENANDOAH -> Finding.info(
                "Garbage collector: Shenandoah, which Kindling measured (generational) as slower than G1 with barely less memory",
                "Use -XX:+UseG1GC",
                Basis.KINDLING_MEASUREMENT);
            case PARALLEL, SERIAL -> Finding.warn(
                "Garbage collector: " + collector + ", which stops all threads for every collection and shows as tick lag",
                "Use -XX:+UseG1GC",
                Basis.GENERAL_ADVICE);
        });
    }

    static Optional<Finding> compactObjectHeaders(final JvmFacts jvm) {
        if (jvm.javaFeatureVersion() < MIN_JAVA_WITH_COMPACT_HEADERS || jvm.compactObjectHeaders().isEmpty()) {
            return Optional.empty();
        }
        if (jvm.compactObjectHeaders().get()) {
            return Optional.of(Finding.ok("Compact object headers are on"));
        }
        return Optional.of(Finding.info(
            "Compact object headers are off: Kindling measured live heap -5 % and GC time -17 % with the flag, and no tick cost (with G1)",
            "Add -XX:+UseCompactObjectHeaders",
            Basis.KINDLING_MEASUREMENT));
    }

    static Optional<Finding> aotCache(final JvmFacts jvm) {
        if (jvm.javaFeatureVersion() < MIN_JAVA_WITH_SIMPLE_AOT_CACHE) {
            return Optional.empty();
        }
        if (usesAotCache(jvm)) {
            return Optional.of(Finding.ok("An AOT cache is configured"));
        }
        return Optional.of(Finding.info(
            "No AOT cache: every start loads and links the classes again; on the Cubyx VPS, AOT-cached starts took 9.5 to 10.7 s instead of "
                + "16.9 to 26.9 s (billiards) and 19.2 s instead of 23 to 27 s (smp)",
            "Start the server with a direct class path (java -cp ... instead of java -jar with Paperclip, which loads the server in its own "
                + "class loader, where the cache does not apply); run once with -XX:AOTCacheOutput=kindling.aot, stop the server, then start with "
                + "-XX:AOTCache=kindling.aot; create the cache again after every update of the server jar",
            Basis.KINDLING_MEASUREMENT));
    }

    private static boolean usesAotCache(final JvmFacts jvm) {
        return jvm.arguments().stream().anyMatch(JvmChecks::isAotArgument);
    }

    private static boolean isAotArgument(final String argument) {
        return AOT_ARGUMENT_PREFIXES.stream().anyMatch(argument::startsWith);
    }
}
