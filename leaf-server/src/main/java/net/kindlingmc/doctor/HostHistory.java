package net.kindlingmc.doctor;

import java.util.ArrayDeque;
import java.util.Iterator;

/** The latest samples of the host monitor, as many as the longest window needs. Used by the sampling thread only. */
final class HostHistory {

    private final ArrayDeque<HostSample> samples = new ArrayDeque<>();
    private final int capacity;

    HostHistory(final long sampleIntervalNanos) {
        this.capacity = (int) (HostWindow.LAST_15_MINUTES.nanos() / sampleIntervalNanos) + 1;
    }

    void add(final HostSample sample) {
        if (this.samples.size() == this.capacity) {
            this.samples.removeFirst();
        }
        this.samples.addLast(sample);
    }

    HostLoads loads() {
        return new HostLoads(load(HostWindow.LAST_MINUTE), load(HostWindow.LAST_15_MINUTES));
    }

    /** Adds up the newest samples until the window is covered; the sample that crosses its start counts whole. */
    private HostLoad load(final HostWindow window) {
        long coveredNanos = 0;
        HostCounters growth = HostCounters.NONE;
        final Iterator<HostSample> newestFirst = this.samples.descendingIterator();
        while (newestFirst.hasNext() && coveredNanos < window.nanos()) {
            final HostSample sample = newestFirst.next();
            coveredNanos += sample.intervalNanos();
            growth = growth.plus(sample.growth());
        }
        return new HostLoad(coveredNanos, growth);
    }
}
