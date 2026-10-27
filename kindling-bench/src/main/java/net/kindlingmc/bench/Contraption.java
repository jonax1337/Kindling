package net.kindlingmc.bench;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * A piece of a survival base, built into one chunk of a plot. Besides load it reports running totals of what it did,
 * so two runs can be compared not only by speed but by whether the server still behaves the same.
 */
interface Contraption {

    void build();

    /** Running totals by name; the run adds them up over all plots and reports how much each changed while measured. */
    Map<String, Long> totals();

    /** The totals of all {@code contraptions}, summed by name. */
    static Map<String, Long> totalsOf(Collection<? extends Contraption> contraptions) {
        Map<String, Long> totals = new TreeMap<>();
        for (Contraption contraption : contraptions) {
            contraption.totals().forEach((name, value) -> totals.merge(name, value, Long::sum));
        }
        return totals;
    }
}
