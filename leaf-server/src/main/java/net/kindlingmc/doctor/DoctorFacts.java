package net.kindlingmc.doctor;

import java.util.Optional;

/**
 * Everything the checks look at, collected once so that the checks themselves stay pure.
 *
 * @param worldDisk  the file store of the worlds, when it could be read
 * @param live       what the host monitor measured; empty loads when it is not running
 */
public record DoctorFacts(MachineFacts machine, JvmFacts jvm, ServerSettings server, Optional<DiskSpace> worldDisk, HostLoads live,
                          WorkerPriorityFacts workerPriority, WorkerGovernorFacts workerGovernor) {
}
