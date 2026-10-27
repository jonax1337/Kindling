package net.kindlingmc.bench;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * One piece of a survival base: {@value #CHUNKS} chunks in a row on a stone floor high above the terrain, so the
 * landscape never touches what is built on it. Every contraption keeps to its own chunk, so the order in which the
 * server ticks chunks cannot change what a contraption does.
 */
final class Plot {

    static final int CHUNKS = 3;
    static final int FLOOR_Y = 199;

    private static final int FLOOR = 0;
    private static final int CLEARED_BELOW = 2;
    private static final int CLEARED_ABOVE = 8;

    private final World world;
    private final int firstChunkX;
    private final int chunkZ;

    Plot(World world, int firstChunkX, int chunkZ) {
        this.world = world;
        this.firstChunkX = firstChunkX;
        this.chunkZ = chunkZ;
    }

    /** The plot's chunk number {@code index}, counted from 0 in the west. */
    ChunkSite site(int index) {
        return new ChunkSite(world, firstChunkX + index, FLOOR_Y, chunkZ);
    }

    /** Lays the floor and clears the air around it in every chunk of the plot; loads the chunks if needed. */
    void clear() {
        for (int index = 0; index < CHUNKS; index++) {
            ChunkSite site = site(index);
            for (int y = -CLEARED_BELOW; y <= CLEARED_ABOVE; y++) {
                site.layer(y, y == FLOOR ? Material.SMOOTH_STONE : Material.AIR);
            }
        }
    }

    /** Whether every chunk of the plot ticks its block entities and entities, the state a base near a player is in. */
    boolean isTicking() {
        for (int x = firstChunkX; x < firstChunkX + CHUNKS; x++) {
            if (!world.isChunkLoaded(x, chunkZ) || world.getChunkAt(x, chunkZ).getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) {
                return false;
            }
        }
        return true;
    }
}
