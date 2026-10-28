package net.kindlingmc.pregen;

import java.nio.file.Path;
import java.util.OptionalDouble;
import net.kindlingmc.pregen.PregenState.Phase;
import net.kindlingmc.pregen.TaskSupervisor.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSupervisorTest {

    private static final String WORLD = "minecraft:fake";
    private static final PregenLimits LIMITS = new PregenLimits(40_000_000L, 32, 0L, 0.10);
    private static final long CALM_NANOS = 10_000_000L;

    @TempDir
    Path folder;

    private final FakeWorld world = new FakeWorld();
    private StateStore store;
    private TaskSupervisor supervisor;
    private long tick;

    @BeforeEach
    void setUp() {
        this.store = new StateStore(this.folder.resolve("pregeneration.properties"));
        this.supervisor = new TaskSupervisor(this.store);
    }

    /** The store writes on a thread of its own; the temporary folder can only be deleted once that thread is done. */
    @AfterEach
    void finishTheWrites() {
        this.store.saveAndClose();
    }

    @Test
    void aFailingRunStaysKnownAsPausedWithItsKeptChunksReleased() {
        final PregenTask task = new PregenTask(this.world, 0, 0, 40, 0L);
        assertEquals(Outcome.CONTINUES, this.pump(task));
        this.world.complete(0, true);
        assertFalse(this.world.tickets.isEmpty());
        this.world.loadFailure = new IllegalStateException("the chunk system is down");

        final Outcome outcome = this.pump(task);

        assertEquals(Outcome.CONTINUES, outcome);
        assertTrue(task.paused());
        assertFalse(task.active());
        assertTrue(this.world.tickets.isEmpty());
        assertEquals(Phase.PAUSED, this.store.state().run(WORLD).orElseThrow().phase());
    }

    @Test
    void aFailedRunContinuesWhenItIsResumed() {
        final PregenTask task = new PregenTask(this.world, 0, 0, 1, 0L);
        this.world.loadFailure = new IllegalStateException("the chunk system is down");
        this.pump(task);
        this.world.loadFailure = null;

        task.resume();
        for (int pumps = 0; pumps < 1000 && !task.finished(); pumps++) {
            assertEquals(Outcome.CONTINUES, this.pump(task));
            this.world.completeAll();
        }

        assertEquals(9, this.world.requests.size());
        assertEquals(Outcome.FINISHED, this.pump(task));
    }

    @Test
    void aFinishedRunIsDroppedAndSavedAsDone() {
        final PregenTask task = new PregenTask(this.world, 0, 0, 0, 0L);
        this.pump(task);
        this.world.completeAll();

        assertEquals(Outcome.FINISHED, this.pump(task));

        assertEquals(Phase.DONE, this.store.state().run(WORLD).orElseThrow().phase());
    }

    @Test
    void aRunWhoseWorldWasUnloadedIsParkedAndSavedWithItsPhase() {
        final PregenTask task = new PregenTask(this.world, 0, 0, 40, 0L);
        this.world.loaded = false;

        assertEquals(Outcome.PARKED, this.pump(task));

        assertEquals(Phase.RUNNING, this.store.state().run(WORLD).orElseThrow().phase());
    }

    private Outcome pump(final PregenTask task) {
        this.tick += 20;
        return this.supervisor.pump(task, this.tick, CALM_NANOS, OptionalDouble.empty(), LIMITS);
    }
}
