package net.kindlingmc.stress;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;

/**
 * One chunk of a base plot, on a stone floor at {@value #FLOOR_Y}, high above the terrain. Blocks are addressed by
 * their position inside the chunk (0 to 15) and their height above the floor. Blocks are placed without physics, so a
 * contraption is built completely before any part of it acts. The chunk is kept loaded, so the plot ticks with nobody
 * nearby; it is built on only once it has loaded.
 */
final class PlotChunk {

    static final int FLOOR_Y = 199;

    private static final int CLEARED_BELOW = 2;
    private static final int CLEARED_ABOVE = 8;
    private static final int SIZE = 16;

    private final World world;
    private final KeptChunks kept;
    private final int chunkX;
    private final int chunkZ;

    PlotChunk(final World world, final KeptChunks kept, final int chunkX, final int chunkZ) {
        this.world = world;
        this.kept = kept;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    static BlockData facing(final Material material, final BlockFace face) {
        return material.createBlockData(data -> ((Directional) data).setFacing(face));
    }

    void keepLoaded() {
        this.kept.hold(this.chunkX, this.chunkZ);
    }

    boolean isLoaded() {
        return this.kept.isLoaded(this.chunkX, this.chunkZ);
    }

    /** Lays the floor and clears the air around it. */
    void layFloor() {
        for (int y = -CLEARED_BELOW; y <= CLEARED_ABOVE; y++) {
            this.layer(y, y == 0 ? Material.SMOOTH_STONE : Material.AIR);
        }
    }

    /** Removes everything built, floor included. */
    void clear() {
        for (int y = -CLEARED_BELOW; y <= CLEARED_ABOVE; y++) {
            this.layer(y, Material.AIR);
        }
    }

    private void layer(final int y, final Material material) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                this.place(x, y, z, material);
            }
        }
    }

    Block block(final int x, final int y, final int z) {
        return this.world.getBlockAt((this.chunkX << 4) + x, FLOOR_Y + y, (this.chunkZ << 4) + z);
    }

    void place(final int x, final int y, final int z, final Material material) {
        this.block(x, y, z).setType(material, false);
    }

    void place(final int x, final int y, final int z, final BlockData data) {
        this.block(x, y, z).setBlockData(data, false);
    }

    /** Puts {@code amount} of {@code item} into the container at the position. */
    void stock(final int x, final int y, final int z, final Material item, final int amount) {
        final Container container = (Container) this.block(x, y, z).getState(false);
        int left = amount;
        while (left > 0) {
            final int stack = Math.min(left, item.getMaxStackSize());
            container.getInventory().addItem(new ItemStack(item, stack));
            left -= stack;
        }
    }

    /** Fills every slot of the container at the position with {@code item}. */
    void fill(final int x, final int y, final int z, final Material item) {
        final Container container = (Container) this.block(x, y, z).getState(false);
        this.stock(x, y, z, item, container.getInventory().getSize() * item.getMaxStackSize());
    }
}
