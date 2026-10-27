package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Runs every check over the facts; checks whose facts are missing drop out. */
public final class Doctor {

    private Doctor() {
    }

    /** Reads the facts of this machine and server, then runs the checks over them. */
    public static List<Finding> examineThisServer() {
        return examine(new DoctorFacts(MachineFactsCollector.collect(), JvmFactsCollector.collect(), ServerSettingsCollector.collect()));
    }

    public static List<Finding> examine(final DoctorFacts facts) {
        final MachineFacts machine = facts.machine();
        final JvmFacts jvm = facts.jvm();
        final ServerSettings server = facts.server();
        return Stream.of(
                CpuChecks.processorCount(machine),
                CpuChecks.containerLimit(machine),
                MemoryChecks.heapHeadroom(jvm, machine),
                MemoryChecks.heapSize(jvm, server),
                JvmChecks.garbageCollector(jvm),
                JvmChecks.compactObjectHeaders(jvm),
                JvmChecks.aotCache(jvm),
                ServerSettingChecks.workerThreads(machine, server),
                MemoryChecks.mallocArenas(machine),
                MemoryChecks.trimNativeHeap(jvm, machine),
                MemoryChecks.transparentHugePages(machine),
                ServerSettingChecks.viewDistance(machine, server),
                ServerSettingChecks.proxyCompression(server))
            .flatMap(Optional::stream)
            .toList();
    }
}
