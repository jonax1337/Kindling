package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * How the server JVM was started.
 *
 * @param javaFeatureVersion            for example 25
 * @param maxHeapBytes                  {@code -Xmx}
 * @param garbageCollector              the active collector, when it is one of the known ones
 * @param compactObjectHeaders          the value of {@code UseCompactObjectHeaders}, when the JVM has that flag
 * @param trimNativeHeapIntervalMillis  the value of {@code TrimNativeHeapInterval} (0 is off), when the JVM has that flag
 * @param arguments                     the JVM's input arguments
 */
public record JvmFacts(
    int javaFeatureVersion,
    long maxHeapBytes,
    Optional<GarbageCollector> garbageCollector,
    Optional<Boolean> compactObjectHeaders,
    OptionalLong trimNativeHeapIntervalMillis,
    List<String> arguments
) {
}
