package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadChecksTest {

    private static final int NICE = 10;

    @Test
    void anUnreadablePriorityWithTheSettingOffSaysNothing() {
        assertSkipped(ThreadChecks.workerPriority(new WorkerPriorityFacts(false, NICE, OptionalInt.empty())));
    }

    @Test
    void anUnreadablePriorityWithTheSettingOnOnlySaysItIsOn() {
        final Finding finding = ThreadChecks.workerPriority(new WorkerPriorityFacts(true, NICE, OptionalInt.empty())).orElseThrow();

        assertSeverity(Severity.INFO, Optional.of(finding));
        assertTrue(finding.what().startsWith("worker-priority is on"), finding.what());
    }

    @Test
    void workersAtTheMainThreadsPriorityPointToTheSetting() {
        final Finding finding = ThreadChecks.workerPriority(new WorkerPriorityFacts(false, NICE, OptionalInt.of(0))).orElseThrow();

        assertSeverity(Severity.INFO, Optional.of(finding));
        assertTrue(finding.what().contains("nice 0") && finding.what().contains("performance.worker-priority"), finding.what());
    }

    @Test
    void workersAtTheAskedNiceValueShowTheSettingWorks() {
        assertSeverity(Severity.OK, ThreadChecks.workerPriority(new WorkerPriorityFacts(true, NICE, OptionalInt.of(NICE))));
        assertSeverity(Severity.OK, ThreadChecks.workerPriority(new WorkerPriorityFacts(true, NICE, OptionalInt.of(15))));
    }

    @Test
    void workersBelowTheAskedNiceValueAreAWarning() {
        final Finding finding = ThreadChecks.workerPriority(new WorkerPriorityFacts(true, NICE, OptionalInt.of(0))).orElseThrow();

        assertSeverity(Severity.WARN, Optional.of(finding));
        assertTrue(finding.what().contains("nice 10") && finding.what().contains("nice 0"), finding.what());
    }
}
