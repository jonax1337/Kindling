package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Listener;

/**
 * The survival bases: per plot a storage room, a furnace row, a redstone board and a workshop, optionally under a
 * hopper guard, with the piston extensions counted through their event.
 */
final class BasesLayout implements Layout {

    private static final int STORAGE_CHUNK = 0;
    private static final int FURNACE_CHUNK = 1;
    private static final int BOARD_CHUNK = 2;
    private static final int WORKSHOP_CHUNK = 3;

    private final List<Contraption> contraptions = new ArrayList<>();
    private final List<Clocked> clocks = new ArrayList<>();
    private final PistonCount pistons = new PistonCount();
    private final int hopperGuardSeconds;

    BasesLayout(List<Plot> plots, int hopperGuardSeconds) {
        this.hopperGuardSeconds = hopperGuardSeconds;
        List<Clocked> workshops = new ArrayList<>();
        for (Plot plot : plots) {
            RedstoneBoard board = new RedstoneBoard(plot.site(BOARD_CHUNK));
            Workshop workshop = new Workshop(plot.site(WORKSHOP_CHUNK));
            contraptions.addAll(List.of(new HopperHall(plot.site(STORAGE_CHUNK)),
                    new FurnaceRow(plot.site(FURNACE_CHUNK)), board, workshop));
            clocks.add(board);
            workshops.add(workshop);
        }
        // All boards before all workshops: taking turns, each kind would step on every other tick only.
        clocks.addAll(workshops);
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
        List<Listener> listeners = new ArrayList<>();
        if (hopperGuardSeconds > 0) {
            listeners.add(new HopperGuard(buildTick + hopperGuardSeconds * TICKS_PER_SECOND));
        }
        listeners.add(pistons);
        return listeners;
    }

    @Override
    public Map<String, Long> totals() {
        Map<String, Long> totals = Layout.super.totals();
        totals.put("piston_extensions", pistons.extensions());
        return totals;
    }
}
