package net.kindlingmc.preview;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;
import java.util.function.LongPredicate;
import net.kindlingmc.chunk.LookAheadDirection;

/**
 * The chunks of a player's preview ring: beyond the send distance, within the preview radius (both as circles), in the
 * order of the player's chunk queues ({@link LookAheadDirection}: ahead of a moving player first, along any one
 * direction by distance). Reuses its list between calls; one instance per player, tick thread only.
 */
final class RingChunks {
    private final LongArrayList chunks = new LongArrayList();
    private LookAheadDirection order = new LookAheadDirection();
    private int centerX;
    private int centerZ;
    private final LongComparator byOrder = (first, second) -> Double.compare(this.priority(first), this.priority(second));

    /**
     * The ring's chunks that {@code skip} does not reject, at most {@code limit}, first in order first.
     * The returned list is reused by the next call.
     */
    LongArrayList select(final int centerX, final int centerZ, final int sendDistance, final int radius,
                         final LookAheadDirection order, final LongPredicate skip, final int limit) {
        this.chunks.clear();
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.order = order;
        final int inner = sendDistance * sendDistance;
        final int outer = radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                final int distance = dx * dx + dz * dz;
                if (distance > inner && distance <= outer) {
                    final long chunk = CoordinateUtils.getChunkKey(centerX + dx, centerZ + dz);
                    if (!skip.test(chunk)) {
                        this.chunks.add(chunk);
                    }
                }
            }
        }
        LongArrays.quickSort(this.chunks.elements(), 0, this.chunks.size(), this.byOrder);
        this.chunks.size(Math.min(this.chunks.size(), Math.max(0, limit)));
        return this.chunks;
    }

    static boolean isWithin(final long chunk, final int centerX, final int centerZ, final int radius) {
        final int dx = CoordinateUtils.getChunkX(chunk) - centerX;
        final int dz = CoordinateUtils.getChunkZ(chunk) - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    private double priority(final long chunk) {
        return this.order.priority(CoordinateUtils.getChunkX(chunk) - this.centerX, CoordinateUtils.getChunkZ(chunk) - this.centerZ);
    }
}
