package net.kindlingmc.spawn;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jspecify.annotations.Nullable;

/**
 * Runs a spawn try on the main thread the way Leaf's {@code NaturalSpawner.spawnCategoryForPosition} commits it. The
 * pick and the placement checks of a step come from the try's record while the record still describes the world;
 * from the first step it does not, the rest of the try computes them with Leaf's own methods and the level's random.
 * The collision box, the spawn potential, the mob's own checks, finalizeSpawn, the events and the counts always run
 * here, in vanilla's order. Main thread only.
 */
final class TryReplay implements SpawnTry.Steps {

    private final SpawnTry spawnTry = new SpawnTry();
    private final RandomSource positions = new SingleThreadedRandomSource(0L);
    private final RandomSource verificationChecks = new SingleThreadedRandomSource(0L);
    private final SpawnPrecheckRound round;
    private ServerLevel level;
    private LeafMobLists leafMobLists;
    private TryRecord record;
    private MobCategory category;
    private LevelChunk chunk;
    private NaturalSpawner.SpawnPredicate canSpawn;
    private NaturalSpawner.AfterSpawnCallback afterSpawn;
    private int maxSpawns;
    private @Nullable Consumer<Entity> trackEntity;
    private boolean live;
    private int clusterSize;
    private int groupSize;
    private @Nullable SpawnGroupData groupData;

    TryReplay(final SpawnPrecheckRound round) {
        this.round = round;
    }

    /** Returns the number of mobs spawned, as Leaf's spawnCategoryForChunk does. */
    int replay(
        final ServerLevel level,
        final TryRecord record,
        final MobCategory category,
        final LevelChunk chunk,
        final TrySeeds seeds,
        final NaturalSpawner.SpawnPredicate canSpawn,
        final NaturalSpawner.AfterSpawnCallback afterSpawn,
        final int maxSpawns,
        final @Nullable Consumer<Entity> trackEntity
    ) {
        if (this.level != level) {
            this.level = level;
            this.leafMobLists = new LeafMobLists(level);
        }
        this.record = record;
        this.category = category;
        this.chunk = chunk;
        this.canSpawn = canSpawn;
        this.afterSpawn = afterSpawn;
        this.maxSpawns = maxSpawns;
        this.trackEntity = trackEntity;
        this.live = false;
        this.clusterSize = 0;
        this.positions.setSeed(seeds.positions());
        this.verificationChecks.setSeed(seeds.checks());
        level.environmentAttributes().clearNaturalMobSpawnsMemo();
        this.spawnTry.run(level, chunk, this.positions, this);
        PrecheckVerifier.countReplay(this.live);
        return this.clusterSize;
    }

    @Override
    public void beginGroup() {
        this.groupData = null;
        this.groupSize = 0;
    }

    @Override
    public Pick pick(final SpawnTry.Step step) {
        if (this.recordHolds() && this.record.hasPickFor(step)) {
            final Pick recorded = this.record.pick(step.attempt());
            if (PrecheckVerifier.ENABLED) {
                PrecheckVerifier.comparePick(recorded, this.leafPick(step, this.verificationChecks), step);
            }
            return recorded;
        }
        this.live = true;
        return this.leafPick(step, this.level.getRandom());
    }

    @Override
    public SpawnTry.Outcome attempt(final SpawnTry.Step step, final MobSpawnSettings.SpawnerData data, final boolean spawnDataSelectedHere) {
        final NaturalSpawner.PreSpawnStatus status = this.placement(step, data, spawnDataSelectedHere);
        if (status == NaturalSpawner.PreSpawnStatus.ABORT || status == NaturalSpawner.PreSpawnStatus.CANCELLED) {
            this.level.getChunkSource().chunkMap.updateFailurePlayerMobTypeMap(step.pos().getX() >> 4, step.pos().getZ() >> 4, this.category);
        }
        if (status == NaturalSpawner.PreSpawnStatus.ABORT) {
            return SpawnTry.Outcome.END_TRY;
        }
        if (status != NaturalSpawner.PreSpawnStatus.SUCCESS || !this.canSpawn.test(data.type(), this.level, step.pos(), this.chunk)) {
            return SpawnTry.Outcome.CONTINUE;
        }
        return this.spawn(step, data);
    }

