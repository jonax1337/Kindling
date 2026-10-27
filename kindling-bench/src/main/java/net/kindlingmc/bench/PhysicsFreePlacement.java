package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * Chests that appear next to hoppers that have long had nothing to do, placed without block updates as tools and
 * plugins do ({@code Block#setType(type, false)} and {@code Block#setBlockData(data, false)}). One pair of hoppers
 * points into air and gets its chest below; the other pair stands over a barrel and gets a stocked chest above. Every
 * hopper has to notice at once and move its items.
 */
final class PhysicsFreePlacement implements Contraption, Clocked {

    private static final int FLOOR = 0;
    private static final int HOPPER = 1;
    private static final int ABOVE_HOPPER = 2;
    private static final int TARGET_ROW = 0;
    private static final int SOURCE_ROW = 2;
    private static final int BY_TYPE = 0;
    private static final int BY_DATA = 2;
    private static final int[] COLUMNS = {BY_TYPE, BY_DATA};
    private static final int STOCK = 10;

    private final ChunkSite site;
    private final int placeAfterSteps;
    private int steps;

    PhysicsFreePlacement(ChunkSite site, int placeAfterSteps) {
        this.site = site;
        this.placeAfterSteps = placeAfterSteps;
    }

    @Override
    public void build() {
        for (int x : COLUMNS) {
            site.place(x, FLOOR, TARGET_ROW, Material.AIR);
            site.place(x, HOPPER, TARGET_ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
            site.stock(x, HOPPER, TARGET_ROW, Material.COBBLESTONE, STOCK);
            site.place(x, FLOOR, SOURCE_ROW, Material.BARREL);
            site.place(x, HOPPER, SOURCE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        }
    }

    @Override
    public void clockStep() {
        if (++steps == placeAfterSteps) {
            placeChests();
        }
    }

    private void placeChests() {
        site.placeType(BY_TYPE, FLOOR, TARGET_ROW, Material.CHEST);
        site.place(BY_DATA, FLOOR, TARGET_ROW, Material.CHEST);
        site.placeType(BY_TYPE, ABOVE_HOPPER, SOURCE_ROW, Material.CHEST);
        site.place(BY_DATA, ABOVE_HOPPER, SOURCE_ROW, Material.CHEST);
        for (int x : COLUMNS) {
            site.stock(x, ABOVE_HOPPER, SOURCE_ROW, Material.COBBLESTONE, STOCK);
        }
    }

    /** Items the hoppers moved into the late chests below them, and out of the late chests above them into barrels. */
    @Override
    public Map<String, Long> totals() {
        long intoChests = 0;
        long intoBarrels = 0;
        for (int x : COLUMNS) {
            if (site.hasContainer(x, FLOOR, TARGET_ROW)) {
                intoChests += site.items(x, FLOOR, TARGET_ROW);
            }
            intoBarrels += site.items(x, FLOOR, SOURCE_ROW);
        }
        return Map.of("placement_target_arrived", intoChests, "placement_source_arrived", intoBarrels);
    }
}
