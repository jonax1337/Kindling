package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Hoppers whose pushes a plugin rewrites ({@link HopperRewriter}): each holds a stack of cobblestone above a barrel.
 * Whatever the plugin makes of the moved item, the hopper has to lose exactly what the barrel gained.
 */
final class HopperRewrite implements Contraption {

    private static final int COLUMNS = 4;
    private static final int BARREL = 0;
    private static final int HOPPER = 1;
    private static final int STOCK = 20;

    private final ChunkSite site;

    HopperRewrite(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int x = 0; x < COLUMNS; x++) {
            site.place(x, BARREL, 0, Material.BARREL);
            site.place(x, HOPPER, 0, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
            site.stock(x, HOPPER, 0, Material.COBBLESTONE, STOCK);
        }
    }

    List<Block> hoppers() {
        List<Block> hoppers = new ArrayList<>();
        for (int x = 0; x < COLUMNS; x++) {
            hoppers.add(site.block(x, HOPPER, 0));
        }
        return hoppers;
    }

    /** Items that arrived in the barrels, items that left the hoppers, and the difference: items made from nothing. */
    @Override
    public Map<String, Long> totals() {
        long arrived = 0;
        long left = 0;
        for (int x = 0; x < COLUMNS; x++) {
            arrived += site.items(x, BARREL, 0);
            left += STOCK - site.items(x, HOPPER, 0);
        }
        return Map.of("rewrite_arrived", arrived, "rewrite_left", left, "rewrite_duplicated", arrived - left);
    }
}
