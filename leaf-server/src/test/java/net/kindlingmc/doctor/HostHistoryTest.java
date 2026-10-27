package net.kindlingmc.doctor;

import java.util.OptionalDouble;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostHistoryTest {

    private static final long INTERVAL = TimeUnit.SECONDS.toNanos(5);

    private final HostHistory history = new HostHistory(INTERVAL);

    /** A sample in which the main thread ran for the given share of the interval and waited for the rest of its wish. */
    private static HostSample sampleWithMainThreadWaiting(final long runNanos, final long waitNanos) {
        return new HostSample(INTERVAL, new HostCounters(
            Optional.of(new SchedTime(runNanos, waitNanos)), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void counterGrowthIsTheDifferenceOfTwoReadings() {
        final HostCounters before = new HostCounters(Optional.of(new SchedTime(100, 10)), Optional.empty(),
            Optional.of(new CpuTimes(1000, 400, 5)), Optional.of(new CpuThrottling(10, 1, 500)));
        final HostCounters after = new HostCounters(Optional.of(new SchedTime(160, 40)), Optional.empty(),
            Optional.of(new CpuTimes(1300, 500, 8)), Optional.of(new CpuThrottling(16, 3, 900)));

        final HostCounters growth = after.since(before);

        assertEquals(Optional.of(new SchedTime(60, 30)), growth.mainThread());
        assertEquals(Optional.empty(), growth.chunkWorkers());
        assertEquals(Optional.of(new CpuTimes(300, 100, 3)), growth.cpu());
        assertEquals(Optional.of(new CpuThrottling(6, 2, 400)), growth.throttling());
    }

    @Test
    void aCounterThatWentBackwardsHasNoGrowth() {
        final HostCounters before = new HostCounters(Optional.empty(), Optional.of(new SchedTime(500, 50)), Optional.empty(), Optional.empty());
        final HostCounters after = new HostCounters(Optional.empty(), Optional.of(new SchedTime(300, 70)), Optional.empty(), Optional.empty());

        assertEquals(Optional.empty(), after.since(before).chunkWorkers());
    }

    @Test
    void aSourceThatIsMissingInEitherReadingHasNoGrowth() {
        final HostCounters withMain = new HostCounters(Optional.of(new SchedTime(1, 1)), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals(HostCounters.NONE, withMain.since(HostCounters.NONE));
        assertEquals(HostCounters.NONE, HostCounters.NONE.since(withMain));
    }

    @Test
    void addingGrowthKeepsASourceThatOnlyOneSideHas() {
        final HostCounters main = new HostCounters(Optional.of(new SchedTime(1, 2)), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals(main, HostCounters.NONE.plus(main));
        assertEquals(Optional.of(new SchedTime(2, 4)), main.plus(main).mainThread());
    }

    @Test
    void theWaitShareIsTheWaitedPartOfTheWantedTime() {
        assertEquals(OptionalDouble.of(0.25), new SchedTime(750, 250).waitShare());
        assertEquals(OptionalDouble.empty(), new SchedTime(0, 0).waitShare());
    }

    @Test
    void withoutSamplesThereIsNothingToReport() {
        final HostLoads loads = this.history.loads();

        assertEquals(0, loads.lastMinute().coveredNanos());
        assertEquals(OptionalDouble.empty(), loads.lastMinute().mainThreadWaitShare());
    }

    @Test
    void theLastMinuteOnlyCountsTheNewestSamples() {
        for (int i = 0; i < 12; i++) {
            this.history.add(sampleWithMainThreadWaiting(INTERVAL, 0));
        }
        for (int i = 0; i < 12; i++) {
            this.history.add(sampleWithMainThreadWaiting(INTERVAL / 2, INTERVAL / 2));
        }

        final HostLoads loads = this.history.loads();

        assertEquals(60, loads.lastMinute().coveredSeconds());
        assertEquals(OptionalDouble.of(0.5), loads.lastMinute().mainThreadWaitShare());
        assertEquals(120, loads.last15Minutes().coveredSeconds());
        assertEquals(OptionalDouble.of(0.25), loads.last15Minutes().mainThreadWaitShare());
    }

    @Test
    void theWindowsAreCoveredOnlyAsFarAsTheMonitorHasRun() {
        this.history.add(sampleWithMainThreadWaiting(INTERVAL, 0));
        this.history.add(sampleWithMainThreadWaiting(INTERVAL, 0));

        final HostLoads loads = this.history.loads();

        assertEquals(10, loads.lastMinute().coveredSeconds());
        assertEquals(10, loads.last15Minutes().coveredSeconds());
    }

    @Test
    void theHistoryForgetsSamplesOlderThanTheLongestWindow() {
        for (int i = 0; i < 400; i++) {
            this.history.add(sampleWithMainThreadWaiting(INTERVAL, 0));
        }

        final long coveredSeconds = this.history.loads().last15Minutes().coveredSeconds();

        assertTrue(coveredSeconds >= 900 && coveredSeconds <= 905, "covered " + coveredSeconds);
    }

    @Test
    void theLoadsAreReadableThroughTheWindowEnum() {
        this.history.add(sampleWithMainThreadWaiting(INTERVAL, 0));
        final HostLoads loads = this.history.loads();

        assertEquals(loads.lastMinute(), loads.of(HostWindow.LAST_MINUTE));
        assertEquals(loads.last15Minutes(), loads.of(HostWindow.LAST_15_MINUTES));
    }
}
