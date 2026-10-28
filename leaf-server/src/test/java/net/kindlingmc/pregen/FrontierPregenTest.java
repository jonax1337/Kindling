package net.kindlingmc.pregen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.Set;
import net.kindlingmc.doctor.ByteSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierPregenTest {

    private static final String WORLD = "minecraft:fake";
    private static final PregenLimits LIMITS = new PregenLimits(40_000_000L, 32, 2 * ByteSize.GIB, 0.10);
    private static final Set<String> NO_RUNS = Set.of();
    private static final int NO_MARGIN = 0;
    private static final int TILE_CHUNKS = 256;

    @TempDir
    Path folder;

    private final FakeWorld world = new FakeWorld();
    private long tick;

    @Test
    void aVisitToAWorldThatIsNotWatchedIsIgnored() {
        final FrontierPregen pregen = this.pregen();

        pregen.visit("minecraft:elsewhere", 5, 5, NO_MARGIN);
        this.pumpEmpty(pregen);

        assertFalse(pregen.keepsServerAwake());
        assertTrue(this.world.requests.isEmpty());
    }

    @Test
    void anEmptyServerGeneratesTheTilesPlayersWereIn() {
        final FrontierPregen pregen = this.pregenVisiting(5, 20);

        this.pumpEmpty(pregen);

        assertFalse(this.world.requests.isEmpty());
        this.world.requests.forEach(request -> assertEquals(TileSet.key(0, 1), TileSet.tileOfChunk(request.chunkX, request.chunkZ)));
        assertTrue(pregen.keepsServerAwake());
    }

    @Test
    void theMarginAroundVisitedTilesIsGeneratedNearestFirst() {
        final FrontierPregen pregen = this.pregen();
        pregen.adopt(this.world, 1);
        pregen.visit(WORLD, 0, 0, 1);

        this.runUntilQuiet(pregen);

        assertEquals(9 * TILE_CHUNKS, this.world.requests.size());
        assertEquals(TileSet.key(0, 0), TileSet.tileOfChunk(this.world.requests.get(0).chunkX, this.world.requests.get(0).chunkZ));
        assertFalse(pregen.keepsServerAwake());
    }

    @Test
    void nothingIsGeneratedBeyondTheWorldBorder() {
        this.world.border = new ChunkBorder(0, 15, 0, 15);
        final FrontierPregen pregen = this.pregen();
        pregen.adopt(this.world, 2);
        pregen.visit(WORLD, 0, 0, 2);

        this.runUntilQuiet(pregen);

        assertEquals(TILE_CHUNKS, this.world.requests.size());
    }

    @Test
    void aPlayerJoiningPausesTheWorkAtOnceAndTheEmptyServerResumesIt() {
        final FrontierPregen pregen = this.pregenVisiting(0, 0);
        this.pumpEmpty(pregen);
        final int requested = this.world.requests.size();
        this.world.complete(0, true);
        assertFalse(this.world.tickets.isEmpty());

        pregen.pauseForPlayers();

        assertFalse(pregen.keepsServerAwake());
        assertTrue(this.world.tickets.isEmpty());
        this.world.completeAll();
        assertEquals(requested, this.world.requests.size());

        this.pumpEmpty(pregen);

        assertTrue(this.world.requests.size() > requested);
        assertTrue(pregen.keepsServerAwake());
    }

    @Test
    void anExplicitRunOfTheSameWorldTakesPrecedence() {
        final FrontierPregen pregen = this.pregenVisiting(0, 0);

        this.pumpEmpty(pregen, Set.of(WORLD));

        assertTrue(this.world.requests.isEmpty());
        assertFalse(pregen.keepsServerAwake());
        this.pumpEmpty(pregen);
        assertFalse(this.world.requests.isEmpty());
    }

    @Test
    void anExplicitRunPausesAFrontierTaskThatWasWorkingAlready() {
        final FrontierPregen pregen = this.pregenVisiting(0, 0);
        this.pumpEmpty(pregen);
        final int requested = this.world.requests.size();

        this.pumpEmpty(pregen, Set.of(WORLD));

        assertEquals(requested, this.world.requests.size());
        assertFalse(pregen.keepsServerAwake());
    }

    @Test
    void whatWasLearnedIsSavedAndAFreshStartContinuesWithTheTilesStillOpen() {
        final FrontierPregen pregen = this.pregen();
        pregen.adopt(this.world, 1);
        pregen.visit(WORLD, 0, 0, 1);
        while (this.world.requests.size() < 2 * TILE_CHUNKS) {
            this.pumpEmpty(pregen);
            this.world.completeAll();
        }
        pregen.shutdown();
        final int requestedBefore = this.world.requests.size();

        final FrontierState.Area area = FrontierState.load(this.file()).area(WORLD).orElseThrow();
        assertEquals(42L, area.seed());
        assertTrue(area.visited().contains(TileSet.key(0, 0)));
        assertTrue(area.done().contains(TileSet.key(0, 0)));
        final FrontierPregen restarted = this.pregen();
        restarted.adopt(this.world, 1);
        this.runUntilQuiet(restarted);

        assertEquals((9 - area.done().size()) * TILE_CHUNKS, this.world.requests.size() - requestedBefore);
    }

    @Test
    void newVisitsReachTheDiskWithinTheSaveIntervalWithoutShuttingDown() throws InterruptedException {
        final FrontierPregen pregen = this.pregen();
        pregen.adopt(this.world, NO_MARGIN);
        pregen.visit(WORLD, 33, -1, NO_MARGIN);

        pregen.saveChanges(100);
        for (int waited = 0; waited < 200 && !Files.exists(this.file()); waited++) {
            Thread.sleep(25);
        }

        assertTrue(FrontierState.load(this.file()).area(WORLD).orElseThrow().visited().contains(TileSet.key(2, -1)));
        pregen.shutdown();
    }

    @Test
    void tilesSavedForAnotherSeedAreDroppedWhenTheWorldIsAdopted() throws IOException {
        final FrontierState old = new FrontierState();
        old.put(WORLD, new FrontierState.Area(7L, TileSetTest.tilesOf(0, 0), TileSetTest.tilesOf(0, 0)));
        old.save(this.file());
        final FrontierPregen pregen = this.pregen();

        pregen.adopt(this.world, NO_MARGIN);
        this.pumpEmpty(pregen);

        assertTrue(this.world.requests.isEmpty());
        pregen.shutdown();
        assertTrue(FrontierState.load(this.file()).area(WORLD).isEmpty());
    }

    @Test
    void anUnloadedWorldIsReleasedAndItsTilesAreSaved() {
        final FrontierPregen pregen = this.pregenVisiting(0, 0);
        this.pumpEmpty(pregen);
        this.world.loaded = false;

        this.pumpEmpty(pregen);

        assertFalse(pregen.knows(WORLD));
        assertFalse(pregen.keepsServerAwake());
        pregen.shutdown();
        assertTrue(FrontierState.load(this.file()).area(WORLD).orElseThrow().visited().contains(TileSet.key(0, 0)));
    }

    @Test
    void aFailingWorldStaysOffWithoutBreakingTheTick() {
        final FrontierPregen pregen = this.pregenVisiting(0, 0);
        this.world.loadFailure = new IllegalStateException("the chunk system is down");

        this.pumpEmpty(pregen);
        this.world.loadFailure = null;
        this.pumpEmpty(pregen);

        assertTrue(this.world.requests.isEmpty());
        assertFalse(pregen.keepsServerAwake());
        assertTrue(pregen.status().get(0).endsWith("off after a failure"), pregen.status().toString());
    }

    @Test
    void aFullDiskStopsTheWorkAndFreeSpaceLetsTheServerWakeAgain() {
        this.world.freeBytes = ByteSize.GIB;
        final FrontierPregen pregen = this.pregenVisiting(0, 0);

        this.pumpEmpty(pregen);

        assertFalse(pregen.keepsServerAwake());
        this.world.freeBytes = 100 * ByteSize.GIB;
        for (int ticks = 0; ticks < 600; ticks++) {
            pregen.recheckFullDisk(LIMITS.minFreeDiskBytes());
        }
        assertTrue(pregen.keepsServerAwake());
    }

    @Test
    void theStatusTellsTheTilesAndTheTask() {
        final FrontierPregen pregen = this.pregenVisiting(0, 0);
        this.pumpEmpty(pregen);

        final var lines = pregen.status();

        assertEquals("frontier minecraft:fake: 1 tiles visited, 0 generated", lines.get(0));
        assertTrue(lines.get(1).contains("tiles pending"), lines.toString());
        assertTrue(lines.get(2).contains("disk"), lines.toString());
    }

    private FrontierPregen pregenVisiting(final int chunkX, final int chunkZ) {
        final FrontierPregen pregen = this.pregen();
        pregen.adopt(this.world, NO_MARGIN);
        pregen.visit(WORLD, chunkX, chunkZ, NO_MARGIN);
        return pregen;
    }

    private FrontierPregen pregen() {
        return new FrontierPregen(new FrontierStore(this.file()));
    }

    private Path file() {
        return this.folder.resolve("pregeneration-frontier.properties");
    }

    private void pumpEmpty(final FrontierPregen pregen) {
        this.pumpEmpty(pregen, NO_RUNS);
    }

    /** One server tick of a second's worth, so the chunks in flight grow as they do over time. */
    private void pumpEmpty(final FrontierPregen pregen, final Set<String> worldsWithRun) {
        this.tick += 20;
        pregen.pumpWhileEmpty(new TickLoad(this.tick, 10_000_000L, OptionalDouble.empty()), LIMITS, worldsWithRun);
    }

    private void runUntilQuiet(final FrontierPregen pregen) {
        for (int pumps = 0; pumps < 100_000; pumps++) {
            this.pumpEmpty(pregen);
            this.world.completeAll();
            if (!pregen.keepsServerAwake() && pumps > 2) {
                return;
            }
        }
        throw new AssertionError("the frontier did not run out of tiles");
    }
}
