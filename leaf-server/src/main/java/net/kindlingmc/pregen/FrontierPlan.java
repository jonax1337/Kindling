package net.kindlingmc.pregen;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.OptionalLong;

/**
 * Which tiles a world's frontier pregeneration still has to generate: the tiles within a margin of a tile players have
 * been in ({@link #visit}) that are not generated yet, nearest to a visited tile first. A tile counts as generated once
 * {@link #markDone} said so; it is never offered again.
 *
 * <p>Pending tiles are kept with their distance, in tiles, to the nearest visited tile, so a visit that brings a tile
 * closer only lowers its distance. Used on the main thread only.
 */
final class FrontierPlan {

    private final TileSet visited;
    private final TileSet done;
    private final Long2IntOpenHashMap pending = new Long2IntOpenHashMap();
    private long revision;

    /** @param visited the tiles players were in; @param done the tiles generated up to their last chunk inside the border */
    FrontierPlan(final TileSet visited, final TileSet done, final int marginTiles) {
        this.visited = visited;
        this.done = done;
        this.pending.defaultReturnValue(Integer.MAX_VALUE);
        for (final long tile : visited.toArray()) {
            this.enlistAround(tile, marginTiles);
        }
    }

    /** The margin in whole tiles that covers {@code marginChunks} beyond the visited tile. */
    static int marginTiles(final int marginChunks) {
        return Math.ceilDiv(marginChunks, TileSpiral.TILE_SIDE);
    }

    /** Notes that a player was in the tile; @return whether the tile was new */
    boolean visit(final long tile, final int marginTiles) {
        if (!this.visited.add(tile)) {
            return false;
        }
        this.revision++;
        this.enlistAround(tile, marginTiles);
        return true;
    }

    void markDone(final long tile) {
        this.done.add(tile);
        this.pending.remove(tile);
        this.revision++;
    }

    /** The pending tile nearest to a visited one that has chunks inside the border; ties go to the lower tile key. */
    OptionalLong nearest(final ChunkBorder border) {
        boolean found = false;
        long bestTile = 0L;
        int bestDistance = Integer.MAX_VALUE;
        final ObjectIterator<Long2IntMap.Entry> entries = Long2IntMaps.fastIterator(this.pending);
        while (entries.hasNext()) {
            final Long2IntMap.Entry entry = entries.next();
            final long tile = entry.getLongKey();
            final int distance = entry.getIntValue();
            final boolean nearer = distance < bestDistance || distance == bestDistance && tile < bestTile;
            if (nearer && isReachable(tile, border)) {
                found = true;
                bestTile = tile;
                bestDistance = distance;
            }
        }
        return found ? OptionalLong.of(bestTile) : OptionalLong.empty();
    }

    /** How many pending tiles have chunks inside the border. */
    int pendingCount(final ChunkBorder border) {
        int count = 0;
        final ObjectIterator<Long2IntMap.Entry> entries = Long2IntMaps.fastIterator(this.pending);
        while (entries.hasNext()) {
            if (isReachable(entries.next().getLongKey(), border)) {
                count++;
            }
        }
        return count;
    }

    int visitedCount() {
        return this.visited.size();
    }

    int doneCount() {
        return this.done.size();
    }

    /** Grows with every change of the visited or generated tiles, so a saved state can tell it is outdated. */
    long revision() {
        return this.revision;
    }

    /** The tiles as they are now, independent of later changes, so another thread can write them. */
    FrontierState.Area snapshot(final long seed) {
        return new FrontierState.Area(seed, this.visited.copy(), this.done.copy());
    }

    private void enlistAround(final long visitedTile, final int marginTiles) {
        final int centerX = TileSet.x(visitedTile);
        final int centerZ = TileSet.z(visitedTile);
        for (int dz = -marginTiles; dz <= marginTiles; dz++) {
            for (int dx = -marginTiles; dx <= marginTiles; dx++) {
                this.enlist(TileSet.key(centerX + dx, centerZ + dz), Math.max(Math.abs(dx), Math.abs(dz)));
            }
        }
    }

    private void enlist(final long tile, final int distance) {
        if (!this.done.contains(tile) && distance < this.pending.get(tile)) {
            this.pending.put(tile, distance);
        }
    }

    private static boolean isReachable(final long tile, final ChunkBorder border) {
        final int firstChunkX = TileSet.x(tile) * TileSpiral.TILE_SIDE;
        final int firstChunkZ = TileSet.z(tile) * TileSpiral.TILE_SIDE;
        return border.touches(firstChunkX, firstChunkZ, firstChunkX + TileSpiral.TILE_SIDE - 1, firstChunkZ + TileSpiral.TILE_SIDE - 1);
    }
}
