package net.kindlingmc.pregen;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * A {@link ServerLevel} as a {@link PregenWorld}. The tickets sit one level below full: a kept chunk keeps its data in
 * memory without counting as a loaded chunk of the world.
 */
final class ServerPregenWorld implements PregenWorld, ChunkTickets {

    private static final TicketType KEEP = ChunkSystemTicketType.create("kindling:pregen", Long::compareTo, 0L, TicketType.FLAG_LOADING);
    private static final int KEEP_LEVEL = ChunkHolderManager.FULL_LOADED_TICKET_LEVEL + 1;

    private final ServerLevel level;
    private final ChunkHolderManager holders;

    ServerPregenWorld(final ServerLevel level) {
        this.level = level;
        this.holders = ((ChunkSystemServerLevel) level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }

    static String nameOf(final ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    @Override
    public String name() {
        return nameOf(this.level);
    }

    @Override
    public long seed() {
        return this.level.getSeed();
    }

    @Override
    public boolean isLoaded() {
        return this.level.getServer().getLevel(this.level.dimension()) == this.level;
    }

    @Override
    public ChunkBorder border() {
        final WorldBorder border = this.level.getWorldBorder();
        return ChunkBorder.ofBlocks(border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ());
    }

    @Override
    public void load(final int chunkX, final int chunkZ, final ChunkLoaded done) {
        ((ChunkSystemServerLevel) this.level).moonrise$getChunkTaskScheduler().scheduleChunkLoad(
            chunkX, chunkZ, ChunkStatus.FULL, true, Priority.IDLE, chunk -> done.done(chunk != null));
    }

    @Override
    public ChunkTickets tickets() {
        return this;
    }

    @Override
    public void add(final long chunkKey) {
        this.holders.addTicketAtLevel(KEEP, chunkKey, KEEP_LEVEL, chunkKey);
    }

    @Override
    public void remove(final long chunkKey) {
        this.holders.removeTicketAtLevel(KEEP, chunkKey, KEEP_LEVEL, chunkKey);
    }

    @Override
    public long freeDiskBytes() {
        try {
            return Files.getFileStore(this.level.getWorld().getWorldFolder().toPath()).getUsableSpace();
        } catch (final IOException e) {
            return UNKNOWN_FREE_BYTES;
        }
    }
}
