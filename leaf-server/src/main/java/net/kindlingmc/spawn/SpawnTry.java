package net.kindlingmc.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One natural spawn try of a category in a chunk, step for step as Leaf's {@code NaturalSpawner.spawnCategoryForChunk}
 * and {@code spawnCategoryForPosition} run it: the start position, three groups of steps, and at every step close
 * enough to a player and far enough from it and from the world spawn the pick and the checks. The random values of the
 * start and of the steps come from the random given to {@link #run}; what happens at a step is up to the {@link Steps}.
 * Keep this in step with NaturalSpawner when Leaf changes those methods. Not thread safe: one instance per thread.
 */
final class SpawnTry {

    private static final int GROUPS = 3;

    enum Outcome { CONTINUE, END_GROUP, END_TRY }

    /** What a try does at the steps that reach the pick and the checks. */
    interface Steps {

        void beginGroup();

        /** Picks the mob of the group at its first such step; {@link Pick#NONE} ends the group. */
        Pick pick(Step step);

        Outcome attempt(Step step, MobSpawnSettings.SpawnerData data, boolean spawnDataSelectedHere);
    }

    /** The current step: its number within the try, its group, its position and the squared distance to the nearest player. */
    static final class Step {
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int attempt;
        private int group;
        private double nearestPlayerDistanceSqr;

        int attempt() {
            return this.attempt;
        }

        int group() {
            return this.group;
        }

        BlockPos.MutableBlockPos pos() {
            return this.pos;
        }

        double nearestPlayerDistanceSqr() {
            return this.nearestPlayerDistanceSqr;
        }
    }

    private final ClusterSteps clusterSteps = new ClusterSteps();
    private final Step step = new Step();

    void run(final ServerLevel level, final LevelChunk chunk, final RandomSource positions, final Steps steps) {
        final BlockPos start = randomPosWithin(level, chunk, positions);
        if (start.getY() < level.getMinY() + 1 || !isOpenStart(level, chunk, start)) {
            return;
        }

        this.clusterSteps.start(positions);
        this.step.attempt = 0;
        for (int group = 0; group < GROUPS; group++) {
            if (this.runGroup(level, chunk, start, group, steps) == Outcome.END_TRY) {
                return;
            }
        }
    }

    private Outcome runGroup(final ServerLevel level, final LevelChunk chunk, final BlockPos start, final int group, final Steps steps) {
        final int y = start.getY();
        int x = start.getX();
        int z = start.getZ();
        MobSpawnSettings.SpawnerData data = null;
        steps.beginGroup();
        int max = this.clusterSteps.nextGroupSize();
        for (int i = 0; i < max; i++) {
            this.clusterSteps.nextStep();
            x += this.clusterSteps.stepX();
            z += this.clusterSteps.stepZ();
            if (!this.moveTo(level, chunk, group, x, y, z)) {
                continue;
            }

            final boolean spawnDataSelectedHere = data == null;
            if (spawnDataSelectedHere) {
                final Pick pick = steps.pick(this.step);
                if (pick.isNone()) {
                    return Outcome.END_GROUP;
                }
                data = pick.data();
                max = pick.count();
            }

            final Outcome outcome = steps.attempt(this.step, data, spawnDataSelectedHere);
            this.step.attempt++;
            if (outcome != Outcome.CONTINUE) {
                return outcome;
            }
        }
        return Outcome.CONTINUE;
    }

    /** Sets the step to the position and tells whether Leaf's loop would check it (otherwise it goes on to the next step). */
    private boolean moveTo(final ServerLevel level, final LevelChunk chunk, final int group, final int x, final int y, final int z) {
        final double distance = level.natureSpawnChunkMap.nearest(level, x + 0.5, y, z + 0.5);
        if (distance == Double.POSITIVE_INFINITY) {
            return false;
        }

        final BlockPos.MutableBlockPos pos = this.step.pos.set(x, y, z);
        this.step.group = group;
        this.step.nearestPlayerDistanceSqr = distance;
        return (ChunkPos.pack(pos) == chunk.coordinateKey || level.isLoadedAndInBounds(pos))
            && NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, chunk, pos, distance);
    }

    /** Leaf's {@code NaturalSpawner.getRandomPosWithin}, drawing from the given random. */
    private static BlockPos randomPosWithin(final ServerLevel level, final LevelChunk chunk, final RandomSource random) {
        final ChunkPos chunkPos = chunk.getPos();
        final int x = chunkPos.getMinBlockX() + random.nextInt(16);
        final int z = chunkPos.getMinBlockZ() + random.nextInt(16);
        final int topEmptyY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        final int y = Mth.randomBetweenInclusive(random, level.getMinY(), topEmptyY);
        return new BlockPos(x, y, z);
    }

    private static boolean isOpenStart(final ServerLevel level, final LevelChunk chunk, final BlockPos start) {
        final BlockState state = ChunkPos.pack(start) == chunk.coordinateKey
            ? chunk.getBlockStateFinal(start.getX(), start.getY(), start.getZ())
            : level.getBlockStateIfLoadedAndInBounds(start);
        return state != null && !state.isRedstoneConductor(chunk, start);
    }
}
