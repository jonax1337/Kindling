package net.kindlingmc.doctor;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmChecksTest {

    private final Scenario scenario = new Scenario();

    @Test
    void g1IsFine() {
        assertSeverity(Severity.OK, JvmChecks.garbageCollector(scenario.jvm()));
    }

    @Test
    void stopTheWorldCollectorsAreWarnings() {
        scenario.garbageCollector = Optional.of(GarbageCollector.PARALLEL);
        assertSeverity(Severity.WARN, JvmChecks.garbageCollector(scenario.jvm()));

        scenario.garbageCollector = Optional.of(GarbageCollector.SERIAL);
        assertSeverity(Severity.WARN, JvmChecks.garbageCollector(scenario.jvm()));
    }

    @Test
    void concurrentCollectorsAreOnlyAHint() {
        scenario.garbageCollector = Optional.of(GarbageCollector.ZGC);
        assertSeverity(Severity.INFO, JvmChecks.garbageCollector(scenario.jvm()));

        scenario.garbageCollector = Optional.of(GarbageCollector.SHENANDOAH);
        assertSeverity(Severity.INFO, JvmChecks.garbageCollector(scenario.jvm()));
    }

    @Test
    void anUnknownCollectorIsNotChecked() {
        scenario.garbageCollector = Optional.empty();

        assertSkipped(JvmChecks.garbageCollector(scenario.jvm()));
    }

    @Test
    void compactObjectHeadersThatAreOnAreFine() {
        assertSeverity(Severity.OK, JvmChecks.compactObjectHeaders(scenario.jvm()));
    }

    @Test
    void compactObjectHeadersThatAreOffAreAMeasuredHintWithTheFlag() {
        scenario.compactObjectHeaders = Optional.of(false);

        final Finding finding = JvmChecks.compactObjectHeaders(scenario.jvm()).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(Basis.KINDLING_MEASUREMENT, finding.basis());
        assertTrue(finding.what().contains("-5 %"));
        assertTrue(finding.what().contains("-17 %"));
        assertEquals("Add -XX:+UseCompactObjectHeaders", finding.fix());
    }

    @Test
    void compactObjectHeadersAreSkippedBeforeJava25AndWithoutTheFlag() {
        scenario.compactObjectHeaders = Optional.of(false);
        scenario.javaFeatureVersion = 21;
        assertSkipped(JvmChecks.compactObjectHeaders(scenario.jvm()));

        scenario.javaFeatureVersion = 25;
        scenario.compactObjectHeaders = Optional.empty();
        assertSkipped(JvmChecks.compactObjectHeaders(scenario.jvm()));
    }

    @Test
    void aConfiguredAotCacheIsFine() {
        assertSeverity(Severity.OK, JvmChecks.aotCache(scenario.jvm()));
    }

    @Test
    void anAotCacheBeingCreatedCountsAsConfigured() {
        scenario.jvmArguments = List.of("-Xmx2G", "-XX:AOTCacheOutput=kindling.aot");

        assertSeverity(Severity.OK, JvmChecks.aotCache(scenario.jvm()));
    }

    @Test
    void withoutAnAotCacheOnJava25TheHintNamesTheMeasuredStartTimes() {
        scenario.jvmArguments = List.of("-Xmx2G");

        final Finding finding = JvmChecks.aotCache(scenario.jvm()).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(Basis.KINDLING_MEASUREMENT, finding.basis());
        assertTrue(finding.what().contains("7.5 s to 2.7 s"));
        assertTrue(finding.fix().contains("-XX:AOTCacheOutput="));
    }

    @Test
    void theAotCacheIsSkippedBeforeJava25() {
        scenario.jvmArguments = List.of();
        scenario.javaFeatureVersion = 21;

        assertSkipped(JvmChecks.aotCache(scenario.jvm()));
    }
}
