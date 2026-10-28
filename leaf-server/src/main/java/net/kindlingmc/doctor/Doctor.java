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
        return examine(new DoctorFacts(MachineFactsCollector.collect(), JvmFactsCollector.collect(), ServerSettingsCollector.collect(),
            DiskSpaceCollector.collect(), HostMonitor.loads(), WorkerPriorityCollector.collect(),
            WorkerGovernorFacts.ofThisServer()));
    }

    public static List<Finding> examine(final DoctorFacts facts) {
        final MachineFacts machine = facts.machine();
        final JvmFacts jvm = facts.jvm();
        final ServerSettings server = facts.server();
        final HostLoads live = facts.live();
        return Stream.of(
                CpuChecks.processorCount(machine),
                CpuChecks.containerLimit(machine),
                MemoryChecks.heapHeadroom(jvm, machine),
                MemoryChecks.heapSize(jvm, server),
                MemoryChecks.fixedHeap(jvm, server),
                JvmChecks.garbageCollector(jvm),
                JvmChecks.compactObjectHeaders(jvm),
                JvmChecks.aotCache(jvm),
                ServerSettingChecks.workerThreads(machine, server),
                MemoryChecks.mallocArenas(machine),
                MemoryChecks.trimNativeHeap(jvm, machine),
                MemoryChecks.transparentHugePages(machine),
                ServerSettingChecks.viewDistance(machine, server),
                ServerSettingChecks.proxyCompression(server),
                ServerSettingChecks.recommendedOptions(server),
                ServerSettingChecks.vanillaParity(server),
                StorageChecks.worldDisk(facts.worldDisk()),
                HostChecks.mainThreadWait(live),
                HostChecks.chunkWorkerWait(live),
                HostChecks.hostSteal(live),
                HostChecks.cpuThrottling(live),
                ThreadChecks.workerPriority(facts.workerPriority()),
                ThreadChecks.workerGovernor(facts.workerGovernor()))
            .flatMap(Optional::stream)
            .toList();
    }
}
