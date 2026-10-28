package net.kindlingmc.pregen;

import java.util.OptionalDouble;
import net.kindlingmc.doctor.ByteSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierTaskTest {

    private static final long BUDGET_NANOS = 40_000_000L;
    private static final long CALM_NANOS = 10_000_000L;
    private static final long BUSY_NANOS = 43_000_000L;
    private static final long RESERVE_BYTES = 2 * ByteSize.GIB;
    private static final PregenLimits LIMITS = new PregenLimits(BUDGET_NANOS, 32, RESERVE_BYTES, 0.10);
    private static final int TILE_CHUNKS = 256;

    private final FakeWorld world = new FakeWorld();
    private long tick;

    @Test
    void aVisitedTileIsGeneratedChunkByChunkAndThenMarkedDone() {
        final FrontierPlan plan = this.planVisiting(0, 0);
        final FrontierTask task = new FrontierTask(this.world, plan);

        this.runToTheEnd(task);

        assertEquals(TILE_CHUNKS, this.world.requests.size());
        this.world.requests.forEach(request -> assertTrue(request.chunkX >= 0 && request.chunkX < 16 && request.chunkZ >= 0 && request.chunkZ < 16));
        assertEquals(1, plan.doneCount());
        assertTrue(plan.nearest(this.world.border).isEmpty());
        assertTrue(task.finished());
        assertFalse(task.active());
    }

    @Test
    void everyChunkOfATileInNegativeCoordinatesIsRequestedOnce() {
        final FrontierPlan plan = this.planVisiting(-1, -3);
        final FrontierTask task = new FrontierTask(this.world, plan);

        this.runToTheEnd(task);

        assertEquals(TILE_CHUNKS, this.world.requests.stream().map(request -> request.chunkX + "," + request.chunkZ).distinct().count());
        this.world.requests.forEach(request -> assertTrue(request.chunkX >= -16 && request.chunkX < 0 && request.chunkZ >= -48 && request.chunkZ < -32));
    }

    @Test
    void theTilesNearestToAVisitedOneComeFirst() {
        final FrontierPlan plan = this.planVisiting(0, 0, 1);
        final FrontierTask task = new FrontierTask(this.world, plan);

        this.runToTheEnd(task);

        assertEquals(9 * TILE_CHUNKS, this.world.requests.size());
        this.world.requests.subList(0, TILE_CHUNKS).forEach(request -> assertEquals(TileSet.key(0, 0), TileSet.tileOfChunk(request.chunkX, request.chunkZ)));
        assertEquals(9, plan.doneCount());
    }

    @Test
    void onlyChunksInsideTheBorderAreRequestedAndTheTileCountsAsDone() {
        this.world.border = new ChunkBorder(0, 7, 0, 3);
        final FrontierPlan plan = this.planVisiting(0, 0);
        final FrontierTask task = new FrontierTask(this.world, plan);

        this.runToTheEnd(task);

        assertEquals(8 * 4, this.world.requests.size());
        assertEquals(1, plan.doneCount());
    }

    @Test
    void aTileIsNotDoneWhileOneOfItsChunksIsStillInFlight() {
        final FrontierPlan plan = this.planVisiting(0, 0);
        final FrontierTask task = new FrontierTask(this.world, plan);
        while (this.world.requests.size() < TILE_CHUNKS) {
            this.pump(task, CALM_NANOS);
            this.completeAllBut(this.world.requests.size() - 1);
        }

        this.pump(task, CALM_NANOS);

        assertEquals(0, plan.doneCount());
        assertFalse(task.finished());
        this.world.completeAll();
        this.pump(task, CALM_NANOS);
        assertEquals(1, plan.doneCount());
    }

    @Test
    void chunksInFlightGrowByOneASecondAndHalveWhileTheServerIsBusy() {
        final FrontierTask task = new FrontierTask(this.world, this.planVisiting(0, 0, 1));

        this.pump(task, CALM_NANOS);
        this.pump(task, CALM_NANOS);
        assertEquals(3, this.world.requests.size());
        this.pump(task, BUSY_NANOS);

        assertEquals(3, this.world.requests.size());
        assertTrue(task.describe().contains("throttled"), task.describe());
        assertTrue(task.describe().contains("(up to 1)"), task.describe());
    }

    @Test
    void pausingStopsSchedulingAtOnceAndReleasesTheKeptChunks() {
        final FrontierTask task = new FrontierTask(this.world, this.planVisiting(0, 0));
        this.pump(task, CALM_NANOS);
        final int requested = this.world.requests.size();
        this.world.complete(0, true);
        assertFalse(this.world.tickets.isEmpty());

        task.pause();
        this.pump(task, CALM_NANOS);
        this.pump(task, CALM_NANOS);

        assertEquals(requested, this.world.requests.size());
        assertTrue(this.world.tickets.isEmpty());
        assertFalse(task.active());
        assertTrue(task.describe().endsWith(", paused"), task.describe());
    }

    @Test
    void aResumedTaskFinishesTheTileItLeftOpen() {
        final FrontierPlan plan = this.planVisiting(0, 0);
        final FrontierTask task = new FrontierTask(this.world, plan);
        this.pump(task, CALM_NANOS);
        this.pump(task, CALM_NANOS);
        task.pause();
        this.world.completeAll();

        task.resume();
        this.runToTheEnd(task);

        assertEquals(TILE_CHUNKS, this.world.requests.size());
        assertEquals(1, plan.doneCount());
    }

    @Test
    void aClosedTaskKeepsNoChunk() {
        final FrontierTask task = new FrontierTask(this.world, this.planVisiting(0, 0));
        this.pump(task, CALM_NANOS);
        this.world.complete(0, true);

        task.close();
        this.pump(task, CALM_NANOS);
        this.world.completeAll();

        assertTrue(this.world.tickets.isEmpty());
    }

    @Test
    void aDiskThatWouldRunOutStopsTheTaskAndSaysSo() {
        this.world.freeBytes = ByteSize.GIB;
        final FrontierTask task = new FrontierTask(this.world, this.planVisiting(0, 0));

        this.pump(task, CALM_NANOS);

        assertTrue(this.world.requests.isEmpty());
        assertFalse(task.active());
        assertTrue(task.describe().endsWith(", paused: the disk would run out"), task.describe());
        assertTrue(task.describeDisk().contains("reserve 2.0 GiB"), task.describeDisk());
    }

    @Test
    void aStoppedDiskIsCheckedAgainWithoutPumpingAndTheTaskContinues() {
        this.world.freeBytes = ByteSize.GIB;
        final FrontierTask task = new FrontierTask(this.world, this.planVisiting(0, 0));
        this.pump(task, CALM_NANOS);
        this.world.freeBytes = 100 * ByteSize.GIB;

        for (int ticks = 0; ticks < 599; ticks++) {
            task.recheckFullDisk(RESERVE_BYTES);
        }
        assertFalse(task.active());
        task.recheckFullDisk(RESERVE_BYTES);

        assertTrue(task.active());
    }

    @Test
    void aRequestTheChunkSystemRefusesIsTakenBackSoTheTileStillGetsEveryChunk() {
        final FrontierTask task = new FrontierTask(this.world, this.planVisiting(0, 0));
        this.world.loadFailure = new IllegalStateException("the chunk system is down");

        assertThrows(IllegalStateException.class, () -> this.pump(task, CALM_NANOS));
        this.world.loadFailure = null;
        this.runToTheEnd(task);

        assertEquals(TILE_CHUNKS, this.world.requests.size());
    }

    @Test
    void aTaskWithoutPendingTilesIsFinishedAfterOnePump() {
        final FrontierTask task = new FrontierTask(this.world, new FrontierPlan(new TileSet(), new TileSet(), 2));

        this.pump(task, CALM_NANOS);

        assertTrue(task.finished());
        assertFalse(task.active());
    }

    /** A plan in which only the given tile was visited, without a margin. */
    private FrontierPlan planVisiting(final int tileX, final int tileZ) {
        return this.planVisiting(tileX, tileZ, 0);
    }

    private FrontierPlan planVisiting(final int tileX, final int tileZ, final int marginTiles) {
        final FrontierPlan plan = new FrontierPlan(new TileSet(), new TileSet(), marginTiles);
        plan.visit(TileSet.key(tileX, tileZ), marginTiles);
        return plan;
    }

    /** One server tick of a second's worth, so the chunks in flight grow as they do over time. */
    private void pump(final FrontierTask task, final long averageNanos) {
        this.tick += 20;
        task.pump(new TickLoad(this.tick, averageNanos, OptionalDouble.empty()), LIMITS);
    }

    private void completeAllBut(final int keptRequest) {
        for (int number = 0; number < this.world.requests.size(); number++) {
            if (number != keptRequest && !this.world.requests.get(number).answered) {
                this.world.complete(number, true);
            }
        }
    }

    private void runToTheEnd(final FrontierTask task) {
        for (int pumps = 0; pumps < 100_000 && !task.finished(); pumps++) {
            this.pump(task, CALM_NANOS);
            this.world.completeAll();
        }
        assertTrue(task.finished(), "the task did not finish");
    }
}
