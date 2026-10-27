package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Brewing stands and furnaces that are ready to work at once but whose fuel a plugin refuses at first
 * ({@link FuelGate}). Each stand holds three water bottles, nether wart and blaze powder; each furnace holds
 * cobblestone and coal. The counters tell how much has been brewed and smelted at the end.
 */
final class GatedFuelRow implements Contraption {

    private static final int[] COLUMNS = {0, 2};
    private static final int STAND_ROW = 0;
    private static final int FURNACE_ROW = 4;
    private static final int LAYER = 1;
    private static final int BOTTLE_SLOTS = 3;
    private static final int INGREDIENT_SLOT = 3;
    private static final int BREWING_FUEL_SLOT = 4;
    private static final int INPUT_SLOT = 0;
    private static final int BURNING_FUEL_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;
    private static final int COBBLESTONE = 16;
    private static final int COAL = 4;

    private final ChunkSite site;

    GatedFuelRow(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int x : COLUMNS) {
            buildStand(x);
            buildFurnace(x);
        }
    }

    private void buildStand(int x) {
        site.place(x, LAYER, STAND_ROW, Material.BREWING_STAND);
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            site.put(x, LAYER, STAND_ROW, slot, BrewingRow.waterBottle());
        }
        site.put(x, LAYER, STAND_ROW, INGREDIENT_SLOT, new ItemStack(Material.NETHER_WART));
        site.put(x, LAYER, STAND_ROW, BREWING_FUEL_SLOT, new ItemStack(Material.BLAZE_POWDER));
    }

    private void buildFurnace(int x) {
        site.place(x, LAYER, FURNACE_ROW, Material.FURNACE);
        site.put(x, LAYER, FURNACE_ROW, INPUT_SLOT, new ItemStack(Material.COBBLESTONE, COBBLESTONE));
        site.put(x, LAYER, FURNACE_ROW, BURNING_FUEL_SLOT, new ItemStack(Material.COAL, COAL));
    }

    @Override
    public Map<String, Long> totals() {
        return Map.of("gate_brewed_potions", brewedPotions(), "gate_smelted_items", smeltedItems());
    }

    private long brewedPotions() {
        long brewed = 0;
        for (int x : COLUMNS) {
            for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
                if (isAwkward(site.stackIn(x, LAYER, STAND_ROW, slot))) {
                    brewed++;
                }
            }
        }
        return brewed;
    }

    private static boolean isAwkward(ItemStack stack) {
        return stack != null && stack.getItemMeta() instanceof PotionMeta potion
                && potion.getBasePotionType() == PotionType.AWKWARD;
    }

    private long smeltedItems() {
        long smelted = 0;
        for (int x : COLUMNS) {
            smelted += site.itemsIn(x, LAYER, FURNACE_ROW, OUTPUT_SLOT);
        }
        return smelted;
    }
}
