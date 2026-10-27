package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The formats of the kernel files the host monitor reads, with samples as the kernel writes them. */
class ProcFormatsTest {

    private static final String PROC_STAT = """
        cpu  4705 150 1120 16250 520 30 45 12 0 0
        cpu0 1200 40 300 4000 100 10 20 3 0 0
        intr 12345 0 0
        """;

    @Test
    void theFirstLineOfProcStatGivesTotalIdleAndStealTicks() {
        assertEquals(Optional.of(new CpuTimes(22832, 16770, 12)), LinuxFiles.parseCpuTimes(PROC_STAT));
    }

    @Test
    void anOldKernelWithoutIowaitAndStealCountsThemAsZero() {
        assertEquals(Optional.of(new CpuTimes(100 + 5 + 20 + 400, 400, 0)), LinuxFiles.parseCpuTimes("cpu  100 5 20 400\n"));
    }

    @Test
    void procStatWithoutACpuLineOrWithGarbageIsEmpty() {
        assertEquals(Optional.empty(), LinuxFiles.parseCpuTimes(""));
        assertEquals(Optional.empty(), LinuxFiles.parseCpuTimes("intr 1 2 3"));
        assertEquals(Optional.empty(), LinuxFiles.parseCpuTimes("cpu  1 2 x 4 5"));
        assertEquals(Optional.empty(), LinuxFiles.parseCpuTimes("cpu  1 2 3"));
    }

    @Test
    void schedstatGivesRunTimeAndRunQueueWait() {
        assertEquals(Optional.of(new SchedTime(1_500_000_000L, 250_000_000L)), LinuxFiles.parseSchedstat("1500000000 250000000 42\n"));
    }

    @Test
    void schedstatThatIsMissingOrBrokenIsEmpty() {
        assertEquals(Optional.empty(), LinuxFiles.parseSchedstat(""));
        assertEquals(Optional.empty(), LinuxFiles.parseSchedstat("12"));
        assertEquals(Optional.empty(), LinuxFiles.parseSchedstat("a b c"));
    }

    @Test
    void theThreadNameInStatMayContainSpaces() {
        assertEquals(Optional.of("Leaf Common Wor"), LinuxFiles.parseThreadName("5678 (Leaf Common Wor) S 1 5678 5678 0 -1 4194560 100 0"));
    }

    @Test
    void theThreadNameInStatMayContainBrackets() {
        assertEquals(Optional.of("a) (b c"), LinuxFiles.parseThreadName("9 (a) (b c) R 1 9 9 0"));
    }

    @Test
    void aStatLineWithoutBracketsHasNoThreadName() {
        assertEquals(Optional.empty(), LinuxFiles.parseThreadName("9 name S 1"));
        assertEquals(Optional.empty(), LinuxFiles.parseThreadName(")("));
    }

    @Test
    void theThreadIdIsTheLastPartOfTheThreadSelfLink() {
        assertEquals(OptionalLong.of(5678), LinuxFiles.parseThreadId("1234/task/5678"));
        assertEquals(OptionalLong.empty(), LinuxFiles.parseThreadId("1234/task/"));
        assertEquals(OptionalLong.empty(), LinuxFiles.parseThreadId("nonsense"));
    }

    @Test
    void cgroupVersion2CountsThrottlingInMicroseconds() {
        final String cpuStat = """
            usage_usec 1000
            user_usec 600
            system_usec 400
            nr_periods 100
            nr_throttled 7
            throttled_usec 123456
            nr_bursts 0
            burst_usec 0
            """;

        assertEquals(Optional.of(new CpuThrottling(100, 7, 123456)), LinuxFiles.parseCpuStat(cpuStat));
    }

    @Test
    void cgroupVersion1CountsThrottlingInNanoseconds() {
        assertEquals(Optional.of(new CpuThrottling(100, 7, 123456)),
            LinuxFiles.parseCpuStat("nr_periods 100\nnr_throttled 7\nthrottled_time 123456000\n"));
    }

    @Test
    void aCpuStatWithoutThrottlingCountersIsEmpty() {
        assertEquals(Optional.empty(), LinuxFiles.parseCpuStat("usage_usec 1000\nuser_usec 600\nsystem_usec 400\n"));
        assertEquals(Optional.empty(), LinuxFiles.parseCpuStat(""));
    }

    @Test
    void theOwnCgroupIsTheLineOfVersion2() {
        assertEquals(Optional.of("/system.slice/kindling.service"),
            LinuxFiles.parseCgroupV2Path("12:cpu,cpuacct:/old\n0::/system.slice/kindling.service\n"));
        assertEquals(Optional.empty(), LinuxFiles.parseCgroupV2Path("12:cpu,cpuacct:/old\n"));
    }

    @Test
    void theArenaLimitIsOneOfTheGlibcTunables() {
        assertEquals(Optional.of("2"), LinuxFiles.parseArenaMaxTunable("glibc.malloc.arena_max=2"));
        assertEquals(Optional.of("4"), LinuxFiles.parseArenaMaxTunable("glibc.malloc.tcache_count=0:glibc.malloc.arena_max=4:glibc.malloc.trim_threshold=1"));
        assertEquals(Optional.empty(), LinuxFiles.parseArenaMaxTunable("glibc.malloc.tcache_count=0"));
        assertEquals(Optional.empty(), LinuxFiles.parseArenaMaxTunable(""));
    }
}
