package net.kindlingmc.stress;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

/**
 * The chunks a stress test keeps loaded and ticking with nobody nearby: the mob area and the base plots. Holding a
 * chunk only adds its forced ticket, so the chunk system loads or generates it in the background; Bukkit's
 * {@code setChunkForceLoaded} would load it on the main thread at once. The chunks are released together when the test
 * ends.
 */
final class KeptChunks {

    private final World world;
    private final ServerLevel level;
    private final Set<ChunkPos> held = new HashSet<>();

    KeptChunks(final World world) {
        this.world = world;
        this.level = ((CraftWorld) world).getHandle();
    }

    void hold(final int chunkX, final int chunkZ) {
        final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (this.held.add(pos)) {
            this.level.getChunkSource().updateChunkForced(pos, true);
        }
    }

    boolean isLoaded(final int chunkX, final int chunkZ) {
        return this.world.isChunkLoaded(chunkX, chunkZ);
    }

    void releaseAll() {
        for (final ChunkPos pos : this.held) {
            this.level.getChunkSource().updateChunkForced(pos, false);
        }
        this.held.clear();
    }
}
