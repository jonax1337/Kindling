package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Listener;

/**
 * Contraptions that must keep counting the same under load, beside lag machines that make the load. Per plot: the
 * self-running clocks ({@link ClockBoard}), the workshop with its item sorters, hoppers fed by drops, minecarts and
 * brewing stands ({@link Workshop}), a counted TNT chamber, and in the last chunk one of the scenario's lag machines,
 * taking turns from plot to plot; without lag machines that chunk stays empty.
 *
 * <p>Comparing a run with lag machines to one without shows two things: the tick time the machines cost, and whether
 * the counted contraptions still did exactly what they do on an unloaded server. In vanilla nothing a contraption
 * does depends on how long a tick takes, so any counter that differs is behaviour the load changed.
 */
final class LagLayout implements Layout {

    static final String TNT_EXPLOSIONS = "tnt_chamber_explosions";

    private static final int CLOCK_CHUNK = 0;
    private static final int WORKSHOP_CHUNK = 1;
    private static final int TNT_CHUNK = 2;
    private static final int MACHINE_CHUNK = 3;

    private final List<Contraption> contraptions = new ArrayList<>();
    private final List<Clocked> clocks = new ArrayList<>();
    private final PistonTally pistons = new PistonTally();
    private final TntTally tnt = new TntTally();

    LagLayout(List<Plot> plots, List<LagMachine> machines) {
        List<Clocked> chambers = new ArrayList<>();
        List<Clocked> clockedMachines = new ArrayList<>();
        for (int index = 0; index < plots.size(); index++) {
            Plot plot = plots.get(index);
            Workshop workshop = new Workshop(plot.site(WORKSHOP_CHUNK));
            TntChamber chamber = TntChamber.counted(plot.site(TNT_CHUNK));
            tnt.watch(plot.site(TNT_CHUNK).block(0, 0, 0).getLocation());
            contraptions.addAll(List.of(new ClockBoard(plot.site(CLOCK_CHUNK), pistons), workshop, chamber));
            clocks.add(workshop);
            chambers.add(chamber);
            if (!machines.isEmpty()) {
                Contraption machine = machines.get(index % machines.size()).in(plot.site(MACHINE_CHUNK));
                contraptions.add(machine);
                if (machine instanceof Clocked clocked) {
                    clockedMachines.add(clocked);
                }
            }
        }
        // Grouped by kind, as BasesLayout does: taking turns, each kind would step on every other tick only.
        clocks.addAll(chambers);
        clocks.addAll(clockedMachines);
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
        return List.of(pistons, tnt);
    }

    @Override
    public Map<String, Long> totals() {
        Map<String, Long> totals = Layout.super.totals();
        totals.putAll(pistons.totals());
        totals.put(TNT_EXPLOSIONS, tnt.explosions());
        return totals;
    }
}
