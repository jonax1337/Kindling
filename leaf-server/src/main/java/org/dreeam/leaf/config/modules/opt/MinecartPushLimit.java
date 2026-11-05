package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - optional minecart push limit
public class MinecartPushLimit extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".minecart-push-limit";
    }

    public static boolean enabled = false;
    public static int maxPushes = 4;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Lets each minecart push at most max-pushes other minecarts per tick; the others in its reach are not pushed by
            it that tick. Changes gameplay: a crowd of minecarts spreads out more slowly than in vanilla. Meant against
            minecart lag machines, so off by default.""",
            """
            每个矿车每刻最多推动 max-pushes 个其他矿车. 会改变游戏行为, 默认关闭.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        maxPushes = Math.max(1, globalConfig.getInt(basePath() + ".max-pushes", maxPushes));
    }
}
