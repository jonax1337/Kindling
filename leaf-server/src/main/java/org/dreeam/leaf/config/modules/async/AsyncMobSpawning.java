package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class AsyncMobSpawning extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-mob-spawning";
    }

    public static boolean enabled = false; // Kindling - vanilla default: mob counts are taken on the main thread, so they are never stale and no spawn tick is skipped
    private static boolean asyncMobSpawningInitialized;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
                Whether or not asynchronous mob spawning should be enabled.
                On servers with many entities, this can improve performance by up to 15%. You must have
                paper's per-player-mob-spawns setting set to true for this to work.
                One quick note - this does not actually spawn mobs async (that would be very unsafe).
                This just offloads some expensive calculations that are required for mob spawning.
                Off by default (Kindling): the mob counts can be stale, and a spawn tick is skipped when the
                off-thread count is not ready yet, so spawning differs from vanilla.""",
            """
                是否异步化生物生成.
                在实体较多的服务器上, 异步生成可最高带来 15% 的性能提升.
                须在Paper配置文件中打开 per-player-mob-spawns 才能生效.
                Kindling 默认关闭: 生物数量可能滞后, 异步计数未就绪时会跳过该刻的生成, 与原版不同.""");

        // This prevents us from changing the value during a reload.
        if (asyncMobSpawningInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        asyncMobSpawningInitialized = true;

        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
