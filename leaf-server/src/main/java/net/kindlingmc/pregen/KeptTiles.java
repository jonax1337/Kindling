package net.kindlingmc.pregen;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;

/**
 * Keeps the generated chunks of the last tiles of a pregeneration in memory, and with them the half-generated chunks
 * around them, which are exactly what the next tile needs: without the ticket they would be saved, unloaded and read
 * back from disk a moment later. Older tiles are let go, so memory stays bounded.
 *
 * <p>The ticket sits one level below full. A kept chunk therefore leaves the world right after generation exactly as
 * an unloaded one does (unload event, entities hidden: not ticked, not counted for mob caps, not selectable); only its
 * data stays in memory. Used on the main thread only, where the chunk system completes full chunk loads.
 */
final class KeptTiles {

    private static final TicketType KEEP = ChunkSystemTicketType.create("kindling:pregen", Long::compareTo, 0L, TicketType.FLAG_LOADING);
    private static final int KEEP_LEVEL = ChunkHolderManager.FULL_LOADED_TICKET_LEVEL + 1;
    private static final int TILES_KEPT = 2;

    private final ChunkHolderManager holders;
    private final Map<Long, LongArrayList> chunksByTile = new TreeMap<>();

    KeptTiles(final ServerLevel level) {
        this.holders = ((ChunkSystemServerLevel) level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }

    void keep(final long tile, final int chunkX, final int chunkZ) {
        final long chunk = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        this.holders.addTicketAtLevel(KEEP, chunk, KEEP_LEVEL, chunk);
        this.chunksByTile.computeIfAbsent(tile, ignored -> new LongArrayList()).add(chunk);
    }

    /** Lets go of the tiles before {@code currentTile}, except the last {@value #TILES_KEPT} - 1 of them. */
    void releaseBefore(final long currentTile) {
        final var tiles = this.chunksByTile.entrySet().iterator();
        while (tiles.hasNext()) {
            final Map.Entry<Long, LongArrayList> tile = tiles.next();
            if (tile.getKey() > currentTile - TILES_KEPT) {
                return;
            }
            this.release(tile.getValue());
            tiles.remove();
        }
    }

    void releaseAll() {
        this.chunksByTile.values().forEach(this::release);
        this.chunksByTile.clear();
    }

    private void release(final LongArrayList chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            final long chunk = chunks.getLong(i);
            this.holders.removeTicketAtLevel(KEEP, chunk, KEEP_LEVEL, chunk);
        }
    }
}
