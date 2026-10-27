package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryChecksTest {

    private final Scenario scenario = new Scenario();

    @Test
    void aHeapWellBelowTheMemoryLeavesRoomForNativeMemory() {
        assertSeverity(Severity.OK, MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()));
    }

    @Test
    void aHeapAlmostAsBigAsTheMemoryIsAWarningWithASmallerHeap() {
        scenario.maxHeapBytes = 15 * ByteSize.GIB;

        final Finding finding = MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("-Xmx12288M"), finding.fix());
    }

    @Test
    void withoutKnownMemoryTheHeadroomIsNotChecked() {
        scenario.memoryBytes = OptionalLong.empty();

        assertSkipped(MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()));
    }

    @Test
    void aHeapBelowOneGibIsTooSmall() {
        scenario.maxHeapBytes = 512 * ByteSize.MIB;

        assertSeverity(Severity.WARN, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void twoGibAreEnoughForTheDefaultDistances() {
        scenario.maxHeapBytes = 2 * ByteSize.GIB;

        assertSeverity(Severity.OK, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void aFarViewDistanceNeedsMoreThanOneGib() {
        scenario.maxHeapBytes = 1536 * ByteSize.MIB;
        scenario.viewDistance = 16;

        assertSeverity(Severity.WARN, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void manyPlayerSlotsNeedMoreThanOneGib() {
        scenario.maxHeapBytes = 1536 * ByteSize.MIB;
        scenario.maxPlayers = 100;

        assertSeverity(Severity.WARN, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void unsetMallocArenasOnGlibcAreAWarningWithTheVariableToSet() {
        scenario.mallocArenaMax = Optional.empty();

        final Finding finding = MemoryChecks.mallocArenas(scenario.machine()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("MALLOC_ARENA_MAX=2"));
        assertTrue(finding.what().contains("64 malloc arenas"), finding.what());
    }

    @Test
    void setMallocArenasAreFine() {
        assertSeverity(Severity.OK, MemoryChecks.mallocArenas(scenario.machine()));
    }

    @Test
    void mallocArenasAreSkippedOnMuslAndOnOtherSystems() {
        scenario.mallocArenaMax = Optional.empty();
        scenario.glibc = Optional.of(false);
        assertSkipped(MemoryChecks.mallocArenas(scenario.machine()));

        scenario.glibc = Optional.empty();
        assertSkipped(MemoryChecks.mallocArenas(scenario.machine()));
    }

    @Test
    void aDisabledNativeHeapTrimIsAMeasuredHint() {
        scenario.trimNativeHeapIntervalMillis = OptionalLong.of(0);

        final Finding finding = MemoryChecks.trimNativeHeap(scenario.jvm(), scenario.machine()).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(Basis.KINDLING_MEASUREMENT, finding.basis());
        assertTrue(finding.fix().contains("-XX:TrimNativeHeapInterval="));
    }

    @Test
    void anEnabledNativeHeapTrimIsFine() {
        assertSeverity(Severity.OK, MemoryChecks.trimNativeHeap(scenario.jvm(), scenario.machine()));
    }

    @Test
    void theNativeHeapTrimIsSkippedWithoutTheFlagOrBeforeJava25OrWithoutGlibc() {
        scenario.trimNativeHeapIntervalMillis = OptionalLong.empty();
        assertSkipped(MemoryChecks.trimNativeHeap(scenario.jvm(), scenario.machine()));

        scenario.trimNativeHeapIntervalMillis = OptionalLong.of(0);
        scenario.javaFeatureVersion = 21;
        assertSkipped(MemoryChecks.trimNativeHeap(scenario.jvm(), scenario.machine()));

        scenario.javaFeatureVersion = 25;
        scenario.glibc = Optional.empty();
        assertSkipped(MemoryChecks.trimNativeHeap(scenario.jvm(), scenario.machine()));
    }

    @Test
    void alwaysOnTransparentHugePagesAreAWarningWithMadvise() {
        scenario.transparentHugePages = Optional.of("always");

        final Finding finding = MemoryChecks.transparentHugePages(scenario.machine()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("madvise"));
    }

    @Test
    void madviseTransparentHugePagesAreFine() {
        assertSeverity(Severity.OK, MemoryChecks.transparentHugePages(scenario.machine()));
    }

    @Test
    void transparentHugePagesAreSkippedWhenTheKernelDoesNotSayAnything() {
        scenario.transparentHugePages = Optional.empty();

        assertSkipped(MemoryChecks.transparentHugePages(scenario.machine()));
    }
}
