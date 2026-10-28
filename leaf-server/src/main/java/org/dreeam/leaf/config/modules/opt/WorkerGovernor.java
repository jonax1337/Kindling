package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - shared-host governor for the chunk worker count
public class WorkerGovernor extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".worker-governor";
    }

    private static final int MIN_QUIET_SECONDS = 5;
    private static final int MAX_QUIET_SECONDS = 3600;

    public static boolean enabled = false;
    public static int quietSeconds = 60;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            For servers that share their machine with other servers or programs (Linux only, needs `performance.doctor.host-monitor`).
            After every 5-second sample of the host monitor, the server takes one chunk worker thread away when the main thread
            had to wait for a CPU (more than 10 % of the time it wanted one and at least 20 ms per second) or the container's
            CPU limit stopped the server, two samples in a row; after `quiet-seconds` in which every sample stayed below half
            of those limits (and the host took little CPU time, steal), it adds one back. The count stays between Paper's own
            default for this machine (a quarter of the processors) and chunk-system.worker-threads (or Kindling's default).
            A worker that is added and taken back within `quiet-seconds` doubles the wait before the next try, up to 8 times.
            This changes no gameplay, only how fast new chunks are generated; turning it off gives the workers back.
            `/kindling doctor` shows the current count.""",
            """
            根据主线程等待 CPU 的情况自动调整区块工作线程数量 (仅 Linux).""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        quietSeconds = Math.clamp(globalConfig.getInt(basePath() + ".quiet-seconds", quietSeconds), MIN_QUIET_SECONDS, MAX_QUIET_SECONDS);
    }
}
