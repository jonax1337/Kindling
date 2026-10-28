package net.kindlingmc.pregen;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiralsTest {

    @Test
    void theSquareSpiralNumbersEveryChunkOfTheSquareExactlyOnce() {
        for (int radius = 0; radius <= 12; radius++) {
            final long size = SquareSpiral.size(radius);
            final Set<Long> seen = new HashSet<>();
            for (long index = 0; index < size; index++) {
                final long offset = SquareSpiral.offset(index);
                assertTrue(Math.abs(SquareSpiral.x(offset)) <= radius && Math.abs(SquareSpiral.z(offset)) <= radius,
                    "index " + index + " leaves the square of radius " + radius);
                assertTrue(seen.add(offset), "index " + index + " repeats a chunk at radius " + radius);
            }
            assertEquals(size, seen.size());
        }
    }

    @Test
    void theLargestRadiusAWorldBorderAllowsDoesNotOverflowTheTileSpiral() {
        final ChunkBorder widest = ChunkBorder.ofBlocks(-30_000_000, 30_000_000, -30_000_000, 30_000_000);

        assertTrue(widest.reachFrom(-1_875_000, -1_875_000) <= TileSpiral.MAX_RADIUS);
        assertTrue(TileSpiral.size(TileSpiral.MAX_RADIUS) > TileSpiral.size(TileSpiral.MAX_RADIUS / 2));
        assertTrue(TileSpiral.size(TileSpiral.MAX_RADIUS / 2) > 0);
    }

    @Test
    void theSquareSpiralStartsAtTheCentreAndGrowsRingByRing() {
        assertEquals(SquareSpiral.pack(0, 0), SquareSpiral.offset(0));
        for (long index = 1; index < SquareSpiral.size(6); index++) {
            final long offset = SquareSpiral.offset(index);
            final int ring = Math.max(Math.abs(SquareSpiral.x(offset)), Math.abs(SquareSpiral.z(offset)));
            assertEquals(SquareSpiral.ringOf(index), ring, "index " + index);
        }
    }

    @Test
    void theTileSpiralNumbersEveryChunkInsideTheRadiusExactlyOnce() {
        for (final int radius : new int[] {0, 1, 7, 8, 9, 23, 24, 40}) {
            final Set<Long> inside = new HashSet<>();
            final Set<Long> all = new HashSet<>();
            for (long index = 0; index < TileSpiral.size(radius); index++) {
                final long offset = TileSpiral.offset(index);
                assertTrue(all.add(offset), "index " + index + " repeats a chunk at radius " + radius);
                if (TileSpiral.isInside(offset, radius)) {
                    inside.add(offset);
                }
            }
            final long side = 2L * radius + 1;
            assertEquals(side * side, inside.size(), "radius " + radius);
        }
    }

    @Test
    void theTileSpiralSizeIsAWholeNumberOfTiles() {
        for (int radius = 0; radius < 100; radius++) {
            assertEquals(0, TileSpiral.size(radius) % TileSpiral.TILE_CHUNKS);
        }
    }

    @Test
    void theChunksOfOneTileAreNeighboursInRowOrder() {
        final long first = TileSpiral.offset(5L * TileSpiral.TILE_CHUNKS);
        for (int inTile = 0; inTile < TileSpiral.TILE_CHUNKS; inTile++) {
            final long offset = TileSpiral.offset(5L * TileSpiral.TILE_CHUNKS + inTile);
            assertEquals(SquareSpiral.x(first) + inTile % TileSpiral.TILE_SIDE, SquareSpiral.x(offset));
            assertEquals(SquareSpiral.z(first) + inTile / TileSpiral.TILE_SIDE, SquareSpiral.z(offset));
        }
    }

    @Test
    void theCentreTileComesFirstAndHoldsTheCentreChunk() {
        boolean centreFound = false;
        for (int inTile = 0; inTile < TileSpiral.TILE_CHUNKS; inTile++) {
            centreFound |= TileSpiral.offset(inTile) == SquareSpiral.pack(0, 0);
        }
        assertTrue(centreFound);
    }

    @Test
    void aRingSavedByEarlierVersionsContinuesAtAWholeTileAtOrBeforeIt() {
        for (long ringIndex : new long[] {0, 1, 100, 5000, 123_456}) {
            final long resumed = TileSpiral.fromRingIndex(ringIndex);
            assertEquals(0, resumed % TileSpiral.TILE_CHUNKS);
        }
    }
}
