package net.kindlingmc.bench;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Collections and time of the garbage collectors, counting stop-the-world pauses only. ZGC and Shenandoah report their
 * mostly concurrent cycles as collectors of their own ("... Cycles") next to their pauses ("... Pauses"); G1 reports
 * its young and full collections and, as "G1 Concurrent GC", the remark and cleanup pauses of a concurrent cycle. So
 * every collector counts except the ones named "... Cycles".
 *
 * @param collections collections or pauses, as the collectors count them
 * @param millis the time they took, in milliseconds
 */
record GcTotals(long collections, long millis) {

    private static final String CONCURRENT_CYCLES_SUFFIX = "Cycles";

    static GcTotals ofPauses() {
        long collections = 0;
        long millis = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!collector.getName().endsWith(CONCURRENT_CYCLES_SUFFIX)) {
                collections += Math.max(0, collector.getCollectionCount());
                millis += Math.max(0, collector.getCollectionTime());
            }
        }
        return new GcTotals(collections, millis);
    }

    GcTotals since(GcTotals earlier) {
        return new GcTotals(collections - earlier.collections, millis - earlier.millis);
    }
}
