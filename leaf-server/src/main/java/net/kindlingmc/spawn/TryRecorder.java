package net.kindlingmc.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

/**
 * Runs a spawn try as if no mob ever spawned and writes down the pick and the placement checks of every step. Runs on
 * a helper thread or on the main thread while the main thread does nothing else. One instance per thread.
 */
final class TryRecorder implements SpawnTry.Steps {

    private final SpawnTry spawnTry = new SpawnTry();
    private final RandomSource positions = new SingleThreadedRandomSource(0L);
    private final RandomSource checks = new SingleThreadedRandomSource(0L);
    private ServerLevel level;
    private OffThreadSpawnReads reads;
    private LevelChunk chunk;
    private MobCategory category;
    private TryRecord record;
    private boolean stopped;

    void record(final ServerLevel level, final OffThreadSpawnReads reads, final LevelChunk chunk, final MobCategory category, final TrySeeds seeds, final TryRecord record) {
        this.level = level;
        this.reads = reads;
        this.chunk = chunk;
        this.category = category;
        this.record = record;
        this.stopped = false;
        this.positions.setSeed(seeds.positions());
        this.checks.setSeed(seeds.checks());
        record.startRecording();
        try {
            this.spawnTry.run(level, chunk, this.positions, this);
        } finally {
            this.forgetTry();
        }
    }

    /** Helper threads live on; they must not keep a level or a chunk alive after the try. */
    private void forgetTry() {
        this.level = null;
        this.reads = null;
        this.chunk = null;
        this.record = null;
    }

    @Override
    public void beginGroup() {
    }

    @Override
    public Pick pick(final SpawnTry.Step step) {
        if (this.stopped) {
            return Pick.NONE;
        }
        try {
            final Pick pick = this.reads.pick(this.category, this.checks, step.pos(), this.chunk);
            this.record.putPick(step, pick);
            return pick;
        } catch (final ChunkLoadGuard.ChunkNotPresent e) {
            this.stopped = true;
            return Pick.NONE;
        }
    }

    @Override
    public SpawnTry.Outcome attempt(final SpawnTry.Step step, final MobSpawnSettings.SpawnerData data, final boolean spawnDataSelectedHere) {
        try {
            final boolean passed = SpawnPlacementCheck.passes(this.level, this.category, this.reads, data, step, this.chunk, spawnDataSelectedHere, this.checks);
            this.record.putVerdict(step, passed);
            return SpawnTry.Outcome.CONTINUE;
        } catch (final ChunkLoadGuard.ChunkNotPresent e) {
            this.stopped = true;
            return SpawnTry.Outcome.END_TRY;
        }
    }
}
