package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.inventory.ItemStack;

/**
 * The item sorter of a storage room. Cobblestone and dirt take turns falling onto a line of hoppers; under the line a
 * filter hopper holds {@value #FILTER_STACK} cobblestone and one stick in each other slot, so it takes in cobblestone
 * only. A comparator reads it: signal 2, or 3 with one cobblestone more, and only signal 3 reaches far enough along
 * the dust to switch off the torch that locks the outlet hopper underneath. For the few ticks it is unlocked, the
 * outlet hands the cobblestone it took last time into the sorted barrel and takes the new one; dirt runs on to the
 * end of the line.
 *
 * <p>Layout, from the corner: the line runs east on its layer above the filter (x 1) to its end (x 3); the outlet,
 * the torch under it and the comparator with its dust lie below and south of the filter.
 */
final class ItemSorter implements Contraption, Clocked {

    /** With the four sticks the filter reads signal 2: (41 + 4) / 64 / 5 slots; one cobblestone more makes it 3. */
    private static final int FILTER_STACK = 41;
    private static final int FILTER_SLOTS = 5;
    private static final int LINE_LAYER = 4;
    private static final int FILTER_LAYER = 3;
    private static final int OUTLET_LAYER = 2;
    private static final int TORCH_LAYER = 1;
    private static final int FIRST_LINE_X = 0;
    private static final int FILTER_X = 1;
    private static final int DUST_X = 2;
    private static final int LINE_END_X = 3;
    private static final int LINE_ROW = 0;
    /** The comparator, the sorted barrel it stands on, and dust beside it. */
    private static final int COMPARATOR_ROW = 1;
    private static final int RELAY_ROW = 2;

    private final ChunkSite site;
    private boolean dropFilteredItem = true;

    ItemSorter(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        buildLine();
        buildFilter();
        buildLock();
    }

    private void buildLine() {
        for (int x = FIRST_LINE_X; x < LINE_END_X; x++) {
            site.place(x, LINE_LAYER, LINE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.EAST));
        }
        site.place(LINE_END_X, LINE_LAYER, LINE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        site.place(LINE_END_X, FILTER_LAYER, LINE_ROW, Material.BARREL);
    }

    /** The filter hopper faces into air, so items leave it only through the locked outlet underneath. */
    private void buildFilter() {
        site.place(FILTER_X, FILTER_LAYER, LINE_ROW, BlockStates.facing(Material.HOPPER, BlockFace.WEST));
        site.put(FILTER_X, FILTER_LAYER, LINE_ROW, 0, new ItemStack(Material.COBBLESTONE, FILTER_STACK));
        for (int slot = 1; slot < FILTER_SLOTS; slot++) {
            site.put(FILTER_X, FILTER_LAYER, LINE_ROW, slot, new ItemStack(Material.STICK));
        }
        site.place(FILTER_X, OUTLET_LAYER, LINE_ROW, lockedHopper(BlockFace.SOUTH));
        site.place(FILTER_X, OUTLET_LAYER, COMPARATOR_ROW, Material.BARREL);
    }

    /**
     * Comparator, a block it powers, then three dust: the third, on the block that holds the torch, gets the
     * comparator's signal minus two, so it is on only at signal 3.
     */
    private void buildLock() {
        site.place(FILTER_X, FILTER_LAYER, COMPARATOR_ROW, BlockStates.facing(Material.COMPARATOR, BlockFace.NORTH));
        site.place(FILTER_X, FILTER_LAYER, RELAY_ROW, Material.STONE);
        site.place(DUST_X, OUTLET_LAYER, RELAY_ROW, Material.STONE);
        site.place(DUST_X, FILTER_LAYER, RELAY_ROW, BlockStates.wire(BlockFace.NORTH, BlockFace.SOUTH));
        site.place(DUST_X, OUTLET_LAYER, COMPARATOR_ROW, Material.STONE);
        site.place(DUST_X, FILTER_LAYER, COMPARATOR_ROW,
                BlockStates.wire(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST));
        site.place(DUST_X, TORCH_LAYER, LINE_ROW, Material.STONE);
        site.place(DUST_X, OUTLET_LAYER, LINE_ROW, BlockStates.wire(BlockFace.NORTH, BlockFace.SOUTH));
        site.place(FILTER_X, TORCH_LAYER, LINE_ROW, BlockStates.facing(Material.REDSTONE_WALL_TORCH, BlockFace.WEST));
    }

    /** Locked from the start, as the lit torch under it would have locked it. */
    private static BlockData lockedHopper(BlockFace face) {
        Hopper hopper = (Hopper) BlockStates.facing(Material.HOPPER, face);
        hopper.setEnabled(false);
        return hopper;
    }

    /** Cobblestone and dirt by turns, so cobblestone arrives every other step and the torch never burns out. */
    @Override
    public void clockStep() {
        site.dropOnto(FIRST_LINE_X, LINE_LAYER, LINE_ROW, dropFilteredItem ? Material.COBBLESTONE : Material.DIRT);
        dropFilteredItem = !dropFilteredItem;
    }

    /** Cobblestone that passed the filter, in the outlet or the sorted barrel, and what reached the end of the line. */
    @Override
    public Map<String, Long> totals() {
        long sorted = site.items(FILTER_X, OUTLET_LAYER, LINE_ROW) + site.items(FILTER_X, OUTLET_LAYER, COMPARATOR_ROW);
        return Map.of(
                "sorter_sorted_items", sorted,
                "sorter_passed_items", site.items(LINE_END_X, FILTER_LAYER, LINE_ROW));
    }
}
