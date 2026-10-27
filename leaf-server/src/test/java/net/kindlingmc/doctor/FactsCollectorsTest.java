package net.kindlingmc.doctor;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The collectors read the real JVM and OS of the test machine, so these only check what holds on every machine. */
class FactsCollectorsTest {

    @Test
    void theJvmFactsDescribeTheRunningJvm() {
        final JvmFacts jvm = JvmFactsCollector.collect();

        assertEquals(Runtime.version().feature(), jvm.javaFeatureVersion());
        assertEquals(Runtime.getRuntime().maxMemory(), jvm.maxHeapBytes());
        assertTrue(jvm.maxHeapBytes() > 0);
    }

    @Test
    void theMachineFactsCountTheProcessorsAndKnowTheMemory() {
        final MachineFacts machine = MachineFactsCollector.collect();

        assertEquals(Runtime.getRuntime().availableProcessors(), machine.availableProcessors());
        assertTrue(machine.memoryBytes().orElse(1) > 0);
    }

    @Test
    void checkingTheRealMachineNeverThrows() {
        final DoctorFacts facts = new DoctorFacts(
            MachineFactsCollector.collect(), JvmFactsCollector.collect(), new Scenario().server(), Optional.empty(), HostLoads.NONE,
            new Scenario().workerPriority);

        assertTrue(Doctor.examine(facts).size() >= 5);
    }
}