    private boolean recordHolds() {
        return !this.live && this.round.snapshotHolds();
    }

    private Pick leafPick(final SpawnTry.Step step, final RandomSource random) {
        return Pick.of(
            NaturalSpawner.getRandomSpawnMobAtWithChunk(
                this.level, this.level.structureManager(), this.level.getChunkSource().getGenerator(), this.category, random, step.pos(), this.chunk
            ),
            random
        );
    }

    private NaturalSpawner.PreSpawnStatus placement(final SpawnTry.Step step, final MobSpawnSettings.SpawnerData data, final boolean spawnDataSelectedHere) {
        if (this.recordHolds() && this.record.hasVerdictFor(step)) {
            final boolean passed = this.record.passed(step.attempt());
            if (PrecheckVerifier.ENABLED) {
                PrecheckVerifier.compareVerdict(passed, SpawnPlacementCheck.passes(
                    this.level, this.category, this.leafMobLists, data, step, this.chunk, spawnDataSelectedHere, this.verificationChecks
                ), step);
            }
            return passed && this.level.noCollision(data.type().getSpawnAABB(step.pos().getX() + 0.5, step.pos().getY(), step.pos().getZ() + 0.5))
                ? NaturalSpawner.PreSpawnStatus.SUCCESS
                : NaturalSpawner.PreSpawnStatus.FAIL;
        }
        this.live = true;
        return NaturalSpawner.isValidSpawnPostitionForTypeWithChunk(
            this.level, this.category, this.level.structureManager(), this.level.getChunkSource().getGenerator(), data, step.pos(),
            step.nearestPlayerDistanceSqr(), this.chunk, spawnDataSelectedHere
        );
    }

    /** Leaf's commit of a mob that passed every check, unchanged. */
    private SpawnTry.Outcome spawn(final SpawnTry.Step step, final MobSpawnSettings.SpawnerData data) {
        final Mob mob = NaturalSpawner.getMobForSpawn(this.level, data.type());
        if (mob == null) {
            return SpawnTry.Outcome.END_TRY;
        }

        mob.snapTo(step.pos().getX() + 0.5, step.pos().getY(), step.pos().getZ() + 0.5, this.level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!NaturalSpawner.isValidPositionForMob(this.level, mob, step.nearestPlayerDistanceSqr())) {
            return SpawnTry.Outcome.CONTINUE;
        }

        this.groupData = mob.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, this.groupData);
        this.level.addFreshEntityWithPassengers(mob, spawnReason(mob));
        this.level.environmentAttributes().clearNaturalMobSpawnsMemo();
        if (!mob.isRemoved()) {
            this.clusterSize++;
            this.groupSize++;
            this.afterSpawn.run(mob, this.chunk);
            if (this.trackEntity != null) {
                this.trackEntity.accept(mob);
            }
        }
        if (this.clusterSize >= this.maxSpawns || this.clusterSize >= mob.getMaxSpawnClusterSize()) {
            return SpawnTry.Outcome.END_TRY;
        }
        if (mob.isMaxGroupSizeReached(this.groupSize)) {
            this.live = true; // the record went on with this group, so its later steps belong to other positions
            return SpawnTry.Outcome.END_GROUP;
        }
        return SpawnTry.Outcome.CONTINUE;
    }

    /** SPIGOT-7045: ocelot babies keep their special spawn reason. */
    private static CreatureSpawnEvent.SpawnReason spawnReason(final Mob mob) {
        return mob instanceof Ocelot && !((org.bukkit.entity.Ageable) mob.getBukkitEntity()).isAdult()
            ? CreatureSpawnEvent.SpawnReason.OCELOT_BABY
            : CreatureSpawnEvent.SpawnReason.NATURAL;
    }
}
