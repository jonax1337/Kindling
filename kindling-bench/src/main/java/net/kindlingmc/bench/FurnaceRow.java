package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * A row of furnaces as players run them: a barrel of cobblestone above each, coal from the side and the stone carried
 * off by a hopper underneath, plus a few cold furnaces nobody uses. Smelting takes a fixed time, so the output is exact.
 */
final class FurnaceRow implements Contraption {

    private static final int FIRST_X = 2;
    private static final int LAST_X = 13;
    private static final int FURNACE_ROW = 6;
    private static final int FUEL_ROW = FURNACE_ROW - 1;
    private static final int OUTPUT_ROW = FURNACE_ROW + 1;
    private static final int COLD_ROW = 11;
    private static final int COLD_FURNACES = 4;
    private static final int ON_FLOOR = 1;
    private static final int FURNACE = 2;
    private static final int ABOVE_FURNACE = 3;
    private static final int TOP = 4;
    private static final int COBBLESTONE = 64;
    private static final int COAL = 16;

    private final ChunkSite site;

    FurnaceRow(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int x = FIRST_X; x <= LAST_X; x++) {
            buildColumn(x);
        }
        for (int x = FIRST_X; x < FIRST_X + COLD_FURNACES; x++) {
            site.place(x, ON_FLOOR, COLD_ROW, Material.FURNACE);
        }
    }

    private void buildColumn(int x) {
        site.place(x, ON_FLOOR, OUTPUT_ROW, Material.BARREL);
        site.place(x, ON_FLOOR, FURNACE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.SOUTH));
        site.place(x, FURNACE, FURNACE_ROW, Material.FURNACE);
        site.place(x, ABOVE_FURNACE, FURNACE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        site.place(x, TOP, FURNACE_ROW, Material.BARREL);
        site.stock(x, TOP, FURNACE_ROW, Material.COBBLESTONE, COBBLESTONE);
        site.place(x, FURNACE, FUEL_ROW, BlockStates.facing(Material.HOPPER, BlockFace.SOUTH));
        site.place(x, ABOVE_FURNACE, FUEL_ROW, Material.BARREL);
        site.stock(x, ABOVE_FURNACE, FUEL_ROW, Material.COAL, COAL);
    }

    @Override
    public Map<String, Long> totals() {
        long smelted = 0;
        for (int x = FIRST_X; x <= LAST_X; x++) {
            smelted += site.items(x, ON_FLOOR, OUTPUT_ROW);
        }
        return Map.of("furnace_smelted_items", smelted);
    }
}
