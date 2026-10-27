package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * Hoppers as they stand in a storage room: chains that carry items from one barrel into another, idle hoppers between
 * empty barrels, and hoppers stuck in front of a full barrel. Vanilla asks the idle and the stuck ones every tick
 * although they can do nothing, which is what most hoppers on a real server do most of the time.
 */
final class HopperHall implements Contraption {

    private static final int[] CHAIN_ROWS = {2, 5, 8};
    private static final int IDLE_ROW = 11;
    private static final int STUCK_ROW = 13;
    private static final int FIRST_X = 1;
    private static final int LAST_X = 14;
    private static final int LOWER_BARRELS = 0;
    private static final int HOPPER_LAYER = 1;
    private static final int UPPER_BARRELS = 2;
    private static final int STUCK_ITEMS = 5;

    private final ChunkSite site;

    HopperHall(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int row : CHAIN_ROWS) {
            buildChain(row);
        }
        buildIdleRow();
        buildStuckRow();
    }

    private void buildChain(int z) {
        site.place(FIRST_X, UPPER_BARRELS, z, Material.BARREL);
        site.fill(FIRST_X, UPPER_BARRELS, z, Material.COBBLESTONE);
        for (int x = FIRST_X; x < LAST_X; x++) {
            site.place(x, HOPPER_LAYER, z, BlockStates.facing(Material.HOPPER, BlockFace.EAST));
        }
        site.place(LAST_X, HOPPER_LAYER, z, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        site.place(LAST_X, LOWER_BARRELS, z, Material.BARREL);
    }

    private void buildIdleRow() {
        for (int x = FIRST_X; x <= LAST_X; x++) {
            site.place(x, UPPER_BARRELS, IDLE_ROW, Material.BARREL);
            site.place(x, HOPPER_LAYER, IDLE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
            site.place(x, LOWER_BARRELS, IDLE_ROW, Material.BARREL);
        }
    }

    private void buildStuckRow() {
        for (int x = FIRST_X; x <= LAST_X; x++) {
            site.place(x, UPPER_BARRELS, STUCK_ROW, Material.BARREL);
            site.place(x, HOPPER_LAYER, STUCK_ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
            site.stock(x, HOPPER_LAYER, STUCK_ROW, Material.COBBLESTONE, STUCK_ITEMS);
            site.place(x, LOWER_BARRELS, STUCK_ROW, Material.BARREL);
            site.fill(x, LOWER_BARRELS, STUCK_ROW, Material.STONE);
        }
    }

    /** Items carried to the end of the chains; the idle and the stuck rows must not change. */
    @Override
    public Map<String, Long> totals() {
        return Map.of(
                "hopper_chain_items", chainItems(),
                "hopper_idle_items", rowItems(HOPPER_LAYER, IDLE_ROW) + rowItems(LOWER_BARRELS, IDLE_ROW),
                "hopper_stuck_items", rowItems(HOPPER_LAYER, STUCK_ROW));
    }

    private long chainItems() {
        long total = 0;
        for (int row : CHAIN_ROWS) {
            total += site.items(LAST_X, LOWER_BARRELS, row);
        }
        return total;
    }

    private long rowItems(int y, int z) {
        long total = 0;
        for (int x = FIRST_X; x <= LAST_X; x++) {
            total += site.items(x, y, z);
        }
        return total;
    }
}
