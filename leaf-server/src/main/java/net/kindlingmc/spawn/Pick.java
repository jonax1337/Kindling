package net.kindlingmc.spawn;

import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jspecify.annotations.Nullable;

/** The mob a spawn group picked and how many steps the group takes with it, or {@link #NONE} when nothing can spawn there. */
record Pick(MobSpawnSettings.@Nullable SpawnerData data, int count) {

    static final Pick NONE = new Pick(null, 0);

    /** Leaf's two steps after the weighted pick: the group size is drawn from the same random right after the pick. */
    static Pick of(final Optional<MobSpawnSettings.SpawnerData> picked, final RandomSource random) {
        if (picked.isEmpty()) {
            return NONE;
        }
        final MobSpawnSettings.SpawnerData data = picked.get();
        return new Pick(data, data.count().sample(random));
    }

    boolean isNone() {
        return this.data == null;
    }
}
