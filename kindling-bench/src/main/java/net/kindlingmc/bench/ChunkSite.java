package net.kindlingmc.bench;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * One chunk of a plot, addressed in chunk-local coordinates: x and z from 0 to 15, y counted from the plot's floor
 * (0 is the floor layer, 1 the first layer above it); {@link #corner} addresses a module in a part of the chunk the
 * same way. Blocks are placed without block updates and without their own placement reactions (a piston does not
 * look for power, an observer does not fire), so a contraption is complete before anything in it reacts; block
 * entities and their tickers are created as usual.
 */
final class ChunkSite {

    static final int SIZE = 16;
    private static final double BLOCK_CENTRE = 0.5;
    private static final double DROP_ABOVE_TOP = 0.25;

    private final World world;
    private final int minX;
    private final int floorY;
    private final int minZ;

    private ChunkSite(World world, int minX, int floorY, int minZ) {
        this.world = world;
        this.minX = minX;
        this.floorY = floorY;
        this.minZ = minZ;
    }

    static ChunkSite ofChunk(World world, int chunkX, int floorY, int chunkZ) {
        return new ChunkSite(world, chunkX * SIZE, floorY, chunkZ * SIZE);
    }

    /** The same site addressed from {@code (x, z)} on, for a module built into that part of the chunk. */
    ChunkSite corner(int x, int z) {
        return new ChunkSite(world, minX + x, floorY, minZ + z);
    }

    void place(int x, int y, int z, BlockData data) {
        block(x, y, z).setBlockData(data, false);
    }

    void place(int x, int y, int z, Material material) {
        place(x, y, z, material.createBlockData());
    }

    /** Sets the block through {@link Block#setType(Material, boolean)} without physics, the way plugins and tools place. */
    void placeType(int x, int y, int z, Material material) {
        block(x, y, z).setType(material, false);
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

    /** Fills every slot of the container at the position with a full stack of {@code item}. */
    void fill(int x, int y, int z, Material item) {
        Inventory inventory = inventory(x, y, z);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, new ItemStack(item, item.getMaxStackSize()));
        }
    }

    /** Puts {@code stack} into one slot of the container at the position. */
    void put(int x, int y, int z, int slot, ItemStack stack) {
        inventory(x, y, z).setItem(slot, stack);
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

    /** How many items one slot of the container at the position holds. */
    int itemsIn(int x, int y, int z, int slot) {
        ItemStack stack = stackIn(x, y, z, slot);
        return stack == null ? 0 : stack.getAmount();
    }

    /** The stack in one slot of the container at the position; null for an empty slot. */
    ItemStack stackIn(int x, int y, int z, int slot) {
        return inventory(x, y, z).getItem(slot);
    }

    /** Whether the block at the position holds an inventory; a container placed later is not there yet. */
    boolean hasContainer(int x, int y, int z) {
        return block(x, y, z).getState(false) instanceof Container;
    }

    /**
     * Drops one item at rest a quarter block above the top of the block at the position: without the random push a
     * dropped item gets, so it lands on the same spot in every run; above a hopper, inside the space it collects from.
     */
    void dropOnto(int x, int y, int z, Material item) {
        Location spot = centre(x, y + 1 + DROP_ABOVE_TOP, z);
        world.dropItem(spot, new ItemStack(item), dropped -> dropped.setVelocity(new Vector()));
    }

    <T extends Entity> T spawn(int x, int y, int z, Class<T> type) {
        return world.spawn(centre(x, y, z), type);
    }

    Block block(int x, int y, int z) {
        return world.getBlockAt(minX + x, floorY + y, minZ + z);
    }

    private Location centre(int x, double height, int z) {
        return new Location(world, minX + x + BLOCK_CENTRE, floorY + height, minZ + z + BLOCK_CENTRE);
    }

    private Inventory inventory(int x, int y, int z) {
        return ((Container) block(x, y, z).getState(false)).getInventory();
    }
}
