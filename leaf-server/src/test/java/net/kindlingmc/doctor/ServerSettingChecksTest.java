package net.kindlingmc.doctor;

import java.util.List;
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
        assertTrue(finding.what().contains("92 %") && finding.what().contains("9 %") && finding.what().contains("directional"), finding.what());
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
    void aViewDistanceAboveTenOnEightProcessorsOrFewerRestsOnTheMeasurement() {
        scenario.processors = 8;
        scenario.viewDistance = 12;

        final Finding finding = ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertEquals(Basis.KINDLING_MEASUREMENT, finding.basis());
        assertTrue(finding.what().contains("45 new chunks per second"), finding.what());
        assertTrue(finding.fix().contains("view-distance at 10"), finding.fix());
    }

    @Test
    void aViewDistanceBeyondTheRuleOfThumbOnManyProcessorsIsOnlyGeneralAdvice() {
        scenario.processors = 16;
        scenario.viewDistance = 40;

        final Finding finding = ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertEquals(Basis.GENERAL_ADVICE, finding.basis());
        assertTrue(finding.fix().contains("view-distance at 38"), finding.fix());
    }

    @Test
    void aHighSimulationDistanceOnFewProcessorsIsAGeneralWarning() {
        scenario.processors = 4;
        scenario.simulationDistance = 12;

        final Finding finding = ServerSettingChecks.viewDistance(scenario.machine(), scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertEquals(Basis.GENERAL_ADVICE, finding.basis());
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
    void compressionBehindAProxyOnTheSameHostIsAWarningWithTheSetting() {
        scenario.behindProxy = true;
        scenario.proxyLocation = ProxyLocation.SAME_HOST;

        final Finding finding = ServerSettingChecks.proxyCompression(scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("network-compression-threshold=-1"));
    }

    @Test
    void compressionBehindAProxyAtAnUnknownPlaceIsAWarningToo() {
        scenario.behindProxy = true;
        scenario.proxyLocation = ProxyLocation.UNKNOWN;

        assertSeverity(Severity.WARN, ServerSettingChecks.proxyCompression(scenario.server()));
    }

    @Test
    void compressionBehindAProxyOnAnotherHostIsOnlyAHint() {
        scenario.behindProxy = true;
        scenario.proxyLocation = ProxyLocation.OTHER_HOST;

        assertSeverity(Severity.INFO, ServerSettingChecks.proxyCompression(scenario.server()));
    }

    @Test
    void recommendedOptionsThatAreOnAreFine() {
        assertSeverity(Severity.OK, ServerSettingChecks.recommendedOptions(scenario.server()));
    }

    @Test
    void recommendedOptionsThatAreOffAreAHintNamingThemAndWhyTheyStayOff() {
        scenario.recommendedOptionsOff = List.of("async.async-pathfinding.enabled", "performance.sleeping-block-entity");

        final Finding finding = ServerSettingChecks.recommendedOptions(scenario.server()).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertTrue(finding.what().contains("async.async-pathfinding.enabled, performance.sleeping-block-entity"), finding.what());
        assertTrue(finding.what().contains("fresh file"), finding.what());
        assertTrue(finding.fix().contains("config/leaf-global.yml"), finding.fix());
    }

    @Test
    void noCompressionBehindAProxyIsFine() {
        scenario.behindProxy = true;
        scenario.compressionThreshold = -1;

        assertSeverity(Severity.OK, ServerSettingChecks.proxyCompression(scenario.server()));
    }
}
