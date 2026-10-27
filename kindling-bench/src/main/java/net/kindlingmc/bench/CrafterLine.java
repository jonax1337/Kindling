package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Crafter;
import org.bukkit.block.Orientation;

/**
 * A crafter on a clock, as in an automatic crafting line: a hopper keeps its one open slot filled with sugar cane, and
 * every switch-on of the clock crafts one sugar into the barrel in front of it.
 */
final class CrafterLine implements Contraption, Clocked {

    private static final int CRAFTER_X = 0;
    private static final int OUTPUT_X = 1;
    private static final int ROW = 0;
    private static final int CLOCK_ROW = 1;
    private static final int CRAFTER = 1;
    private static final int HOPPER = 2;
    private static final int BARREL = 3;
    private static final int CRAFTING_SLOTS = 9;
    private static final int OPEN_SLOT = 4;

    private final ChunkSite site;
    private final RedstoneSwitch clock;

    CrafterLine(ChunkSite site) {
        this.site = site;
        this.clock = new RedstoneSwitch(site.block(CRAFTER_X, CRAFTER, CLOCK_ROW));
    }

    @Override
    public void build() {
        site.place(CRAFTER_X, CRAFTER, ROW, BlockStates.crafter(Orientation.EAST_UP));
        openOnly(OPEN_SLOT);
        site.place(OUTPUT_X, CRAFTER, ROW, Material.BARREL);
        site.place(CRAFTER_X, HOPPER, ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        site.place(CRAFTER_X, BARREL, ROW, Material.BARREL);
        site.fill(CRAFTER_X, BARREL, ROW, Material.SUGAR_CANE);
    }

    /** With a single slot open the hopper fills only that one, so every craft finds the same recipe. */
    private void openOnly(int open) {
        Crafter crafter = (Crafter) site.block(CRAFTER_X, CRAFTER, ROW).getState(false);
        for (int slot = 0; slot < CRAFTING_SLOTS; slot++) {
            crafter.setSlotDisabled(slot, slot != open);
        }
    }

    @Override
    public void clockStep() {
        clock.toggle();
    }

    @Override
    public Map<String, Long> totals() {
        return Map.of("crafter_crafted_items", site.items(OUTPUT_X, CRAFTER, ROW));
    }
}
