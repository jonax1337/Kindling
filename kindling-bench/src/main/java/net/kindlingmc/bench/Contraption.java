package net.kindlingmc.bench;

import java.util.Map;

/**
 * A piece of a survival base, built into one chunk of a plot. Besides load it reports running totals of what it did,
 * so two runs can be compared not only by speed but by whether the server still behaves the same.
 */
interface Contraption {

    void build();

    /** Running totals by name; the run adds them up over all plots and reports how much each changed while measured. */
    Map<String, Long> totals();
}
