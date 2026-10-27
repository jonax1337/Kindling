package net.kindlingmc.bench;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * One chunk of a plot, addressed in chunk-local coordinates: x and z from 0 to 15, y counted from the plot's floor
 * (0 is the floor layer, 1 the first layer above it). Blocks are placed without block updates and without their own
 * placement reactions (a piston does not look for power, an observer does not fire), so a contraption is complete
 * before anything in it reacts; block entities and their tickers are created as usual.
 */
final class ChunkSite {

    static final int SIZE = 16;

    private final World world;
    private final int minX;
    private final int floorY;
    private final int minZ;

    ChunkSite(World world, int chunkX, int floorY, int chunkZ) {
        this.world = world;
        this.minX = chunkX * SIZE;
        this.floorY = floorY;
        this.minZ = chunkZ * SIZE;
    }

    void place(int x, int y, int z, BlockData data) {
        block(x, y, z).setBlockData(data, false);
    }

    void place(int x, int y, int z, Material material) {
        place(x, y, z, material.createBlockData());
    }

    /** Sets every block of one layer of the chunk. */
    void layer(int y, Material material) {
        BlockData data = material.createBlockData();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                place(x, y, z, data);
            }
        }
    }

    /** Puts {@code amount} of {@code item} into the container at the position, as full stacks from the first slot on. */
    void stock(int x, int y, int z, Material item, int amount) {
        Inventory inventory = inventory(x, y, z);
        int stackSize = item.getMaxStackSize();
        int slot = 0;
        for (int left = amount; left > 0; left -= stackSize) {
            inventory.setItem(slot++, new ItemStack(item, Math.min(stackSize, left)));
        }
    }

    /** How many items the container at the position holds. */
    long items(int x, int y, int z) {
        long total = 0;
        for (ItemStack stack : inventory(x, y, z).getContents()) {
            if (stack != null) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    Block block(int x, int y, int z) {
        return world.getBlockAt(minX + x, floorY + y, minZ + z);
    }

    private Inventory inventory(int x, int y, int z) {
        return ((Container) block(x, y, z).getState(false)).getInventory();
    }
}
