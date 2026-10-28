package net.kindlingmc.doctor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.kindlingmc.chunk.HostPressure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How one 5-second host sample reads for the worker governor. */
class HostPressureTest {

    private static final long SAMPLE_SECONDS = 5;
    private static final Optional<CpuThrottling> NO_LIMIT = Optional.empty();
    private static final Optional<CpuTimes> NO_STEAL = Optional.empty();

    private static HostPressure pressure(final Optional<SchedTime> mainThread, final Optional<CpuThrottling> throttling,
                                         final Optional<CpuTimes> cpu) {
        final HostCounters growth = new HostCounters(mainThread, Optional.empty(), cpu, throttling);
        return HostChecks.pressureOf(new HostSample(TimeUnit.SECONDS.toNanos(SAMPLE_SECONDS), growth).load());
    }

    private static HostPressure mainThreadOnly(final long runMillis, final long waitMillis) {
        return pressure(mainThread(runMillis, waitMillis), NO_LIMIT, NO_STEAL);
    }

    private static Optional<SchedTime> mainThread(final long runMillis, final long waitMillis) {
        return Optional.of(new SchedTime(TimeUnit.MILLISECONDS.toNanos(runMillis), TimeUnit.MILLISECONDS.toNanos(waitMillis)));
    }

    private static Optional<CpuThrottling> throttled(final long periods, final long throttledPeriods) {
        return Optional.of(new CpuThrottling(periods, throttledPeriods, throttledPeriods * 10_000));
    }

    private static Optional<CpuTimes> steal(final long totalTicks, final long stealTicks) {
        return Optional.of(new CpuTimes(totalTicks, totalTicks / 2, stealTicks));
    }

    @Test
    void nothingMeasuredTellsNothing() {
        assertEquals(HostPressure.UNKNOWN, pressure(Optional.empty(), NO_LIMIT, steal(1000, 0)));
    }

    @Test
    void aMainThreadWaitingLikeTheDoctorWarnsIsHighPressure() {
        assertEquals(HostPressure.HIGH, mainThreadOnly(4000, 600));
    }

    @Test
    void aHighShareOfShortWaitsIsWakeUpLatencyAndQuiet() {
        assertEquals(HostPressure.LOW, mainThreadOnly(220, 30));
    }

    @Test
    void waitsBelowHalfTheWarningShareAreQuiet() {
        assertEquals(HostPressure.LOW, mainThreadOnly(4750, 200));
    }

    @Test
    void waitsBetweenQuietAndTheWarningHoldTheCount() {
        assertEquals(HostPressure.MODERATE, mainThreadOnly(4600, 400));
    }

    @Test
    void aThrottlingContainerLimitIsHighPressureEvenForAQuietMainThread() {
        assertEquals(HostPressure.HIGH, pressure(mainThread(4750, 200), throttled(50, 1), NO_STEAL));
    }

    @Test
    void aContainerLimitThatNeverThrottlesIsQuietWithoutMainThreadReadings() {
        assertEquals(HostPressure.LOW, pressure(Optional.empty(), throttled(50, 0), NO_STEAL));
    }

    @Test
    void stealAboveTheDoctorsThresholdIsNeverQuiet() {
        assertEquals(HostPressure.MODERATE, pressure(mainThread(4750, 200), NO_LIMIT, steal(1000, 80)));
        assertEquals(HostPressure.LOW, pressure(mainThread(4750, 200), NO_LIMIT, steal(1000, 50)));
    }
}
