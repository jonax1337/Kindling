package net.kindlingmc.preview;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.kindlingmc.chunk.LookAheadDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingChunksTest {

    private static final int SEND_DISTANCE = 2;
    private static final int RADIUS = 4;
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private final RingChunks ring = new RingChunks();

    @Test
    void theRingLiesBeyondTheSendDistanceAndWithinTheRadius() {
        final LongArrayList chunks = this.select(new LookAheadDirection(), NO_LIMIT);

        for (int i = 0; i < chunks.size(); i++) {
            final int squared = squaredDistance(chunks.getLong(i));
            assertTrue(squared > SEND_DISTANCE * SEND_DISTANCE && squared <= RADIUS * RADIUS);
        }
        assertEquals(countRing(), chunks.size());
    }

    @Test
    void rejectedChunksAreLeftOut() {
        final long rejected = CoordinateUtils.getChunkKey(RADIUS, 0);

        final LongArrayList chunks = this.ring.select(0, 0, SEND_DISTANCE, RADIUS, new LookAheadDirection(), chunk -> chunk == rejected, NO_LIMIT);

        assertFalse(chunks.contains(rejected));
        assertEquals(countRing() - 1, chunks.size());
    }

    @Test
    void withoutMovementTheNearestComeFirst() {
        final LongArrayList chunks = this.select(new LookAheadDirection(), NO_LIMIT);

        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(squaredDistance(chunks.getLong(i - 1)) <= squaredDistance(chunks.getLong(i)));
        }
    }

    @Test
    void aMovingPlayerGetsTheChunksAheadFirst() {
        final LongArrayList chunks = this.select(flyingTowardsPositiveX(), NO_LIMIT);

        assertTrue(chunks.indexOf(CoordinateUtils.getChunkKey(RADIUS, 0)) < chunks.indexOf(CoordinateUtils.getChunkKey(-RADIUS, 0)));
    }

    @Test
    void theLimitKeepsTheFirstInOrder() {
        final LongArrayList all = new LongArrayList(this.select(new LookAheadDirection(), NO_LIMIT));

        final LongArrayList limited = this.select(new LookAheadDirection(), 3);

        assertEquals(all.subList(0, 3), limited);
    }

    @Test
    void isWithinCountsTheEdgeOfTheRadius() {
        assertTrue(RingChunks.isWithin(CoordinateUtils.getChunkKey(RADIUS, 0), 0, 0, RADIUS));
        assertFalse(RingChunks.isWithin(CoordinateUtils.getChunkKey(RADIUS, 1), 0, 0, RADIUS));
    }

    private LongArrayList select(final LookAheadDirection order, final int limit) {
        return this.ring.select(0, 0, SEND_DISTANCE, RADIUS, order, chunk -> false, limit);
    }

    private static LookAheadDirection flyingTowardsPositiveX() {
        final LookAheadDirection order = new LookAheadDirection();
        final int ticksPerChunk = 5;
        for (int step = 0; step <= 4; step++) {
            order.update(step, 0, (long) step * ticksPerChunk, 10, 60);
        }
        return order;
    }

    private static int squaredDistance(final long chunk) {
        final int x = CoordinateUtils.getChunkX(chunk);
        final int z = CoordinateUtils.getChunkZ(chunk);
        return x * x + z * z;
    }

    private static int countRing() {
        int count = 0;
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                final int squared = dx * dx + dz * dz;
                if (squared > SEND_DISTANCE * SEND_DISTANCE && squared <= RADIUS * RADIUS) {
                    count++;
                }
            }
        }
        return count;
    }
}
