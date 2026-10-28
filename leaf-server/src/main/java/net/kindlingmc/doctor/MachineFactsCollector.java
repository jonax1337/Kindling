package net.kindlingmc.doctor;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.kindlingmc.os.OperatingSystem;

/** Reads what the machine or container offers; everything that only Linux knows stays empty elsewhere. */
final class MachineFactsCollector {

    private static final Path CGROUP_CPU_MAX = Path.of("/sys/fs/cgroup/cpu.max");
    private static final Path CGROUP_MEMORY_MAX = Path.of("/sys/fs/cgroup/memory.max");
    private static final Path TRANSPARENT_HUGE_PAGES = Path.of("/sys/kernel/mm/transparent_hugepage/enabled");

    private MachineFactsCollector() {
    }

    static MachineFacts collect() {
        final boolean linux = LinuxFiles.runsOnLinux();
        return new MachineFacts(
            Runtime.getRuntime().availableProcessors(),
            linux ? cgroupCpuLimit() : OptionalDouble.empty(),
            memoryBytes(linux),
            linux ? Optional.of(!OperatingSystem.usesMusl()) : Optional.empty(),
            mallocArenaMax(),
            Optional.ofNullable(System.getenv("LD_PRELOAD")),
            linux ? transparentHugePages() : Optional.empty());
    }

    /** The malloc arena limit from {@code MALLOC_ARENA_MAX}, or else from {@code GLIBC_TUNABLES}. */
    private static Optional<String> mallocArenaMax() {
        return Optional.ofNullable(System.getenv("MALLOC_ARENA_MAX"))
            .or(() -> Optional.ofNullable(System.getenv("GLIBC_TUNABLES")).flatMap(LinuxFiles::parseArenaMaxTunable));
    }

    private static OptionalDouble cgroupCpuLimit() {
        return LinuxFiles.read(CGROUP_CPU_MAX).map(LinuxFiles::parseCpuMax).orElse(OptionalDouble.empty());
    }

    private static OptionalLong memoryBytes(final boolean linux) {
        final long physical = ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os ? os.getTotalMemorySize() : 0;
        final OptionalLong containerLimit = linux
            ? LinuxFiles.read(CGROUP_MEMORY_MAX).map(LinuxFiles::parseMemoryMax).orElse(OptionalLong.empty())
            : OptionalLong.empty();
        return physical <= 0 ? containerLimit : OptionalLong.of(Math.min(physical, containerLimit.orElse(physical)));
    }

    private static Optional<String> transparentHugePages() {
        return LinuxFiles.read(TRANSPARENT_HUGE_PAGES).flatMap(LinuxFiles::parseSelectedMode);
    }
}
