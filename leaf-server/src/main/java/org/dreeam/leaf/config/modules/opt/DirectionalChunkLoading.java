package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - load chunks ahead of fast players first
public class DirectionalChunkLoading extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".directional-chunk-loading";
    }

    public static boolean enabled = true;
    public static int lookAheadTicks = 60;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Loads, generates and sends the chunks in a moving player's direction of travel first: a chunk's distance counts
            less ahead of the player and more behind, the more of the view distance the player covers in `look-ahead-ticks`.
            Along any one direction the order stays by distance, and the same chunks load either way.""",
            """
            按玩家移动方向优先加载, 生成和发送区块.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        lookAheadTicks = Math.max(0, globalConfig.getInt(basePath() + ".look-ahead-ticks", lookAheadTicks));
    }
}
