package net.kindlingmc.spawn;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking;
import org.dreeam.leaf.config.modules.opt.OptimizeBiome;
import org.dreeam.leaf.config.modules.opt.OptimizeMobSpawning;
import org.dreeam.leaf.config.modules.opt.ParallelSpawnPrechecks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The natural spawning of one tick of one level with its pre-checks done in parallel ({@code
 * performance.parallel-spawn-prechecks}). Before the chunk loop, {@link #prepare} records every try of every spawning
 * chunk on helper threads and the main thread, while the main thread does nothing else; in the loop, Leaf's
 * spawnForChunk hands each try it would run to {@link #replay} instead, in its own order, as long as {@link #covers}
 * says the record is still good. A record stops being good for the rest of the tick when a block or a biome changed,
 * the sky darkening, the weather, the difficulty or the world border changed, or a {@code PreCreatureSpawnEvent} listener appeared (the
 * time-based environment values are cached per tick, by vanilla as well). Main thread only, apart from the recording.
 */
public final class SpawnPrecheckRound {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FAILURE_LOG_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final ThreadLocal<TryRecorder> RECORDERS = ThreadLocal.withInitial(TryRecorder::new);
    private static final AtomicLong LAST_FAILURE_LOG = new AtomicLong(System.nanoTime() - FAILURE_LOG_INTERVAL_NANOS);
    private static @Nullable AssistedParallelLoop helpers;

    private final IntConsumer recordChunk = this::recordChunk;
    private final Reference2IntOpenHashMap<LevelChunk> chunkIndices = new Reference2IntOpenHashMap<>();
    private final List<MobCategory> categories = new ArrayList<>();
    private final TryReplay replay = new TryReplay(this);
    private ServerLevel level;
    private LevelChunk[] chunks = new LevelChunk[0];
    private int chunkCount;
    private boolean[] categoryHasRoom = new boolean[0];
    private TryRecord[] records = new TryRecord[0];
    private OffThreadSpawnReads reads;
    private long tickSeed;
    private boolean active;
    private @Nullable Boolean generatorSupported;

    private int worldWrites;
    private int skyDarken;
    private float rainLevel;
    private float thunderLevel;
    private Difficulty difficulty;
    private double borderMinX;
    private double borderMaxX;
    private double borderMinZ;
    private double borderMaxZ;

    public SpawnPrecheckRound() {
        this.chunkIndices.defaultReturnValue(-1);
    }

    public void prepare(final ServerLevel level, final ObjectArrayList<LevelChunk> spawningChunks, final List<MobCategory> spawningCategories) {
        this.active = false;
        if (!ParallelSpawnPrechecks.enabled || spawningChunks.isEmpty() || spawningCategories.isEmpty() || !this.isSupported(level)) {
            return;
        }

        Profiler.get().push("kindlingSpawnPrechecks");
        this.level = level;
        this.collectCategories(spawningCategories);
        this.collectChunks(spawningChunks);
        this.reads = OffThreadSpawnReads.capture(level);
        this.tickSeed = level.getRandom().nextLong();
        this.takeSnapshot();
        helpers().run(this.chunkCount, this.recordChunk);
        this.active = true;
        Profiler.get().pop();
    }

    /** Whether {@link #replay} can run this try now. */
    public boolean covers(final LevelChunk chunk, final MobCategory category) {
        return this.active && this.recordedTryIndex(chunk, category) >= 0 && this.snapshotHolds();
    }

    /** Runs the try Leaf's spawnCategoryForChunk would run with these arguments; returns the number of mobs spawned. */
    public int replay(
        final LevelChunk chunk,
        final MobCategory category,
        final NaturalSpawner.SpawnPredicate canSpawn,
        final NaturalSpawner.AfterSpawnCallback afterSpawn,
        final int maxSpawns,
        final @Nullable Consumer<Entity> trackEntity
    ) {
        final int tryIndex = this.recordedTryIndex(chunk, category);
        this.records[tryIndex].markReplayed();
        return this.replay.replay(
            this.level, this.records[tryIndex], category, chunk, TrySeeds.of(this.tickSeed, tryIndex), canSpawn, afterSpawn, maxSpawns, trackEntity
        );
    }

    /** Ends the tick's round; keeps no chunk. */
    public void finish() {
        if (!this.active) {
            return;
        }
        this.active = false;
        Arrays.fill(this.chunks, 0, this.chunkCount, null);
        this.chunkIndices.clear();
        this.chunkCount = 0;
        PrecheckVerifier.reportWhenDue();
    }

    boolean snapshotHolds() {
        final ServerLevel level = this.level;
        return level.kindling$blockAndBiomeWrites == this.worldWrites
            && !hasPreCreatureSpawnListeners()
            && level.getSkyDarken() == this.skyDarken
            && level.getRainLevel(1.0F) == this.rainLevel
            && level.getThunderLevel(1.0F) == this.thunderLevel
            && level.getDifficulty() == this.difficulty
            && level.getWorldBorder().getMinX() == this.borderMinX
            && level.getWorldBorder().getMaxX() == this.borderMaxX
            && level.getWorldBorder().getMinZ() == this.borderMinZ
            && level.getWorldBorder().getMaxZ() == this.borderMaxZ;
    }

    private void takeSnapshot() {
        final ServerLevel level = this.level;
        this.worldWrites = level.kindling$blockAndBiomeWrites;
        this.skyDarken = level.getSkyDarken();
        this.rainLevel = level.getRainLevel(1.0F);
        this.thunderLevel = level.getThunderLevel(1.0F);
        this.difficulty = level.getDifficulty();
        this.borderMinX = level.getWorldBorder().getMinX();
        this.borderMaxX = level.getWorldBorder().getMaxX();
        this.borderMinZ = level.getWorldBorder().getMinZ();
        this.borderMaxZ = level.getWorldBorder().getMaxZ();
    }

    /**
     * Off where the pre-checks could not see what Leaf sees: Leaf's nearest-player tree shares its search stack between
     * callers, Leaf's biome cache answers per biome cell (so the cached biome depends on which block asked first), and
     * with parallel world ticking other levels' threads change the player lists the pre-checks read.
     */
    private boolean isSupported(final ServerLevel level) {
        if (OptimizeMobSpawning.enabled || OptimizeBiome.mobSpawn || SparklyPaperParallelWorldTicking.enabled || hasPreCreatureSpawnListeners()) {
            return false;
        }
        if (this.generatorSupported == null) {
            this.generatorSupported = usesLeafsMobLookup(level.getChunkSource().getGenerator());
        }
        return this.generatorSupported;
    }

    /** The pre-checks copy {@code ChunkGenerator.getMobsAtChunk}; a generator with a lookup of its own spawns the vanilla way. */
    private static boolean usesLeafsMobLookup(final ChunkGenerator generator) {
        try {
            return generator.getClass().getMethod(
                "getMobsAtChunk", Level.class, StructureManager.class, MobCategory.class, BlockPos.class, ChunkAccess.class
            ).getDeclaringClass() == ChunkGenerator.class;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean hasPreCreatureSpawnListeners() {
        return PreCreatureSpawnEvent.getHandlerList().getRegisteredListeners().length != 0;
    }

    private void collectCategories(final List<MobCategory> spawningCategories) {
        this.categories.clear();
        this.categories.addAll(spawningCategories);
        if (this.categoryHasRoom.length < this.categories.size()) {
            this.categoryHasRoom = new boolean[this.categories.size()];
        }
        for (int i = 0; i < this.categories.size(); i++) {
            this.categoryHasRoom[i] = this.hasRoomFor(this.categories.get(i));
        }
    }

    /**
     * Whether any try of the category can run this tick: with per-player mob caps, a category whose cap every player has
     * filled stays full for the whole tick (spawns only add to the counts), so recording its tries would be wasted.
     */
    private boolean hasRoomFor(final MobCategory category) {
        if (!this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
            return true;
        }
        int limit = category.getMaxInstancesPerChunk();
        final SpawnCategory spawnCategory = CraftSpawnCategory.toBukkit(category);
        if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
            limit = this.level.getWorld().getSpawnLimit(spawnCategory);
        }
        for (final ServerPlayer player : this.level.players()) {
            if (this.level.getChunkSource().chunkMap.getMobCountNear(player, category) < limit) {
                return true;
            }
        }
        return false;
    }

    private void collectChunks(final ObjectArrayList<LevelChunk> spawningChunks) {
        this.chunkCount = spawningChunks.size();
        if (this.chunks.length < this.chunkCount) {
            this.chunks = new LevelChunk[this.chunkCount];
        }
        final Object[] raw = spawningChunks.elements();
        this.chunkIndices.clear();
        for (int i = 0; i < this.chunkCount; i++) {
            this.chunks[i] = (LevelChunk) raw[i];
            this.chunkIndices.put(this.chunks[i], i);
        }

        final int tryCount = this.chunkCount * this.categories.size();
        if (this.records.length < tryCount) {
            final int oldLength = this.records.length;
            this.records = Arrays.copyOf(this.records, tryCount);
            for (int i = oldLength; i < tryCount; i++) {
                this.records[i] = new TryRecord();
            }
        }
        for (int i = 0; i < tryCount; i++) {
            this.records[i].forget();
        }
    }

    private void recordChunk(final int chunkIndex) {
        final LevelChunk chunk = this.chunks[chunkIndex];
        if (!this.level.getWorldBorder().isWithinBounds(chunk.getPos())) {
            return;
        }
        final TryRecorder recorder = RECORDERS.get();
        final int firstTry = chunkIndex * this.categories.size();
        ChunkLoadGuard.startRecording();
        try {
            for (int i = 0; i < this.categories.size(); i++) {
                if (this.categoryHasRoom[i]) {
                    recorder.record(this.level, this.reads, chunk, this.categories.get(i), TrySeeds.of(this.tickSeed, firstTry + i), this.records[firstTry + i]);
                }
            }
        } catch (final Throwable t) {
            for (int i = 0; i < this.categories.size(); i++) {
                this.records[firstTry + i].forget();
            }
            logFailure(t);
        } finally {
            ChunkLoadGuard.stopRecording();
        }
    }

    /** The index of the try's record, or -1 when this tick did not record it. */
    private int recordedTryIndex(final LevelChunk chunk, final MobCategory category) {
        final int chunkIndex = this.chunkIndices.getInt(chunk);
        final int categoryIndex = this.categories.indexOf(category);
        if (chunkIndex < 0 || categoryIndex < 0) {
            return -1;
        }
        final int tryIndex = chunkIndex * this.categories.size() + categoryIndex;
        return this.records[tryIndex].isRecorded() ? tryIndex : -1;
    }

    private static void logFailure(final Throwable t) {
        final long now = System.nanoTime();
        final long last = LAST_FAILURE_LOG.get();
        if (now - last >= FAILURE_LOG_INTERVAL_NANOS && LAST_FAILURE_LOG.compareAndSet(last, now)) {
            LOGGER.warn("A spawn pre-check failed; that chunk spawns the vanilla way this tick", t);
        }
    }

    private static synchronized AssistedParallelLoop helpers() {
        if (helpers == null) {
            helpers = new AssistedParallelLoop(ParallelSpawnPrechecks.helperThreads, "Kindling Spawn Pre-check");
        }
        return helpers;
    }
}
