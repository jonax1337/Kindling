package net.kindlingmc.bench;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;

/**
 * Stands in for a plugin that denies fuel to brewing stands and furnaces for a while, as region and claim plugins do:
 * until the release tick it cancels the fuel of every brewing stand and keeps every furnace from burning, then it lets
 * everything through. Afterwards the stands and furnaces have to work as if nothing had happened.
 */
final class FuelGate implements Listener {

    private final int releaseTick;

    FuelGate(int releaseTick) {
        this.releaseTick = releaseTick;
    }

    @EventHandler
    public void holdBrewingFuel(BrewingStandFuelEvent event) {
        if (holding()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void holdFurnaceFuel(FurnaceBurnEvent event) {
        if (holding()) {
            event.setBurning(false);
        }
    }

    private boolean holding() {
        return Bukkit.getCurrentTick() < releaseTick;
    }
}
