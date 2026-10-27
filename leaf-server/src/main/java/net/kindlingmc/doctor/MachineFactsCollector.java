package net.kindlingmc.doctor;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Reads what the machine or container offers; everything that only Linux knows stays empty elsewhere. */
final class MachineFactsCollector {

    private static final Path CGROUP_CPU_MAX = Path.of("/sys/fs/cgroup/cpu.max");
    private static final Path CGROUP_MEMORY_MAX = Path.of("/sys/fs/cgroup/memory.max");
    private static final Path TRANSPARENT_HUGE_PAGES = Path.of("/sys/kernel/mm/transparent_hugepage/enabled");
    private static final Path LIBRARY_DIRECTORY = Path.of("/lib");

    private MachineFactsCollector() {
    }

    static MachineFacts collect() {
        final boolean linux = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
        return new MachineFacts(
            Runtime.getRuntime().availableProcessors(),
            linux ? cgroupCpuLimit() : OptionalDouble.empty(),
            memoryBytes(linux),
            linux ? Optional.of(!usesMusl()) : Optional.empty(),
            Optional.ofNullable(System.getenv("MALLOC_ARENA_MAX")),
            linux ? transparentHugePages() : Optional.empty());
    }

    private static OptionalDouble cgroupCpuLimit() {
        return LinuxFiles.read(CGROUP_CPU_MAX).map(LinuxFiles::parseCpuMax).orElse(OptionalDouble.empty());
    }

    private static OptionalLong memoryBytes(final boolean linux) {
        final long physical = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        final OptionalLong containerLimit = linux
            ? LinuxFiles.read(CGROUP_MEMORY_MAX).map(LinuxFiles::parseMemoryMax).orElse(OptionalLong.empty())
            : OptionalLong.empty();
        return physical <= 0 ? containerLimit : OptionalLong.of(Math.min(physical, containerLimit.orElse(physical)));
    }

    private static Optional<String> transparentHugePages() {
        return LinuxFiles.read(TRANSPARENT_HUGE_PAGES).flatMap(LinuxFiles::parseSelectedMode);
    }

    private static boolean usesMusl() {
        try (DirectoryStream<Path> loaders = Files.newDirectoryStream(LIBRARY_DIRECTORY, "ld-musl-*")) {
            return loaders.iterator().hasNext();
        } catch (final IOException e) {
            return false;
        }
    }
}
