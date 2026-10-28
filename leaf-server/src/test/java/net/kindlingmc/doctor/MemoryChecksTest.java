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
        scenario.maxHeapBytes = 15872 * ByteSize.MIB;

        final Finding finding = MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().contains("-Xmx15784M"), finding.fix());
        assertTrue(finding.what().contains("other servers"), finding.what());
    }

    @Test
    void theHeapPlusTheNativeOverheadMustFitTheMemory() {
        scenario.memoryBytes = OptionalLong.of(4 * ByteSize.GIB);
        scenario.maxHeapBytes = 4 * ByteSize.GIB - 600 * ByteSize.MIB;
        assertSeverity(Severity.OK, MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()));

        scenario.maxHeapBytes = 4 * ByteSize.GIB - 599 * ByteSize.MIB;
        assertSeverity(Severity.WARN, MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()));
    }

    @Test
    void aMachineSmallerThanTheNativeOverheadNeedsMoreMemoryNotASmallerHeap() {
        scenario.memoryBytes = OptionalLong.of(512 * ByteSize.MIB);
        scenario.maxHeapBytes = 256 * ByteSize.MIB;

        final Finding finding = MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.fix().startsWith("Give the machine more memory"), finding.fix());
    }

    @Test
    void withoutKnownMemoryTheHeadroomIsNotChecked() {
        scenario.memoryBytes = OptionalLong.empty();

        assertSkipped(MemoryChecks.heapHeadroom(scenario.jvm(), scenario.machine()));
    }

    @Test
    void aHeapBelowOneAndAHalfGibIsTooSmallAndCitesTheMeasuredLiveData() {
        scenario.maxHeapBytes = ByteSize.GIB;

        final Finding finding = MemoryChecks.heapSize(scenario.jvm(), scenario.server()).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertEquals(Basis.KINDLING_MEASUREMENT, finding.basis());
        assertTrue(finding.what().contains("1.2 GB of live data"), finding.what());
        assertTrue(finding.fix().contains("-Xmx2048M"), finding.fix());
    }

    @Test
    void aHeapBetweenOneAndAHalfAndTwoGibIsAHint() {
        scenario.maxHeapBytes = 1792 * ByteSize.MIB;

        assertSeverity(Severity.INFO, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void twoGibAreEnoughForTheDefaultDistances() {
        scenario.maxHeapBytes = 2 * ByteSize.GIB;

        assertSeverity(Severity.OK, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void aFarViewDistanceNeedsMoreThanOneAndAHalfGib() {
        scenario.maxHeapBytes = 1536 * ByteSize.MIB;
        scenario.viewDistance = 16;

        assertSeverity(Severity.WARN, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
    }

    @Test
    void manyPlayerSlotsNeedMoreThanTwoGibForAnUnmeasuredLoadHint() {
        scenario.maxHeapBytes = 2 * ByteSize.GIB;
        scenario.maxPlayers = 100;

        final Finding finding = MemoryChecks.heapSize(scenario.jvm(), scenario.server()).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(Basis.GENERAL_ADVICE, finding.basis());
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
    void anArenaLimitFromGlibcTunablesCountsLikeTheEnvironmentVariable() {
        scenario.mallocArenaMax = LinuxFiles.parseArenaMaxTunable("glibc.malloc.tcache_count=0:glibc.malloc.arena_max=2");

        assertSeverity(Severity.OK, MemoryChecks.mallocArenas(scenario.machine()));
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
    void mallocArenasAreSkippedWithAnAlternativeAllocator() {
        scenario.mallocArenaMax = Optional.empty();
        scenario.preloadedLibraries = Optional.of("/usr/lib/x86_64-linux-gnu/libjemalloc.so.2");

        assertSkipped(MemoryChecks.mallocArenas(scenario.machine()));
    }

    @Test
    void aHeapReportedSlightlyBelowTheWantedSizeIsStillEnough() {
        scenario.maxHeapBytes = 2 * ByteSize.GIB - 100 * ByteSize.MIB;

        assertSeverity(Severity.OK, MemoryChecks.heapSize(scenario.jvm(), scenario.server()));
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
    void aFixedHeapIsAnInfoWhenTheIdleMemoryReturnIsOn() {
        scenario.idleMemory = true;

        final Finding finding = MemoryChecks.fixedHeap(scenario.jvm(), scenario.server()).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertTrue(finding.fix().contains("-Xms512M"));
    }

    @Test
    void aSmallerInitialHeapIsFineWhenTheIdleMemoryReturnIsOn() {
        scenario.idleMemory = true;
        scenario.initialHeapBytes = ByteSize.GIB;

        assertSeverity(Severity.OK, MemoryChecks.fixedHeap(scenario.jvm(), scenario.server()));
    }

    @Test
    void theFixedHeapCheckIsSkippedWithoutTheIdleMemoryReturnOrWithoutTheInitialSize() {
        assertSkipped(MemoryChecks.fixedHeap(scenario.jvm(), scenario.server()));

        scenario.idleMemory = true;
        scenario.initialHeapBytes = 0;
        assertSkipped(MemoryChecks.fixedHeap(scenario.jvm(), scenario.server()));
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
