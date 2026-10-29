package net.kindlingmc.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The checks of Leaf's {@code NaturalSpawner.isValidSpawnPostitionForTypeWithChunk} that read neither entities nor
 * spawn state, in Leaf's order: everything except the {@code PreCreatureSpawnEvent} (pre-checks only run without its
 * listeners) and the collision box, the last check, which also looks at entities and draws no random value. Keep it in
 * step with that method when Leaf changes it.
 */
final class SpawnPlacementCheck {

    private SpawnPlacementCheck() {
    }

    static boolean passes(
        final ServerLevel level,
        final MobCategory category,
        final MobLists mobLists,
        final MobSpawnSettings.SpawnerData data,
        final SpawnTry.Step step,
        final LevelChunk chunk,
        final boolean spawnDataSelectedHere,
        final RandomSource random
    ) {
        final EntityType<?> type = data.type();
        final double distance = step.nearestPlayerDistanceSqr();
        return type.getCategory() != MobCategory.MISC
            && (type.canSpawnFarFromPlayer() || !(distance > type.getCategory().getDespawnDistance() * type.getCategory().getDespawnDistance()))
            && type.canSummon()
            && (spawnDataSelectedHere || mobLists.at(category, step.pos(), chunk).contains(data))
            && SpawnPlacements.isSpawnPositionOk(type, level, step.pos())
            && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, step.pos(), random);
    }
}
