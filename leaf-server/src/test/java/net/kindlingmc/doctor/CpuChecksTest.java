package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuChecksTest {

    private final Scenario scenario = new Scenario();

    @Test
    void enoughProcessorsAreFine() {
        assertSeverity(Severity.OK, CpuChecks.processorCount(scenario.machine()));
    }

    @Test
    void twoProcessorsAreVeryFewButOnlyAHint() {
        scenario.processors = 2;

        assertSeverity(Severity.INFO, CpuChecks.processorCount(scenario.machine()));
    }

    @Test
    void aContainerLimitBelowTheJvmCountCountsInsteadOfIt() {
        scenario.cgroupCpuLimit = OptionalDouble.of(1.5);

        assertSeverity(Severity.INFO, CpuChecks.processorCount(scenario.machine()));
    }

    @Test
    void withoutAContainerLimitThereIsNothingToCompare() {
        assertSkipped(CpuChecks.containerLimit(scenario.machine()));
    }

    @Test
    void aJvmCountAboveTheContainerLimitIsAWarningWithTheFlagToSet() {
        scenario.cgroupCpuLimit = OptionalDouble.of(4.0);

        final Finding finding = CpuChecks.containerLimit(scenario.machine()).orElseThrow();

        assertSeverity(Severity.WARN, Optional.of(finding));
        assertTrue(finding.fix().contains("-XX:ActiveProcessorCount=4"));
    }

    @Test
    void aJvmCountWithinTheContainerLimitIsFine() {
        scenario.cgroupCpuLimit = OptionalDouble.of(8.0);

        assertSeverity(Severity.OK, CpuChecks.containerLimit(scenario.machine()));
    }

    @Test
    void aFractionalLimitIsRoundedUpToWholeProcessors() {
        scenario.cgroupCpuLimit = OptionalDouble.of(2.5);

        assertTrue(CpuChecks.containerLimit(scenario.machine()).orElseThrow().fix().contains("ActiveProcessorCount=3"));
    }
}
