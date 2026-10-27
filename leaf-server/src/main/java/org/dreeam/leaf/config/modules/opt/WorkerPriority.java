package org.dreeam.leaf.config.modules.opt;

import net.kindlingmc.os.LowerPriority;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - optional lower OS priority for chunk workers
public class WorkerPriority extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".worker-priority";
    }

    public static boolean enabled = false;
    public static int nice = 10;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Lets the chunk system's worker and I/O threads give way to the main thread when the CPUs are busy, as Paper intends
            with Java thread priorities. Linux ignores those, so with `enabled` each of these threads gives itself the nice value
            `nice` (0 to 19, higher is lower priority) when it starts; elsewhere it lowers its Java thread priority. A priority is
            never raised. Threads these workers start inherit the lower priority. Applies to threads started after the setting
            is read, so restart the server after changing it; `/kindling doctor` shows whether it took effect.""",
            """
            降低区块工作线程和 I/O 线程的操作系统优先级 (Linux 上为 nice 值).""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        nice = Math.clamp(globalConfig.getInt(basePath() + ".nice", nice), 0, LowerPriority.MAX_NICE);
    }
}
