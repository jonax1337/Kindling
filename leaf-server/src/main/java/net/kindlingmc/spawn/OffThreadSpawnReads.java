package net.kindlingmc.spawn;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import org.jspecify.annotations.Nullable;

/**
 * The pick of a spawn try as Leaf makes it ({@code NaturalSpawner.getRandomSpawnMobAtWithChunk}, {@code mobsAtWithChunk},
 * {@code isInNetherFortressBoundsChunk} and {@code ChunkGenerator.getMobsAtChunk}), safe to call while the main thread
 * waits: chunks are only looked up, never loaded ({@link ChunkLoadGuard.ChunkNotPresent} when one is missing), the biome comes without
 * Leaf's biome cache and the spawn settings from a reader captured on the main thread, as these caches are shared.
 * Keep it in step with those four methods when Leaf changes them.
 */
final class OffThreadSpawnReads implements MobLists {

    private final ServerLevel level;
    private final StructureManager structureManager;
    private final EnvironmentAttributeReader spawnSettings;
    private final @Nullable Structure fortress;

    private OffThreadSpawnReads(final ServerLevel level, final EnvironmentAttributeReader spawnSettings, final @Nullable Structure fortress) {
        this.level = level;
        this.structureManager = level.structureManager();
        this.spawnSettings = spawnSettings;
        this.fortress = fortress;
    }

    /**
     * Captures what the pre-checks of this tick read through caches the main thread owns. Main thread only, once per
     * tick before the pre-checks: it also fills this tick's cache of the slime spawn chance, the one environment value
     * a spawn rule reads, so the pre-checks only read it.
     */
    static OffThreadSpawnReads capture(final ServerLevel level) {
        level.environmentAttributes().getValue(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE, BlockPos.ZERO);
        final EnvironmentAttributeReader spawnSettings = level.environmentAttributes().captureMobSpawnSettings(level.getBiomeManager());
        final Structure fortress = level.structureManager().registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.FORTRESS);
        return new OffThreadSpawnReads(level, spawnSettings, fortress);
    }

    Pick pick(final MobCategory category, final RandomSource random, final BlockPos pos, final LevelChunk chunk) {
        if (category == MobCategory.WATER_AMBIENT
            && this.level.getBiome(pos).is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS)
            && random.nextFloat() < 0.98F) {
            return Pick.NONE;
        }
        return Pick.of(this.at(category, pos, chunk).getRandom(random), random);
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> at(final MobCategory category, final BlockPos pos, final LevelChunk chunk) {
        return this.isInNetherFortress(category, pos, chunk)
            ? NetherFortressStructure.FORTRESS_ENEMIES
            : this.generatorMobsAt(category, pos, chunk);
    }

    private boolean isInNetherFortress(final MobCategory category, final BlockPos pos, final LevelChunk chunk) {
        if (category != MobCategory.MONSTER) {
            return false;
        }
        final BlockState below = chunk.coordinateKey == ChunkPos.pack(pos)
            ? chunk.getBlockStateFinal(pos.getX(), pos.getY() - 1, pos.getZ())
            : this.level.getBlockState(pos.below());
        return below.is(Blocks.NETHER_BRICKS) && this.fortress != null && this.isInsideStartOf(this.fortress, pos);
    }

    /** {@code StructureManager.getStructureAt(pos, structure).isValid()}. */
    private boolean isInsideStartOf(final Structure structure, final BlockPos pos) {
        final ChunkAccess references = this.present(
            SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.STRUCTURE_REFERENCES
        );
        final LongIterator keys = references.getReferencesForStructure(structure).iterator();
        while (keys.hasNext()) {
            final long key = keys.nextLong();
            final StructureStart start = this.present(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
            if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Leaf's {@code ChunkGenerator.getMobsAtChunk}. */
    private WeightedList<MobSpawnSettings.SpawnerData> generatorMobsAt(final MobCategory category, final BlockPos pos, final LevelChunk chunk) {
        final Map<Structure, LongSet> structures = ChunkPos.pack(pos) == chunk.coordinateKey
            ? chunk.getAllReferences()
            : this.present(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
        for (final Map.Entry<Structure, LongSet> entry : structures.entrySet()) {
            final StructureSpawnOverride override = entry.getKey().spawnOverrides().get(category);
            if (override != null && this.overrideApplies(entry.getKey(), override, entry.getValue(), pos, chunk)) {
                return override.spawns();
            }
        }
        return this.spawnSettings.getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos).getMobsToSpawn(category);
    }

    private boolean overrideApplies(
        final Structure structure, final StructureSpawnOverride override, final LongSet startKeys, final BlockPos pos, final LevelChunk chunk
    ) {
        final LongIterator keys = startKeys.iterator();
        while (keys.hasNext()) {
            final long key = keys.nextLong();
            final ChunkAccess access = chunk.coordinateKey == key ? chunk : this.present(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.STRUCTURE_STARTS);
            final StructureStart start = access.getStartForStructure(structure);
            if (start != null && start.isValid()
                && (override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE
                    ? this.structureManager.structureHasPieceAt(pos, start)
                    : start.getBoundingBox().isInside(pos))) {
                return true;
            }
        }
        return false;
    }

    private ChunkAccess present(final int chunkX, final int chunkZ, final ChunkStatus status) {
        final ChunkAccess chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, status, false);
        if (chunk == null) {
            throw ChunkLoadGuard.ChunkNotPresent.INSTANCE;
        }
        return chunk;
    }
}
