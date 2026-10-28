package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalInt;
import net.kindlingmc.chunk.GovernorLimits;
import net.kindlingmc.chunk.GovernorStatus;
import net.kindlingmc.chunk.HostPressure;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void anOffGovernorSaysNothing() {
        assertSkipped(ThreadChecks.workerGovernor(new WorkerGovernorFacts(false, Optional.empty())));
    }

    @Test
    void anOnGovernorWithoutHostReadingsSaysWhatItNeeds() {
        final Finding finding = ThreadChecks.workerGovernor(new WorkerGovernorFacts(true, Optional.empty())).orElseThrow();

        assertSeverity(Severity.INFO, Optional.of(finding));
        assertEquals(Basis.LIVE_READING, finding.basis());
        assertTrue(finding.what().contains("Linux") && finding.what().contains("performance.doctor.host-monitor"), finding.what());
    }

    @Test
    void aRunningGovernorShowsItsCountBoundsAndTheLatestReading() {
        final GovernorStatus status = new GovernorStatus(3, GovernorLimits.of(2, 5, 12), HostPressure.HIGH);
        final Finding finding = ThreadChecks.workerGovernor(new WorkerGovernorFacts(true, Optional.of(status))).orElseThrow();

        assertEquals(Basis.LIVE_READING, finding.basis());
        assertTrue(finding.what().startsWith("worker-governor runs 3 chunk worker thread(s), between 2 (Paper's default) and 5"),
            finding.what());
        assertTrue(finding.what().contains("so workers go"), finding.what());
    }

    @Test
    void aGovernorWithoutRoomSaysSo() {
        final GovernorStatus status = new GovernorStatus(2, GovernorLimits.of(2, 2, 12), HostPressure.LOW);
        final Finding finding = ThreadChecks.workerGovernor(new WorkerGovernorFacts(true, Optional.of(status))).orElseThrow();

        assertTrue(finding.what().contains("nothing to choose"), finding.what());
    }
}
