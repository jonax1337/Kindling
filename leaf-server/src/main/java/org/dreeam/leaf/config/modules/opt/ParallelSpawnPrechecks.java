package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - spawn pre-checks in parallel, commit in order
public class ParallelSpawnPrechecks extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".parallel-spawn-prechecks";
    }

    private static final int MAX_HELPER_THREADS = 16;

    public static boolean enabled = false;
    public static int helperThreads = 2;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Natural spawning first works out, for every chunk at once on `helper-threads` extra threads and the main thread,
            which mob each try would pick and whether its block, light and spawn rules allow it. The main thread then runs
            the tries in vanilla's order and applies everything that depends on what spawned before (mob caps, spawn
            potential, collisions with entities, events) as vanilla does. A try whose world changed in between (a plugin
            placed a block or changed the weather, or a PreCreatureSpawnEvent listener exists) is worked out the
            vanilla way. The random values come from other random generators than vanilla's unseeded one, so the spawns
            follow the same distribution, not the same sequence. Stays off while Leaf's optimize-mob-spawning,
            cache-biome.mob-spawning or parallel world ticking is on. Helps while players explore and the mob caps are not
            full, and only with idle cores. A changed helper-threads takes effect after a restart.""",
            """
            在多个线程上预先计算自然生成的检查, 主线程按原版顺序提交结果.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        helperThreads = Math.clamp(globalConfig.getInt(basePath() + ".helper-threads", helperThreads), 1, MAX_HELPER_THREADS);
    }
}
