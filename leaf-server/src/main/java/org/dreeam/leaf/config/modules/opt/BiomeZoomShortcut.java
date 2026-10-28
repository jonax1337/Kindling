package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - biome zoom shortcut
public class BiomeZoomShortcut extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".biome-zoom-shortcut";
    }

    public static boolean enabled = false;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Answers a block's biome without the zoom's distance math when the eight biome cells it chooses from lie in one
            chunk, in chunk sections that each store only one biome, and that biome is the same. The zoom would pick that
            biome anyway, so the result is the same as without the shortcut. Not yet measured, so off by default.""",
            """
            当方块周围的八个生物群系格子位于同一区块且属于相同的单一生物群系区段时, 跳过生物群系缩放的距离计算.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
