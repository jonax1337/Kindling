package net.kindlingmc.doctor;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import net.kindlingmc.os.ThreadNice;
import org.dreeam.leaf.config.modules.opt.WorkerPriority;

/** Reads the worker priority setting and, on Linux, the nice value of one chunk worker thread. */
final class WorkerPriorityCollector {

    private static final Path TASKS = Path.of("/proc/self/task");

    private WorkerPriorityCollector() {
    }

    static WorkerPriorityFacts collect() {
        return new WorkerPriorityFacts(WorkerPriority.enabled, WorkerPriority.nice,
            LinuxFiles.runsOnLinux() ? chunkWorkerNice() : OptionalInt.empty());
    }

    private static OptionalInt chunkWorkerNice() {
        final String workerName = HostReader.chunkWorkerNamePrefix(PlatformHooks.get().getBrand());
        try (DirectoryStream<Path> tasks = Files.newDirectoryStream(TASKS)) {
            for (final Path task : tasks) {
                final Optional<String> stat = LinuxFiles.read(task.resolve("stat"));
                if (stat.flatMap(LinuxFiles::parseThreadName).filter(name -> name.startsWith(workerName)).isPresent()) {
                    return ThreadNice.parse(stat.get());
                }
            }
        } catch (final IOException e) {
            return OptionalInt.empty();
        }
        return OptionalInt.empty();
    }
}
