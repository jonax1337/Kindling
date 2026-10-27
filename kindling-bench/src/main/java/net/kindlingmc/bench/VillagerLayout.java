package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Listener;

/**
 * A sealed {@link VillagerHall} in every chunk of every plot. Nothing here is driven by a clock or a plugin: the
 * villagers alone claim the beds and job sites, so the run shows what their pathfinding does with the competition. As a
 * performance load, give it a warm-up and read the tick times; the counters then only mean something for a run without one.
 *
 * <p>The counters are absolute: a claim is a state the villagers are in at the end, not something that accumulates.
 */
final class VillagerLayout implements Layout {

    private final List<Contraption> halls = new ArrayList<>();

    VillagerLayout(List<Plot> plots) {
        for (Plot plot : plots) {
            for (int chunk = 0; chunk < Plot.CHUNKS; chunk++) {
                halls.add(new VillagerHall(plot.site(chunk)));
            }
        }
    }

    @Override
    public List<Contraption> contraptions() {
        return halls;
    }

    @Override
    public List<Clocked> clocks() {
        return List.of();
    }

    @Override
    public List<Listener> listeners(int buildTick) {
        return List.of();
    }

    @Override
    public Map<String, Long> counters(Map<String, Long> before, Map<String, Long> after) {
        return after;
    }
}
