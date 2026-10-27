package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * Hoppers under a farm, catching what falls on them: at every step of the clock one item drops onto each hopper,
 * which passes it down into a barrel. Between the items the hoppers have nothing to collect.
 */
final class ItemDrops implements Contraption, Clocked {

    private static final int[] COLUMNS = {0, 3, 6, 9, 12};
    private static final int ROW = 0;
    private static final int BARREL = 0;
    private static final int HOPPER = 1;

    private final ChunkSite site;

    ItemDrops(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int x : COLUMNS) {
            site.place(x, BARREL, ROW, Material.BARREL);
            site.place(x, HOPPER, ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        }
    }

    @Override
    public void clockStep() {
        for (int x : COLUMNS) {
            site.dropOnto(x, HOPPER, ROW, Material.COBBLESTONE);
        }
    }

    @Override
    public Map<String, Long> totals() {
        long caught = 0;
        for (int x : COLUMNS) {
            caught += site.items(x, BARREL, ROW);
        }
        return Map.of("dropped_items_caught", caught);
    }
}
