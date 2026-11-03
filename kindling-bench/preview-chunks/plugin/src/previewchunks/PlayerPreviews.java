package previewchunks;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * One player's previews: the radius they reach, the chunks that got one and the chunks being generated. The ring is
 * everything beyond the server's send distance within the radius; real chunks the server sent are left alone. Main
 * thread only.
 */
final class PlayerPreviews {
    /** The client drops chunks this far beyond its cache radius by itself; forget them here too. */
    private static final int CLIENT_MARGIN = 3;

    private final int radius;
    private final LongOpenHashSet sent = new LongOpenHashSet();
    private final LongOpenHashSet pending = new LongOpenHashSet();

    PlayerPreviews(int radius) {
        this.radius = radius;
    }

    /**
     * Keeps the client's chunk cache at the preview radius (the server sets it back on joins and world changes) and
     * returns the nearest chunks of the ring that need a preview, now marked as being generated.
     */
    List<ChunkPos> update(Player player, int maxPending) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send(new ClientboundSetChunkCacheRadiusPacket(this.radius));
        ChunkPos center = handle.chunkPosition();
        forgetFarPreviews(center);
        List<ChunkPos> wanted = wantedChunks(player, center);
        wanted.sort(Comparator.comparingInt(pos -> distanceSquared(center, pos.x(), pos.z())));
        List<ChunkPos> start = List.copyOf(wanted.subList(0, Math.min(wanted.size(), Math.max(0, maxPending - this.pending.size()))));
        start.forEach(pos -> this.pending.add(pos.pack()));
        return start;
    }

    boolean isWanted(Player player, ChunkPos pos) {
        ChunkPos center = ((CraftPlayer) player).getHandle().chunkPosition();
        return distanceSquared(center, pos.x(), pos.z()) <= this.radius * this.radius;
    }

    void delivered(long key) {
        this.pending.remove(key);
        this.sent.add(key);
    }

    void dropped(long key) {
        this.pending.remove(key);
    }

    /** Gives the client back the server's own cache radius, so it drops the previews. */
    static void restoreRadius(Player player) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundSetChunkCacheRadiusPacket(player.getSendViewDistance()));
    }

    private List<ChunkPos> wantedChunks(Player player, ChunkPos center) {
        int inner = player.getSendViewDistance();
        List<ChunkPos> wanted = new ArrayList<>();
        for (int dz = -this.radius; dz <= this.radius; dz++) {
            for (int dx = -this.radius; dx <= this.radius; dx++) {
                int distance = dx * dx + dz * dz;
                if (distance > inner * inner && distance <= this.radius * this.radius) {
                    addIfWanted(player, center.x() + dx, center.z() + dz, wanted);
                }
            }
        }
        return wanted;
    }

    private void addIfWanted(Player player, int x, int z, List<ChunkPos> wanted) {
        long key = ChunkPos.pack(x, z);
        if (player.isChunkSent(key)) {
            // The real chunk replaced the preview; should the server forget it later, the ring needs a preview again.
            this.sent.remove(key);
        } else if (!this.sent.contains(key) && !this.pending.contains(key)) {
            wanted.add(new ChunkPos(x, z));
        }
    }

    private void forgetFarPreviews(ChunkPos center) {
        int limit = this.radius + CLIENT_MARGIN;
        for (LongIterator keys = this.sent.iterator(); keys.hasNext(); ) {
            long key = keys.nextLong();
            if (distanceSquared(center, ChunkPos.getX(key), ChunkPos.getZ(key)) > limit * limit) {
                keys.remove();
            }
        }
    }

    private static int distanceSquared(ChunkPos center, int x, int z) {
        int dx = x - center.x();
        int dz = z - center.z();
        return dx * dx + dz * dz;
    }
}
