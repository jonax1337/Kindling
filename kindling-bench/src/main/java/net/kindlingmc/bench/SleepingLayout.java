package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;

/**
 * The cases where a server that lets idle block entities sleep (Leaf's {@code performance.sleeping-block-entity}) can
 * go wrong although every contraption is deterministic: hoppers with and without non-default {@code spigot.yml} rates,
 * a plugin rewriting the item a hopper moves, blocks placed without physics next to sleeping hoppers, and plugins that
 * hold back the fuel of brewing stands and furnaces for a while. Run once with the option on and once with it off, the
 * counters must be identical.
 *
 * <p>The counters are absolute, not the growth over a warm-up: each module counts from what it was built with, and the
 * run is meant to start measuring when it builds ({@code warmup-seconds=0}), since the plugin stand-ins act at fixed
 * ticks after the build.
 */
final class SleepingLayout implements Layout {

    /** How long the plugin stand-ins hold things back, and when the physics-free blocks are placed. */
    static final int HOLD_TICKS = 10 * TICKS_PER_SECOND;

    private static final int HOPPER_HALL_CHUNK = 0;
    private static final int HOPPER_TRICKS_CHUNK = 1;
    private static final int FUEL_CHUNK = 2;
    private static final int PLACEMENT_Z = 8;

    private final List<Contraption> contraptions = new ArrayList<>();
    private final List<Clocked> clocks = new ArrayList<>();
    private final Set<Block> rewrittenHoppers = new HashSet<>();

    SleepingLayout(List<Plot> plots) {
        for (Plot plot : plots) {
            ChunkSite tricks = plot.site(HOPPER_TRICKS_CHUNK);
            HopperRewrite rewrite = new HopperRewrite(tricks);
            PhysicsFreePlacement placement =
                    new PhysicsFreePlacement(tricks.corner(0, PLACEMENT_Z), HOLD_TICKS / Clocked.STEP_TICKS);
            contraptions.addAll(List.of(new HopperHall(plot.site(HOPPER_HALL_CHUNK)), rewrite, placement,
                    new GatedFuelRow(plot.site(FUEL_CHUNK))));
            clocks.add(placement);
            rewrittenHoppers.addAll(rewrite.hoppers());
        }
    }

    @Override
    public List<Contraption> contraptions() {
        return contraptions;
    }

    @Override
    public List<Clocked> clocks() {
        return clocks;
    }

    @Override
    public List<Listener> listeners(int buildTick) {
        return List.of(new HopperRewriter(rewrittenHoppers), new FuelGate(buildTick + HOLD_TICKS));
    }

    @Override
    public Map<String, Long> counters(Map<String, Long> before, Map<String, Long> after) {
        return after;
    }
}
