package net.kindlingmc.bench;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Brewing stands as players automate them: a hopper above each stand hands it one ingredient after the other, the next
 * as soon as a brew has used up the last, taking three water bottles through five brews of 20 seconds to splash
 * potions. All five brews end within the default warm-up and measurement; then the stands stand idle, as most brewing
 * stands on a server do.
 */
final class BrewingRow implements Contraption {

    private static final int[] COLUMNS = {0, 3};
    private static final int ROW = 0;
    private static final List<Material> INGREDIENTS = List.of(Material.NETHER_WART, Material.SUGAR, Material.REDSTONE,
            Material.FERMENTED_SPIDER_EYE, Material.GUNPOWDER);
    private static final int BOTTLE_SLOTS = 3;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int STAND = 1;
    private static final int HOPPER = 2;
    private static final int BARREL = 3;

    private final ChunkSite site;

    BrewingRow(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int x : COLUMNS) {
            buildStand(x);
        }
    }

    private void buildStand(int x) {
        site.place(x, STAND, ROW, Material.BREWING_STAND);
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            site.put(x, STAND, ROW, slot, waterBottle());
        }
        site.put(x, STAND, ROW, FUEL_SLOT, new ItemStack(Material.BLAZE_POWDER));
        site.place(x, HOPPER, ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        site.place(x, BARREL, ROW, Material.BARREL);
        for (int slot = 0; slot < INGREDIENTS.size(); slot++) {
            site.put(x, BARREL, ROW, slot, new ItemStack(INGREDIENTS.get(slot)));
        }
    }

    static ItemStack waterBottle() {
        ItemStack bottle = new ItemStack(Material.POTION);
        bottle.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.WATER));
        return bottle;
    }

    /** Brews that have ended: every ingredient no longer in the barrel, the hopper or the stand was used up by one. */
    @Override
    public Map<String, Long> totals() {
        long finished = 0;
        for (int x : COLUMNS) {
            long waiting = site.items(x, BARREL, ROW) + site.items(x, HOPPER, ROW)
                    + site.itemsIn(x, STAND, ROW, INGREDIENT_SLOT);
            finished += INGREDIENTS.size() - waiting;
        }
        return Map.of("brewing_brews_finished", finished);
    }
}
