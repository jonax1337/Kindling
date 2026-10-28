package net.kindlingmc.pregen;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierPlanTest {

    private static final ChunkBorder WIDE = new ChunkBorder(-1_000_000, 1_000_000, -1_000_000, 1_000_000);

    @Test
    void theMarginInChunksIsRoundedUpToWholeTiles() {
        assertEquals(0, FrontierPlan.marginTiles(0));
        assertEquals(1, FrontierPlan.marginTiles(1));
        assertEquals(1, FrontierPlan.marginTiles(16));
        assertEquals(2, FrontierPlan.marginTiles(17));
        assertEquals(2, FrontierPlan.marginTiles(32));
    }

    @Test
    void aPlanWithoutVisitsHasNothingToGenerate() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 2);

        assertTrue(plan.nearest(WIDE).isEmpty());
        assertEquals(0, plan.pendingCount(WIDE));
    }

    @Test
    void aVisitedTileAndItsMarginArePending() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 2);

        assertTrue(plan.visit(TileSet.key(10, -4), 2));

        assertEquals(25, plan.pendingCount(WIDE));
        assertEquals(1, plan.visitedCount());
    }

    @Test
    void theVisitedTileComesFirstAndTheRingsFollowByDistance() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 2);
        plan.visit(TileSet.key(0, 0), 2);

        final List<Integer> distances = new ArrayList<>();
        for (int count = 0; count < 25; count++) {
            final long tile = plan.nearest(WIDE).orElseThrow();
            distances.add(Math.max(Math.abs(TileSet.x(tile)), Math.abs(TileSet.z(tile))));
            plan.markDone(tile);
        }

        assertEquals(0, distances.get(0));
        assertEquals(distances.stream().sorted().toList(), distances);
        assertEquals(List.of(0, 1, 1, 1, 1, 1, 1, 1, 1), distances.subList(0, 9));
        assertTrue(plan.nearest(WIDE).isEmpty());
    }

    @Test
    void aTileGeneratedBeforeIsNeverOfferedAgain() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 1);
        plan.visit(TileSet.key(0, 0), 1);
        plan.markDone(TileSet.key(0, 0));

        plan.visit(TileSet.key(1, 0), 1);

        assertEquals(8 + 3, plan.pendingCount(WIDE));
        while (plan.nearest(WIDE).isPresent()) {
            plan.markDone(plan.nearest(WIDE).getAsLong());
        }
        assertEquals(4 * 3, plan.doneCount());
    }

    @Test
    void aLaterVisitBringsTilesNearerWithoutDuplicatingThem() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 3);
        plan.visit(TileSet.key(0, 0), 3);
        final int before = plan.pendingCount(WIDE);

        plan.visit(TileSet.key(3, 0), 3);

        assertEquals(before + 7 * 3, plan.pendingCount(WIDE));
        plan.markDone(plan.nearest(WIDE).getAsLong());
        plan.markDone(plan.nearest(WIDE).getAsLong());
        for (int count = 0; count < 16; count++) {
            final long tile = plan.nearest(WIDE).getAsLong();
            final int distance = Math.min(
                Math.max(Math.abs(TileSet.x(tile)), Math.abs(TileSet.z(tile))),
                Math.max(Math.abs(TileSet.x(tile) - 3), Math.abs(TileSet.z(tile))));
            assertEquals(1, distance, "tile " + TileSet.x(tile) + ", " + TileSet.z(tile));
            plan.markDone(tile);
        }
    }

    @Test
    void visitingATileAgainChangesNothing() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 1);
        plan.visit(TileSet.key(2, 2), 1);
        final long revision = plan.revision();

        assertFalse(plan.visit(TileSet.key(2, 2), 1));

        assertEquals(revision, plan.revision());
        assertEquals(9, plan.pendingCount(WIDE));
    }

    @Test
    void tilesBeyondTheBorderAreNeverOffered() {
        final ChunkBorder border = new ChunkBorder(0, 15, 0, 15);
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 2);
        plan.visit(TileSet.key(0, 0), 2);

        assertEquals(1, plan.pendingCount(border));
        assertEquals(TileSet.key(0, 0), plan.nearest(border).getAsLong());
        plan.markDone(TileSet.key(0, 0));
        assertTrue(plan.nearest(border).isEmpty());
    }

    @Test
    void aTileWithOneChunkInsideTheBorderIsStillOffered() {
        final ChunkBorder border = new ChunkBorder(-1, 0, -1, 0);
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 1);
        plan.visit(TileSet.key(0, 0), 1);

        assertEquals(4, plan.pendingCount(border));
    }

    @Test
    void aTileOutsideTheBorderIsOfferedOnceTheBorderGrows() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 1);
        plan.visit(TileSet.key(0, 0), 1);

        assertEquals(1, plan.pendingCount(new ChunkBorder(0, 15, 0, 15)));
        assertEquals(2, plan.pendingCount(new ChunkBorder(0, 31, 0, 15)));
    }

    @Test
    void aPlanRestoredFromSavedTilesKeepsOnlyTheTilesNotGeneratedYet() {
        final TileSet visited = TileSetTest.tilesOf(0, 0);
        final TileSet done = TileSetTest.tilesOf(0, 0, 1, 0, -1, -1);

        final FrontierPlan plan = new FrontierPlan(visited, done, 1);

        assertEquals(9 - 3, plan.pendingCount(WIDE));
        assertEquals(1, plan.visitedCount());
        assertEquals(3, plan.doneCount());
    }

    @Test
    void aLargerMarginAfterARestartAddsTheOuterTiles() {
        final TileSet visited = TileSetTest.tilesOf(0, 0);
        final TileSet done = TileSetTest.tilesOf(0, 0, 1, 0, -1, 0, 0, 1, 0, -1, 1, 1, 1, -1, -1, 1, -1, -1);

        final FrontierPlan plan = new FrontierPlan(visited, done, 2);

        assertEquals(25 - 9, plan.pendingCount(WIDE));
    }

    @Test
    void everyChangeRaisesTheRevision() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 0);
        final long start = plan.revision();

        plan.visit(TileSet.key(0, 0), 0);
        final long afterVisit = plan.revision();
        plan.markDone(TileSet.key(0, 0));

        assertTrue(afterVisit > start);
        assertTrue(plan.revision() > afterVisit);
    }

    @Test
    void aSnapshotDoesNotFollowLaterChanges() {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), 0);
        plan.visit(TileSet.key(0, 0), 0);

        final FrontierState.Area snapshot = plan.snapshot(7L);
        plan.visit(TileSet.key(5, 5), 0);
        plan.markDone(TileSet.key(0, 0));

        assertEquals(7L, snapshot.seed());
        assertEquals(1, snapshot.visited().size());
        assertEquals(0, snapshot.done().size());
    }
}
