package net.kindlingmc.doctor;

import java.util.Optional;
import net.kindlingmc.chunk.GovernorStatus;
import net.kindlingmc.chunk.WorkerPoolGovernor;
import org.dreeam.leaf.config.modules.opt.WorkerGovernor;

/**
 * The setting {@code performance.worker-governor} and what the governor did with the latest host sample.
 *
 * @param status  empty while the governor has not run: it is off, not on Linux, the host monitor is off or has no sample yet
 */
public record WorkerGovernorFacts(boolean enabled, Optional<GovernorStatus> status) {

    static WorkerGovernorFacts ofThisServer() {
        return new WorkerGovernorFacts(WorkerGovernor.enabled, WorkerPoolGovernor.status());
    }
}
