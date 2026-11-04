package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - compiled density pruning
public class CompiledDensity extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".compiled-density";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Evaluates the terrain density of world generation (finalDensity) so that a range choice or a lerp computes a
            branch only where vanilla keeps its value, inside the cell corners vanilla interpolates. Every value that is
            kept comes from vanilla's own noise and spline code, so the terrain is the same. Applies to worlds loaded after
            the change.""",
            """
            计算世界生成的地形密度时只在原版保留其值的位置计算分支, 地形与原版相同.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
