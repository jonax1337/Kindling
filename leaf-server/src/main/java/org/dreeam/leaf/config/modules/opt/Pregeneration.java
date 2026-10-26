package org.dreeam.leaf.config.modules.opt;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - chunk pregenerator
public class Pregeneration extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".pregeneration";
    }

    public static double maxMspt = 40.0;
    public static int maxParallel = 32;
    public static boolean automatic = false;
    public static int overworldRadius = 5000;
    public static int netherRadius = 1000;
    public static int endRadius = 3000;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Generates chunks ahead of time around a centre, nearest first, at the chunk system's lowest priority, so the
            chunks players need always come first; `/pregen` starts, pauses and shows runs, which survive restarts.
            A run slows down whenever the average tick takes longer than `max-mspt`.
            `automatic` starts the given radii (in blocks, around the spawn, 0 for none) on every world by itself.""",
            """
            以最低优先级预生成区块; 使用 /pregen 管理.""");
        maxMspt = Math.max(1.0, globalConfig.getDouble(basePath() + ".max-mspt", maxMspt));
        maxParallel = Math.max(1, globalConfig.getInt(basePath() + ".max-parallel", maxParallel));
        automatic = globalConfig.getBoolean(basePath() + ".automatic.enabled", automatic);
        overworldRadius = Math.max(0, globalConfig.getInt(basePath() + ".automatic.overworld-radius", overworldRadius));
        netherRadius = Math.max(0, globalConfig.getInt(basePath() + ".automatic.nether-radius", netherRadius));
        endRadius = Math.max(0, globalConfig.getInt(basePath() + ".automatic.end-radius", endRadius));
    }

    /** The automatic radius in blocks for a world, by its dimension; worlds of other dimensions get none. */
    public static int radiusFor(final ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return overworldRadius;
        }
        if (dimension == Level.NETHER) {
            return netherRadius;
        }
        return dimension == Level.END ? endRadius : 0;
    }
}
