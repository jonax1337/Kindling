package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Minecart;

/**
 * A lag machine of entities: four walled pits of three by three blocks, each crammed with {@value #CARTS_PER_PIT}
 * minecarts that keep pushing each other, as minecart and entity-cramming lag machines do. The carts stand on a
 * grid a little apart, since carts on the very same spot do not push each other. It counts nothing.
 */
final class CartPit implements Contraption {

    private static final int CARTS_PER_PIT = 25;
    private static final int CARTS_PER_ROW = 5;
    /** A cart is 0.98 blocks wide: from 0.5 to 2.5 none reaches into a wall, and each overlaps the next. */
    private static final double CART_SPACING = 0.5;
    private static final double FIRST_CART = 0.5;
    private static final int PIT_SIZE = 3;
    private static final int WALL_HEIGHT = 2;
    private static final int[] PIT_CORNERS = {2, 10};

    private final ChunkSite site;

    CartPit(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        for (int x : PIT_CORNERS) {
            for (int z : PIT_CORNERS) {
                buildPit(x, z);
            }
        }
    }

    /** Walls around the pit whose inside starts at {@code (x, z)}, then the carts in it. */
    private void buildPit(int x, int z) {
        for (int y = 1; y <= WALL_HEIGHT; y++) {
            for (int wallX = x - 1; wallX <= x + PIT_SIZE; wallX++) {
                for (int wallZ = z - 1; wallZ <= z + PIT_SIZE; wallZ++) {
                    boolean inside = wallX >= x && wallX < x + PIT_SIZE && wallZ >= z && wallZ < z + PIT_SIZE;
                    site.place(wallX, y, wallZ, inside ? Material.AIR : Material.STONE);
                }
            }
        }
        for (int cart = 0; cart < CARTS_PER_PIT; cart++) {
            double cartX = x + FIRST_CART + cart % CARTS_PER_ROW * CART_SPACING;
            double cartZ = z + FIRST_CART + cart / CARTS_PER_ROW * CART_SPACING;
            site.spawnAt(cartX, 1, cartZ, Minecart.class);
        }
    }

    @Override
    public Map<String, Long> totals() {
        return Map.of();
    }
}
