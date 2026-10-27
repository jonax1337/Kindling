package net.kindlingmc.doctor;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Reads the counters of {@link HostCounters} from {@code /proc} and the cgroup files; Linux only. */
final class HostReader {

    private static final Path TASKS = Path.of("/proc/self/task");
    private static final Path THREAD_SELF = Path.of("/proc/thread-self");
    private static final Path CPU_STAT = Path.of("/proc/stat");
    private static final Path SELF_CGROUP = Path.of("/proc/self/cgroup");
    private static final Path CGROUP_ROOT = Path.of("/sys/fs/cgroup");
    /** Linux keeps the first 15 characters of a thread's name. */
    private static final int THREAD_NAME_LENGTH = 15;
    private static final String CHUNK_WORKER_NAME_SUFFIX = " Common Worker #";

    private final Path mainThreadSchedstat;
    private final String chunkWorkerNamePrefix;
    private final Optional<Path> throttlingFile;

    HostReader(final long mainThreadId, final String brand) {
        this.mainThreadSchedstat = TASKS.resolve(Long.toString(mainThreadId)).resolve("schedstat");
        this.chunkWorkerNamePrefix = chunkWorkerNamePrefix(brand);
        this.throttlingFile = findThrottlingFile();
    }

    /** The reader for the calling thread, which must be the main thread; empty when the kernel does not say which thread that is. */
    static Optional<HostReader> forCurrentThread(final String brand) {
        try {
            final OptionalLong threadId = LinuxFiles.parseThreadId(Files.readSymbolicLink(THREAD_SELF).toString());
            return threadId.isPresent() ? Optional.of(new HostReader(threadId.getAsLong(), brand)) : Optional.empty();
        } catch (final IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** How Linux shows the name of a chunk worker thread, as the chunk system names it: "<brand> Common Worker #<n>". */
    static String chunkWorkerNamePrefix(final String brand) {
        final String name = brand + CHUNK_WORKER_NAME_SUFFIX;
        return name.substring(0, Math.min(name.length(), THREAD_NAME_LENGTH));
    }

    HostCounters read() {
        return new HostCounters(
            LinuxFiles.read(this.mainThreadSchedstat).flatMap(LinuxFiles::parseSchedstat),
            chunkWorkers(),
            LinuxFiles.read(CPU_STAT).flatMap(LinuxFiles::parseCpuTimes),
            this.throttlingFile.flatMap(file -> LinuxFiles.read(file).flatMap(LinuxFiles::parseCpuStat)));
    }

    private Optional<SchedTime> chunkWorkers() {
        final List<SchedTime> workers = new ArrayList<>();
        try (DirectoryStream<Path> tasks = Files.newDirectoryStream(TASKS)) {
            for (final Path task : tasks) {
                if (isChunkWorker(task)) {
                    LinuxFiles.read(task.resolve("schedstat")).flatMap(LinuxFiles::parseSchedstat).ifPresent(workers::add);
                }
            }
        } catch (final IOException e) {
            return Optional.empty();
        }
        return workers.stream().reduce(SchedTime::plus);
    }

    private boolean isChunkWorker(final Path task) {
        return LinuxFiles.read(task.resolve("stat"))
            .flatMap(LinuxFiles::parseThreadName)
            .filter(name -> name.startsWith(this.chunkWorkerNamePrefix))
            .isPresent();
    }

    /** The first {@code cpu.stat} that has throttling counters: this process's own cgroup (v2), then the root of v2 and v1. */
    private static Optional<Path> findThrottlingFile() {
        final List<Path> candidates = new ArrayList<>();
        LinuxFiles.read(SELF_CGROUP).flatMap(LinuxFiles::parseCgroupV2Path)
            .ifPresent(cgroup -> candidates.add(CGROUP_ROOT.resolve(cgroup.replaceFirst("^/+", "")).resolve("cpu.stat")));
        candidates.add(CGROUP_ROOT.resolve("cpu.stat"));
        candidates.add(CGROUP_ROOT.resolve("cpu").resolve("cpu.stat"));
        candidates.add(CGROUP_ROOT.resolve("cpu,cpuacct").resolve("cpu.stat"));
        return candidates.stream()
            .filter(file -> LinuxFiles.read(file).flatMap(LinuxFiles::parseCpuStat).isPresent())
            .findFirst();
    }
}
