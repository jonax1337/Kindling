package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static net.kindlingmc.doctor.Scenario.assertSeverity;
import static net.kindlingmc.doctor.Scenario.assertSkipped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostChecksTest {

    private static HostLoad load(final long coveredSeconds, final HostCounters growth) {
        return new HostLoad(TimeUnit.SECONDS.toNanos(coveredSeconds), growth);
    }

    private static HostCounters mainThread(final long runMillis, final long waitMillis) {
        return new HostCounters(Optional.of(new SchedTime(runMillis * 1_000_000, waitMillis * 1_000_000)),
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static HostCounters chunkWorkers(final long runMillis, final long waitMillis) {
        return new HostCounters(Optional.empty(), Optional.of(new SchedTime(runMillis * 1_000_000, waitMillis * 1_000_000)),
            Optional.empty(), Optional.empty());
    }

    private static HostCounters cpu(final long total, final long idle, final long steal) {
        return new HostCounters(Optional.empty(), Optional.empty(), Optional.of(new CpuTimes(total, idle, steal)), Optional.empty());
    }

    private static HostCounters throttling(final long periods, final long throttled, final long micros) {
        return new HostCounters(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(new CpuThrottling(periods, throttled, micros)));
    }

    private static HostLoads loads(final HostCounters lastMinute, final HostCounters last15Minutes) {
        return new HostLoads(load(60, lastMinute), load(900, last15Minutes));
    }

    @Test
    void withoutAMonitorNothingIsChecked() {
        assertSkipped(HostChecks.mainThreadWait(HostLoads.NONE));
        assertSkipped(HostChecks.chunkWorkerWait(HostLoads.NONE));
        assertSkipped(HostChecks.hostSteal(HostLoads.NONE));
        assertSkipped(HostChecks.cpuThrottling(HostLoads.NONE));
    }

    @Test
    void theFirstFewSecondsAreTooLittleToSaySomething() {
        final HostLoads fresh = new HostLoads(load(5, mainThread(100, 900)), load(5, mainThread(100, 900)));

        assertSkipped(HostChecks.mainThreadWait(fresh));
    }

    @Test
    void aMainThreadThatRarelyWaitsIsAHintThatContentionIsNotTheCause() {
        final Finding finding = HostChecks.mainThreadWait(loads(mainThread(970, 30), mainThread(950, 50))).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertEquals(Basis.LIVE_READING, finding.basis());
        assertTrue(finding.what().contains("3.0 %"), finding.what());
        assertTrue(finding.what().contains("(last 15 min: 5.0 %)"), finding.what());
        assertTrue(finding.fix().isEmpty());
    }

    @Test
    void aMainThreadThatWaitsMoreThanTenPercentSaysOtherThreadsCompete() {
        final Finding finding = HostChecks.mainThreadWait(loads(mainThread(880, 120), mainThread(950, 50))).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.what().startsWith("Main thread waited for a CPU 12 % of the time it had work to do in the last 60 s"), finding.what());
        assertTrue(finding.what().contains("other threads or processes on this machine compete with it"), finding.what());
        assertTrue(finding.fix().contains("CPUs of its own"), finding.fix());
    }

    @Test
    void exactlyTenPercentIsStillNoWarning() {
        assertSeverity(Severity.INFO, HostChecks.mainThreadWait(loads(mainThread(900, 100), mainThread(900, 100))));
    }

    @Test
    void theFifteenMinuteContextIsLeftOutWhenItCoversNoMoreThanTheMinute() {
        final HostLoads justStarted = new HostLoads(load(30, mainThread(900, 100)), load(30, mainThread(900, 100)));

        final Finding finding = HostChecks.mainThreadWait(justStarted).orElseThrow();

        assertTrue(finding.what().contains("in the last 30 s"), finding.what());
        assertFalse(finding.what().contains("(last"), finding.what());
    }

    @Test
    void chunkWorkersWaitingIsOnlyEverAHint() {
        final Finding finding = HostChecks.chunkWorkerWait(loads(chunkWorkers(100, 900), chunkWorkers(100, 900))).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertTrue(finding.what().contains("90 %"), finding.what());
    }

    @Test
    void stealUpToFivePercentIsAHintWithTheNumber() {
        final Finding finding = HostChecks.hostSteal(loads(cpu(10_000, 5_000, 500), cpu(100_000, 50_000, 1_000))).orElseThrow();

        assertEquals(Severity.INFO, finding.severity());
        assertTrue(finding.what().contains("5.0 %"), finding.what());
    }

    @Test
    void stealAboveFivePercentBlamesTheHost() {
        final Finding finding = HostChecks.hostSteal(loads(cpu(10_000, 5_000, 800), cpu(100_000, 50_000, 1_000))).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.what().contains("8.0 %"), finding.what());
        assertTrue(finding.what().contains("the host's, not the server's"), finding.what());
    }

    @Test
    void aLimitThatThrottlesInAFewPeriodsIsAWarning() {
        final Finding finding = HostChecks.cpuThrottling(loads(throttling(600, 90, 4_500_000), throttling(9000, 100, 6_000_000))).orElseThrow();

        assertEquals(Severity.WARN, finding.severity());
        assertTrue(finding.what().contains("15 %"), finding.what());
        assertTrue(finding.what().contains("4.5 s in total"), finding.what());
        assertTrue(finding.fix().contains("-XX:ActiveProcessorCount"), finding.fix());
    }

    @Test
    void aLimitThatNeverThrottlesIsAHint() {
        assertSeverity(Severity.INFO, HostChecks.cpuThrottling(loads(throttling(600, 0, 0), throttling(9000, 0, 0))));
    }

    @Test
    void aMachineWithoutAContainerLimitHasNoThrottlingCheck() {
        assertSkipped(HostChecks.cpuThrottling(loads(mainThread(900, 100), mainThread(900, 100))));
    }
}
