package org.dreeam.leaf.config.modules.opt;

import net.kindlingmc.doctor.ByteSize;
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
    public static int minFreeDiskGb = 2;
    public static double maxMainThreadWait = 0.10; // Kindling - pregeneration throttle by main-thread CPU wait
    public static boolean automatic = false;
    public static int overworldRadius = 5000;
    public static int netherRadius = 1000;
    public static int endRadius = 3000;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Generates chunks ahead of time around a centre, nearest first, at the chunk system's lowest priority, so the
            chunks players need always come first; `/pregen` starts, pauses and shows runs, which survive restarts.
            While the average tick takes longer than `max-mspt`, a run halves its chunks in flight and starts none (`/pregen status`
            says "throttled"); it adds one a second while the average stays below three quarters of it, up to `max-parallel`.
            On Linux with the doctor's `host-monitor`, a run throttles the same way while the main thread waited for a CPU more
            than `max-main-thread-wait` of the last minute (0.10 is a tenth of the time it had work; 0 turns this off).
            A run stops by itself, and says so in `/pregen status`, when finishing it would leave less than `min-free-disk-gb`
            free on the world's disk (judged by the space each chunk took so far); it continues once there is room again.
            While a run is working the server does not pause for lack of players (`pause-when-empty-seconds`).
            `automatic` starts the given radii (in blocks, around the spawn, 0 for none) on every world by itself.""",
            """
            以最低优先级预生成区块; 使用 /pregen 管理.""");
        maxMspt = Math.max(1.0, globalConfig.getDouble(basePath() + ".max-mspt", maxMspt));
        maxParallel = Math.max(1, globalConfig.getInt(basePath() + ".max-parallel", maxParallel));
        minFreeDiskGb = Math.max(0, globalConfig.getInt(basePath() + ".min-free-disk-gb", minFreeDiskGb));
        maxMainThreadWait = Math.clamp(globalConfig.getDouble(basePath() + ".max-main-thread-wait", maxMainThreadWait), 0.0, 1.0); // Kindling - pregeneration throttle by main-thread CPU wait
        automatic = globalConfig.getBoolean(basePath() + ".automatic.enabled", automatic);
        overworldRadius = Math.max(0, globalConfig.getInt(basePath() + ".automatic.overworld-radius", overworldRadius));
        netherRadius = Math.max(0, globalConfig.getInt(basePath() + ".automatic.nether-radius", netherRadius));
        endRadius = Math.max(0, globalConfig.getInt(basePath() + ".automatic.end-radius", endRadius));
    }

    /** The space a run leaves free on the disk of its world. */
    public static long minFreeDiskBytes() {
        return minFreeDiskGb * ByteSize.GIB;
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
