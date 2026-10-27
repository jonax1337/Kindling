package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A well-tuned Java 25 server on a 16 GiB Linux machine; a test changes the one field it is about. */
final class Scenario {

    int processors = 8;
    OptionalDouble cgroupCpuLimit = OptionalDouble.empty();
    OptionalLong memoryBytes = OptionalLong.of(16 * ByteSize.GIB);
    Optional<Boolean> glibc = Optional.of(true);
    Optional<String> mallocArenaMax = Optional.of("2");
    Optional<String> preloadedLibraries = Optional.empty();
    Optional<String> transparentHugePages = Optional.of("madvise");

    int javaFeatureVersion = 25;
    long maxHeapBytes = 4 * ByteSize.GIB;
    Optional<GarbageCollector> garbageCollector = Optional.of(GarbageCollector.G1);
    Optional<Boolean> compactObjectHeaders = Optional.of(true);
    OptionalLong trimNativeHeapIntervalMillis = OptionalLong.of(60_000);
    List<String> jvmArguments = List.of("-XX:AOTCache=kindling.aot");

    OptionalInt workerThreads = OptionalInt.empty();
    int viewDistance = 10;
    int simulationDistance = 8;
    int maxPlayers = 20;
    boolean behindProxy = false;
    int compressionThreshold = 256;
    ProxyLocation proxyLocation = ProxyLocation.SAME_HOST;
    List<String> recommendedOptionsOff = List.of();

    Optional<DiskSpace> worldDisk = Optional.empty();
    HostLoads live = HostLoads.NONE;
    WorkerPriorityFacts workerPriority = new WorkerPriorityFacts(false, 10, OptionalInt.empty());

    MachineFacts machine() {
        return new MachineFacts(processors, cgroupCpuLimit, memoryBytes, glibc, mallocArenaMax, preloadedLibraries, transparentHugePages);
    }

    JvmFacts jvm() {
        return new JvmFacts(javaFeatureVersion, maxHeapBytes, garbageCollector, compactObjectHeaders,
            trimNativeHeapIntervalMillis, jvmArguments);
    }

    ServerSettings server() {
        return new ServerSettings(workerThreads, viewDistance, simulationDistance, maxPlayers, behindProxy, compressionThreshold,
            proxyLocation, recommendedOptionsOff);
    }

    DoctorFacts facts() {
        return new DoctorFacts(machine(), jvm(), server(), worldDisk, live, workerPriority);
    }

    static void assertSeverity(final Severity expected, final Optional<Finding> finding) {
        assertTrue(finding.isPresent(), "the check was skipped");
        assertEquals(expected, finding.get().severity(), finding.get().what());
    }

    static void assertSkipped(final Optional<Finding> finding) {
        assertTrue(finding.isEmpty(), "the check should have been skipped");
    }
}
