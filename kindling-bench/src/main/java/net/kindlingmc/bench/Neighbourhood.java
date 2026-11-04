package net.kindlingmc.bench;

import net.minecraft.server.level.ServerLevel;

/** The three by three chunks around a chunk. */
final class Neighbourhood {

    private Neighbourhood() {
    }

    /** Whether the chunk and its eight neighbours are loaded at full status. Call on the tick thread. */
    static boolean isFull(ServerLevel level, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
