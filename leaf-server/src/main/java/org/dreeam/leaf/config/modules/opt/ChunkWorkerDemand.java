package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - chunk worker count that follows the generation backlog
public class ChunkWorkerDemand extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".worker-demand";
    }

    private static final int MIN_QUIET_SECONDS = 1;
    private static final int MAX_QUIET_SECONDS = 3600;

    public static boolean enabled = true;
    public static int quietSeconds = 10;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Runs every chunk worker thread (chunk-system.worker-threads, or Kindling's default) only while new terrain waits
            for them. Every second the server reads how many generation steps waited for a worker on average; when more than
            16 waited, all workers run, and after `quiet-seconds` in which fewer than 8 waited, the count falls back to Paper's
            own default for this machine (a quarter of the processors). Loading saved chunks does not count, and the few new
            chunks at the view edge of a pregenerated world stay below 16, so such a world runs with Paper's count. This
            changes no gameplay, only how many threads
            generate chunks. Has no effect while `performance.worker-governor` is on.""",
            """
            仅在有新地形等待生成时运行全部区块工作线程, 否则使用 Paper 的默认数量.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        quietSeconds = Math.clamp(globalConfig.getInt(basePath() + ".quiet-seconds", quietSeconds), MIN_QUIET_SECONDS, MAX_QUIET_SECONDS);
    }
}
