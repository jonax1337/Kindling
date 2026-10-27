package net.kindlingmc.bench;

import java.util.List;
import java.util.Map;

/**
 * The parts of a base where items take other ways than from hopper to hopper: hoppers catching dropped items, item
 * sorters run by a comparator and a locked hopper, hoppers loading and unloading minecarts, a composter, a double
 * chest, brewing stands and a crafter. These are where a server that lets idle block entities sleep has to wake them
 * exactly when vanilla would act, so besides adding load their counters check just that.
 */
final class Workshop implements Contraption, Clocked {

    private final List<Contraption> parts;
    private final List<Clocked> clockedParts;

    Workshop(ChunkSite site) {
        ItemDrops drops = new ItemDrops(site.corner(1, 1));
        ItemSorter westSorter = new ItemSorter(site.corner(1, 4));
        ItemSorter eastSorter = new ItemSorter(site.corner(8, 4));
        CrafterLine crafter = new CrafterLine(site.corner(8, 12));
        parts = List.of(drops, westSorter, eastSorter, new TransferColumns(site.corner(1, 9)),
                new BrewingRow(site.corner(1, 12)), crafter);
        clockedParts = List.of(drops, westSorter, eastSorter, crafter);
    }

    @Override
    public void build() {
        parts.forEach(Contraption::build);
    }

    @Override
    public void clockStep() {
        clockedParts.forEach(Clocked::clockStep);
    }

    @Override
    public Map<String, Long> totals() {
        return Contraption.totalsOf(parts);
    }
}
