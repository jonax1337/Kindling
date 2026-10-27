package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;

/**
 * Hoppers handing items through containers that are not one block entity: a hopper minecart and a chest minecart
 * standing on rails, a composter fed pumpkin pies, and a double chest. In each column a hopper carries items from a
 * full barrel at the top into the container and a second hopper under it carries them on into a barrel at the bottom.
 */
final class TransferColumns implements Contraption {

    private static final int HOPPER_MINECART_X = 0;
    private static final int CHEST_MINECART_X = 3;
    private static final int COMPOSTER_X = 6;
    private static final int CHEST_IN_X = 9;
    private static final int CHEST_OUT_X = 10;
    private static final int ROW = 0;
    private static final int LOWER_BARREL = -1;
    private static final int LOWER_HOPPER = 0;
    private static final int MIDDLE = 1;
    private static final int UPPER_HOPPER = 2;
    private static final int UPPER_BARREL = 3;

    private final ChunkSite site;

    TransferColumns(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        buildMinecartColumn(HOPPER_MINECART_X, HopperMinecart.class);
        buildMinecartColumn(CHEST_MINECART_X, StorageMinecart.class);
        buildComposterColumn();
        buildDoubleChestColumns();
    }

    private void buildMinecartColumn(int x, Class<? extends Entity> minecartType) {
        feed(x, Material.COBBLESTONE);
        drain(x);
        site.place(x, MIDDLE, ROW, Material.RAIL);
        site.spawn(x, MIDDLE, ROW, minecartType);
    }

    private void buildComposterColumn() {
        feed(COMPOSTER_X, Material.PUMPKIN_PIE);
        site.place(COMPOSTER_X, MIDDLE, ROW, Material.COMPOSTER);
        drain(COMPOSTER_X);
    }

    /** Items go into one half and come out of the other, so they pass through the chest as a whole. */
    private void buildDoubleChestColumns() {
        feed(CHEST_IN_X, Material.COBBLESTONE);
        site.place(CHEST_IN_X, MIDDLE, ROW, chestHalf(Chest.Type.LEFT));
        site.place(CHEST_OUT_X, MIDDLE, ROW, chestHalf(Chest.Type.RIGHT));
        drain(CHEST_OUT_X);
    }

    private void feed(int x, Material item) {
        site.place(x, UPPER_BARREL, ROW, Material.BARREL);
        site.fill(x, UPPER_BARREL, ROW, item);
        site.place(x, UPPER_HOPPER, ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
    }

    private void drain(int x) {
        site.place(x, LOWER_HOPPER, ROW, BlockStates.facing(Material.HOPPER, BlockFace.DOWN));
        site.place(x, LOWER_BARREL, ROW, Material.BARREL);
    }

    /** Facing north, the left half joins the chest east of it and the right half the one west of it. */
    private static BlockData chestHalf(Chest.Type type) {
        Chest chest = (Chest) BlockStates.facing(Material.CHEST, BlockFace.NORTH);
        chest.setType(type);
        return chest;
    }

    @Override
    public Map<String, Long> totals() {
        return Map.of(
                "hopper_minecart_items", site.items(HOPPER_MINECART_X, LOWER_BARREL, ROW),
                "chest_minecart_items", site.items(CHEST_MINECART_X, LOWER_BARREL, ROW),
                "composter_bone_meal", site.items(COMPOSTER_X, LOWER_BARREL, ROW),
                "double_chest_items", site.items(CHEST_OUT_X, LOWER_BARREL, ROW));
    }
}
