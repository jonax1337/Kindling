package net.kindlingmc.pregen;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.kindlingmc.doctor.ByteSize;
import net.kindlingmc.pregen.PregenState.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PregenTaskTest {

    private static final long BUDGET_NANOS = 40_000_000L;
    private static final long CALM_NANOS = 10_000_000L;
    private static final long BUSY_NANOS = 43_000_000L;
    private static final long RESERVE_BYTES = 2 * ByteSize.GIB;
    private static final double MAX_MAIN_THREAD_WAIT = 0.10;
    private static final PregenLimits LIMITS = new PregenLimits(BUDGET_NANOS, 32, RESERVE_BYTES, MAX_MAIN_THREAD_WAIT);
    private static final OptionalDouble NOT_MEASURED = OptionalDouble.empty();
    private static final int ANY_RADIUS = 40;

    private final FakeWorld world = new FakeWorld();
    private long tick;

    @Test
    void aVastAreaOutsideTheBorderIsWalkedOnlyUpToTheStepBoundPerTick() {
        this.world.border = new ChunkBorder(100_000, 100_010, 100_000, 100_010);
        final PregenTask task = this.task(1_875_000, 0L);

        this.pump(task, CALM_NANOS);

        assertTrue(this.world.requests.isEmpty());
        assertEquals((long) PregenTask.MAX_STEPS_PER_PUMP * TileSpiral.TILE_CHUNKS, task.resumeIndex());
        assertFalse(task.finished());
        assertTrue(task.describe().startsWith("minecraft:fake: 1,048,576 / "), task.describe());
    }

    @Test
    void anAreaWithManyChunksOutsideTheBorderStillGetsEveryChunkInsideIt() {
        this.world.border = new ChunkBorder(-20, 20, -20, 20);
        final PregenTask task = this.task(ANY_RADIUS, 0L);

        this.runToTheEnd(task);

        assertEquals(41 * 41, this.world.requests.size());
        this.world.requests.forEach(request -> assertTrue(this.world.border.contains(request.chunkX, request.chunkZ)));
        assertTrue(task.finished());
    }

    @Test
    void onlyTheChunksWithinTheRadiusAreRequested() {
        final PregenTask task = this.task(3, 0L);

        this.runToTheEnd(task);

        assertEquals(7 * 7, this.world.requests.size());
        this.world.requests.forEach(request -> assertTrue(Math.abs(request.chunkX) <= 3 && Math.abs(request.chunkZ) <= 3));
    }

    @Test
    void theAreaIsCentredOnTheGivenChunk() {
        final PregenTask task = new PregenTask(this.world, 100, -50, 1, 0L);

        this.runToTheEnd(task);

        assertEquals(9, this.world.requests.size());
        this.world.requests.forEach(request -> assertTrue(Math.abs(request.chunkX - 100) <= 1 && Math.abs(request.chunkZ + 50) <= 1));
    }

    @Test
    void theResumeIndexIsTheOldestChunkStillInFlight() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);
        this.pump(task, CALM_NANOS);
        assertEquals(3, this.world.requests.size());

        this.world.complete(1, true);
        assertEquals(0L, task.resumeIndex());
        this.world.complete(0, true);
        assertEquals(2L, task.resumeIndex());
        this.world.complete(2, true);
        assertEquals(3L, task.resumeIndex());
    }

    @Test
    void aRunContinuesAtTheGivenIndex() {
        final PregenTask task = this.task(ANY_RADIUS, 1000L);

        assertEquals(1000L, task.resumeIndex());
        assertTrue(task.describe().contains(" 1,000 / "), task.describe());
    }

    @Test
    void aRunIsFinishedWhenEveryIndexIsWalkedAndEveryChunkArrived() {
        final PregenTask task = this.task(0, 0L);

        this.pump(task, CALM_NANOS);
        assertEquals(1, this.world.requests.size());
        assertFalse(task.finished());

        this.world.completeAll();
        assertTrue(task.finished());
        assertFalse(task.active());
    }

    @Test
    void aPausedTaskSchedulesNothingAndLetsGoOfItsKeptChunks() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);
        this.world.complete(0, true);
        assertEquals(1, this.world.tickets.size());

        task.pause();
        assertTrue(this.world.tickets.isEmpty());
        final int requested = this.world.requests.size();
        this.pump(task, CALM_NANOS);
        assertEquals(requested, this.world.requests.size());
        assertTrue(task.paused());
        assertFalse(task.active());
        assertTrue(task.describe().endsWith(", paused"));

        task.resume();
        this.pump(task, CALM_NANOS);
        assertTrue(this.world.requests.size() > requested);
        assertTrue(task.active());
    }

    @Test
    void aChunkThatArrivesWhilePausedIsNotKept() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);

        task.pause();
        this.world.complete(0, true);

        assertTrue(this.world.tickets.isEmpty());
    }

    @Test
    void aRequestThatEndedWithoutAChunkKeepsNothing() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);

        this.world.complete(0, false);

        assertTrue(this.world.tickets.isEmpty());
    }

    @Test
    void closingLetsGoOfTheKeptChunks() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);
        this.world.complete(0, true);

        task.close();

        assertTrue(this.world.tickets.isEmpty());
    }

    @Test
    void aBusyServerHalvesTheChunksInFlightStartsNoneAndSaysWhy() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        for (int i = 0; i < 6; i++) {
            this.pump(task, CALM_NANOS);
        }
        final int requested = this.world.requests.size();
        assertTrue(task.describe().contains("up to 7)"), task.describe());

        this.pump(task, BUSY_NANOS);

        assertEquals(requested, this.world.requests.size());
        assertTrue(task.describe().contains("up to 3)"), task.describe());
        assertTrue(task.describe().endsWith(", throttled: 43 ms average > 40 ms"), task.describe());
    }

    @Test
    void aCalmServerEndsTheThrottleAndSchedulesAgain() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, BUSY_NANOS);
        assertTrue(this.world.requests.isEmpty());

        this.pump(task, CALM_NANOS);

        assertFalse(this.world.requests.isEmpty());
        assertFalse(task.describe().contains("throttled"), task.describe());
    }

    @Test
    void aMainThreadThatWaitsTooLongForACpuHalvesTheChunksInFlightAndSaysWhy() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        for (int i = 0; i < 6; i++) {
            this.pump(task, CALM_NANOS);
        }
        final int requested = this.world.requests.size();

        this.pump(task, CALM_NANOS, OptionalDouble.of(0.18));

        assertEquals(requested, this.world.requests.size());
        assertTrue(task.describe().contains("up to 3)"), task.describe());
        assertTrue(task.describe().endsWith(", throttled: the main thread waited for a CPU 18 % of the last minute > 10 %"), task.describe());
    }

    @Test
    void aMainThreadWaitBetweenRelaxedAndTheLimitKeepsTheChunksInFlight() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);
        final String before = task.describe();

        this.pump(task, CALM_NANOS, OptionalDouble.of(0.09));

        assertTrue(before.contains("up to 2)"), before);
        assertTrue(task.describe().contains("up to 2)"), task.describe());
        assertFalse(task.describe().contains("throttled"), task.describe());
    }

    @Test
    void aThrottledTaskStillKeepsTheServerAwake() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, BUSY_NANOS);

        assertTrue(task.active());
    }

    @Test
    void aCrashRestartsBeforeTheChunksKeptInMemoryAndTheOnesInFlight() {
        final PregenTask task = this.task(ANY_RADIUS, 5000L);

        assertEquals(5000L - TileSpiral.TILE_CHUNKS - 32, task.crashSafeResumeIndex(32));
    }

    @Test
    void aCrashRestartStepsBackOverTheKeptTilesToo() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.runUntilIndex(task, 3L * TileSpiral.TILE_CHUNKS);

        final long stepBack = task.resumeIndex() - task.crashSafeResumeIndex(32);

        assertTrue(stepBack >= TileSpiral.TILE_CHUNKS + 32, "stepped back " + stepBack);
        assertTrue(stepBack <= 3L * TileSpiral.TILE_CHUNKS + 32, "stepped back " + stepBack);
    }

    @Test
    void aCrashRestartNeverGoesBelowTheStart() {
        final PregenTask task = this.task(ANY_RADIUS, 10L);

        assertEquals(0L, task.crashSafeResumeIndex(32));
    }

    @Test
    void theSnapshotCarriesTheAreaThePhaseAndTheWorldSeed() {
        final PregenTask task = new PregenTask(this.world, -3, 7, 25, 0L);

        assertEquals(new PregenState.Run(-3, 7, 25, 99L, Phase.PAUSED, OptionalLong.of(42L)), task.snapshot(Phase.PAUSED, 99L));
    }

    @Test
    void theAreaIsDescribedWithItsRadiusInChunksAndBlocks() {
        assertEquals("radius 312 chunks (4992 blocks) around chunk (12, -5)", new PregenTask(this.world, 12, -5, 312, 0L).describeArea());
    }

    @Test
    void aDiskThatWouldRunOutStopsTheRunAndSaysSo() {
        this.world.freeBytes = ByteSize.GIB;
        final PregenTask task = this.task(ANY_RADIUS, 0L);

        this.pump(task, CALM_NANOS);

        assertTrue(this.world.requests.isEmpty());
        assertFalse(task.active());
        assertTrue(task.describe().endsWith(", paused: the disk would run out"), task.describe());
        assertTrue(task.describeDisk().contains("1.0 GiB free"), task.describeDisk());
        assertTrue(task.describeDisk().contains("reserve 2.0 GiB"), task.describeDisk());
    }

    @Test
    void aStoppedDiskReleasesTheKeptChunksAndContinuesWhenThereIsRoomAgain() {
        this.world.freeBytes = 100 * ByteSize.GIB;
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);
        this.world.complete(0, true);
        assertFalse(this.world.tickets.isEmpty());

        this.world.freeBytes = ByteSize.GIB;
        task.checkDisk(RESERVE_BYTES);
        assertTrue(this.world.tickets.isEmpty());
        assertFalse(task.active());

        this.world.freeBytes = 100 * ByteSize.GIB;
        task.checkDisk(RESERVE_BYTES);
        assertTrue(task.active());
        assertFalse(task.describe().contains("disk"), task.describe());
    }

    @Test
    void theDiskIsCheckedAgainEveryHalfMinute() {
        this.world.freeBytes = 100 * ByteSize.GIB;
        final PregenTask task = this.task(ANY_RADIUS, 0L);
        this.pump(task, CALM_NANOS);
        this.world.freeBytes = ByteSize.GIB;

        for (int pumps = 0; pumps < 599; pumps++) {
            task.pump(++this.tick, CALM_NANOS, NOT_MEASURED, LIMITS);
        }
        assertTrue(task.active());
        task.pump(++this.tick, CALM_NANOS, NOT_MEASURED, LIMITS);

        assertFalse(task.active());
    }

    @Test
    void aDiskOfUnknownSizeNeverStopsTheRun() {
        final PregenTask task = this.task(ANY_RADIUS, 0L);

        this.pump(task, CALM_NANOS);

        assertTrue(task.active());
        assertTrue(task.describeDisk().contains("unknown free"), task.describeDisk());
    }

    @Test
    void theDiskIsReportedAsNotCheckedBeforeTheFirstTick() {
        assertEquals("disk: not checked yet", this.task(ANY_RADIUS, 0L).describeDisk());
    }

    private PregenTask task(final int radius, final long next) {
        return new PregenTask(this.world, 0, 0, radius, next);
    }

    private void pump(final PregenTask task, final long averageNanos) {
        this.pump(task, averageNanos, NOT_MEASURED);
    }

    /** One server tick of a second's worth, so the chunks in flight grow as they do over time. */
    private void pump(final PregenTask task, final long averageNanos, final OptionalDouble mainThreadWait) {
        this.tick += 20;
        task.pump(this.tick, averageNanos, mainThreadWait, LIMITS);
    }

    private void runToTheEnd(final PregenTask task) {
        for (int pumps = 0; pumps < 100_000 && !task.finished(); pumps++) {
            this.pump(task, CALM_NANOS);
            this.world.completeAll();
        }
        assertTrue(task.finished(), "the task did not finish");
    }

    private void runUntilIndex(final PregenTask task, final long index) {
        for (int pumps = 0; pumps < 100_000 && task.resumeIndex() < index; pumps++) {
            this.pump(task, CALM_NANOS);
            this.world.completeAll();
        }
        assertTrue(task.resumeIndex() >= index, "the task did not get to " + index);
    }
}
