package net.kindlingmc.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;

/** Leaf's own lookup of the mob lists, for the main thread only (it may load chunks and uses the main thread's memo). */
final class LeafMobLists implements MobLists {

    private final ServerLevel level;

    LeafMobLists(final ServerLevel level) {
        this.level = level;
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> at(final MobCategory category, final BlockPos pos, final LevelChunk chunk) {
        return NaturalSpawner.mobsAtWithChunk(
            this.level, this.level.structureManager(), this.level.getChunkSource().getGenerator(), category, pos, chunk
        );
    }
}
