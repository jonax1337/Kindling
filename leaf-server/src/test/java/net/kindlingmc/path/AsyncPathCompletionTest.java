package net.kindlingmc.path;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import org.dreeam.leaf.async.path.AsyncPath;
import org.dreeam.leaf.async.path.AsyncPathProcessor;
import org.dreeam.leaf.config.modules.async.AsyncPathfinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPathCompletionTest {

    private static final BlockPos BED = new BlockPos(1, 64, 1);
    private static final BlockPos OTHER_BED = new BlockPos(9, 64, 9);
    private static final long FINISH_SECONDS = 10L;

    private final ThreadPoolExecutor previousExecutor = AsyncPathProcessor.PATH_PROCESSING_EXECUTOR;
    private final int previousMaxThreads = AsyncPathfinding.asyncPathfindingMaxThreads;
    private final int previousQueueSize = AsyncPathfinding.asyncPathfindingQueueSize;
    private final PathFinder finder = new PathFinder(new UnusedRules(), 1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch workerFailed = new CountDownLatch(1);
    private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();

    @AfterEach
    void restoreTheProcessor() {
        this.release.countDown();
        AsyncPathProcessor.PATH_PROCESSING_EXECUTOR.shutdownNow();
        AsyncPathProcessor.PATH_PROCESSING_EXECUTOR = this.previousExecutor;
        AsyncPathfinding.asyncPathfindingMaxThreads = this.previousMaxThreads;
        AsyncPathfinding.asyncPathfindingQueueSize = this.previousQueueSize;
    }

    @Test
    void aComputationThatThrowsAnErrorStillCompletesWithoutAPath() throws InterruptedException {
        this.useTestWorker();

        AsyncPath path = this.queue(Set.of(BED), finder -> {
            throw new StackOverflowError("pathfinding ran too deep");
        });

        assertTrue(this.workerFailed.await(FINISH_SECONDS, TimeUnit.SECONDS));
        assertTrue(path.isProcessed());
        assertFalse(path.canReach());
        assertTrue(path.isDone());
        assertEquals(BED, path.getTarget());
        assertInstanceOf(StackOverflowError.class, this.workerFailure.get());
    }

    @Test
    void aPathQueuedAfterTheProcessorShutDownIsComputedOnTheCallingThread() {
        this.useLeafProcessor();
        AsyncPathProcessor.PATH_PROCESSING_EXECUTOR.shutdown();
        AtomicReference<Thread> computedOn = new AtomicReference<>();

        AsyncPath path = this.queue(Set.of(BED), finder -> {
            computedOn.set(Thread.currentThread());
            return pathTo(BED);
        });

        assertTrue(path.isProcessed());
        assertSame(Thread.currentThread(), computedOn.get());
    }

    @Test
    void aPathTowardsOnePositionKnowsItsTargetBeforeItIsProcessed() {
        this.useTestWorker();

        AsyncPath path = this.queue(Set.of(BED), this.blockedComputation(BED));

        assertFalse(path.isProcessed());
        assertEquals(BED, path.knownTarget());
    }

    @Test
    void aPathTowardsSeveralPositionsKnowsItsTargetOnlyOnceProcessed() {
        this.useTestWorker();

        AsyncPath path = this.queue(Set.of(BED, OTHER_BED), this.blockedComputation(OTHER_BED));

        assertNull(path.knownTarget());
        this.release.countDown();
        path.process();
        assertEquals(OTHER_BED, path.knownTarget());
    }

    private AsyncPath queue(final Set<BlockPos> positions, final Function<PathFinder, Path> computation) {
        return new AsyncPath(this.finder, new ArrayList<>(), positions, computation);
    }

    private Function<PathFinder, Path> blockedComputation(final BlockPos target) {
        return finder -> {
            try {
                this.release.await();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return pathTo(target);
        };
    }

    private static Path pathTo(final BlockPos target) {
        return new Path(List.of(), target, true);
    }

    private void useTestWorker() {
        AsyncPathProcessor.PATH_PROCESSING_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread worker = new Thread(runnable, "AsyncPathCompletionTest worker");
                worker.setUncaughtExceptionHandler((thread, failure) -> {
                    this.workerFailure.set(failure);
                    this.workerFailed.countDown();
                });
                return worker;
            }
        );
    }

    private void useLeafProcessor() {
        AsyncPathfinding.asyncPathfindingMaxThreads = 1;
        AsyncPathfinding.asyncPathfindingQueueSize = 1;
        AsyncPathProcessor.PATH_PROCESSING_EXECUTOR = null;
        AsyncPathProcessor.init();
    }

    private static final class UnusedRules extends NodeEvaluator {

        @Override
        public Node getStart() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Target getTarget(final double x, final double y, final double z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNeighbors(final Node[] neighbors, final Node pos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PathType getPathTypeOfMob(final PathfindingContext context, final int x, final int y, final int z, final Mob mob) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
            throw new UnsupportedOperationException();
        }
    }
}
