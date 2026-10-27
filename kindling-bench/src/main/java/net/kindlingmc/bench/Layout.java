package net.kindlingmc.bench;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.event.Listener;

/**
 * What a {@link BaseRun} builds into its plots and what runs beside it: the contraptions, the clocks that drive them and
 * the plugin stand-ins and counting listeners. The scenario type picks the layout.
 */
interface Layout {

    int TICKS_PER_SECOND = 20;

    /** Everything built into the plots, in the order it is built. */
    List<Contraption> contraptions();

    /** The clocks that drive the contraptions, in the order they step within a tick. */
    List<Clocked> clocks();

    /** The stand-in plugins and counting listeners, registered once the contraptions are built at {@code buildTick}. */
    List<Listener> listeners(int buildTick);

    /** The running totals of everything the layout built. */
    default Map<String, Long> totals() {
        return Contraption.totalsOf(contraptions());
    }

    /**
     * What the run reports as its counters, from the totals when the measurement began and when it ended. By default
     * how much each total grew in between.
     */
    default Map<String, Long> counters(Map<String, Long> before, Map<String, Long> after) {
        Map<String, Long> growth = new TreeMap<>();
        after.forEach((name, value) -> growth.put(name, value - before.getOrDefault(name, 0L)));
        return growth;
    }

    static Layout of(Scenario scenario, List<Plot> plots) {
        return switch (scenario.type()) {
            case BASES -> new BasesLayout(plots, scenario.hopperGuardSeconds());
            case SLEEPING -> new SleepingLayout(plots);
            case VILLAGERS -> new VillagerLayout(plots);
            case MIXED -> throw new IllegalArgumentException("a mixed run builds no plots");
        };
    }
}
