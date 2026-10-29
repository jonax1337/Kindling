package net.kindlingmc.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;

/** Which mobs of a category may spawn at a position: a structure's own list where one applies, else the biome's. */
interface MobLists {

    WeightedList<MobSpawnSettings.SpawnerData> at(MobCategory category, BlockPos pos, LevelChunk chunk);
}
