package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - evaluate material rules through a compiled class
public class CompiledMaterialRules extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".compiled-material-rules";
    }

    public static boolean enabled = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Evaluates the material rules of world generation (which block the surface, ore veins and the underground get)
            through a class written once per rule tree instead of vanilla's tree of lambdas. Every condition and rule at the
            leaves is still vanilla's own, called in the same order, so the generated blocks are the same.""",
            """
            通过为每个规则树生成一次的类来计算世界生成的材料规则, 生成的方块与原版相同.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
    }
}
