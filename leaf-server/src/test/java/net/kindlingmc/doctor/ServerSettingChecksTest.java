package net.kindlingmc.doctor;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingChecksTest {

    private final Scenario scenario = new Scenario();

    @Test
    void theMeasuredDefaultFollowsTheFormulaOfThePatch() {
        assertEquals(1, ServerSettingChecks.measuredDefaultWorkerThreads(1));
        assertEquals(2, ServerSettingChecks.measuredDefaultWorkerThreads(4));
        assertEquals(5, ServerSettingChecks.measuredDefaultWorkerThreads(8));
        assertEquals(5, ServerSettingChecks.measuredDefaultWorkerThreads(12));
        assertEquals(6, ServerSettingChecks.measuredDefaultWorkerThreads(24));
    }

    @Test
    void workerThreadsLeftAtTheDefaultAreFine() {
        assertSeverity(Severity.OK, ServerSettingChecks.workerThreads(scenario.machine(), scenario.server()));
    }

    @Test
    void workerThreadsAtOrAboveTheMeasuredDefaultAreFine() {
        scenario.workerThreads = OptionalInt.of(5);
        assertSeverity(Severity.OK, ServerSettingChecks.workerThreads(scenario.machine(), scenario.server()));

        scenario.workerThreads = OptionalInt.of(8);
        assertSeverity(Severity.OK, ServerSettingChecks.workerThreads(scenario.machine(), scenario.server()));
    }

    @Test
    void workerThreadsBelowTheMeasuredDefaultAreAMeasuredWarning() {
        scenario.workerThreads = OptionalInt.of(2);

        final Finding finding = ServerSettingChecks.workerThreads(scenario.machine(), scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertEquals(Basis.KINDLING_MEASUREMENT, finding.basis());
        assertTrue(finding.what().contains("92 %"));
        assertTrue(finding.fix().contains("worker-threads"));
    }

    @Test
    void theMeasuredDefaultDependsOnTheProcessorsOfTheMachine() {
        scenario.processors = 4;
        scenario.workerThreads = OptionalInt.of(2);

        assertSeverity(Severity.OK, ServerSettingChecks.workerThreads(scenario.machine(), scenario.server()));
    }

    @Test
    void distancesThatFitTheProcessorsAreFine() {
        assertSeverity(Severity.OK, ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()));
    }

    @Test
    void theVanillaDefaultsAreFineOnFewProcessors() {
        scenario.processors = 2;
        scenario.viewDistance = 10;
        scenario.simulationDistance = 10;

        assertSeverity(Severity.OK, ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()));
    }

    @Test
    void aHighViewDistanceOnFewProcessorsIsAWarning() {
        scenario.processors = 4;
        scenario.viewDistance = 20;

        final Finding finding = ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("view-distance at 14"), finding.fix());
    }

    @Test
    void aHighSimulationDistanceOnFewProcessorsIsAWarning() {
        scenario.processors = 4;
        scenario.simulationDistance = 12;

        assertSeverity(Severity.WARN, ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()));
    }

    @Test
    void theContainerLimitDecidesHowManyProcessorsTheDistancesAreMeasuredAgainst() {
        scenario.cgroupCpuLimit = OptionalDouble.of(2.0);
        scenario.viewDistance = 16;

        assertSeverity(Severity.WARN, ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()));
    }

    @Test
    void aServerNotBehindAProxyHasNoCompressionCheck() {
        assertSkipped(ServerSettingChecks.proxyCompression(scenario.server()));
    }

    @Test
    void compressionBehindAProxyIsAWarningWithTheSetting() {
        scenario.behindProxy = true;

        final Finding finding = ServerSettingChecks.proxyCompression(scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("network-compression-threshold=-1"));
    }

    @Test
    void noCompressionBehindAProxyIsFine() {
        scenario.behindProxy = true;
        scenario.compressionThreshold = -1;

        assertSeverity(Severity.OK, ServerSettingChecks.proxyCompression(scenario.server()));
    }
}
