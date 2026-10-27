package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - /kindling doctor
public class StartupDoctor extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".doctor";
    }

    public static boolean startupCheck = true;
    public static boolean hostMonitor = true; // Kindling - live host diagnostics

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            `/kindling doctor` lists the settings of this machine that cost performance or memory, each with a fix.
            With `startup-check` the server logs the warnings of that report once it has started.
            With `host-monitor` (Linux only) a background thread samples every 5 seconds how long the main thread waits for a CPU,
            how much CPU time the host takes (steal) and whether a container limit throttles the server, so the report can tell
            whether lag comes from this server or from the machine under it.""",
            """
            /kindling doctor 列出本机上影响性能或内存的设置.
            host-monitor (仅 Linux) 每 5 秒采样主线程等待 CPU 的时间, 宿主机偷取的 CPU 时间和容器 CPU 限制.""");
        startupCheck = globalConfig.getBoolean(basePath() + ".startup-check", startupCheck);
        hostMonitor = globalConfig.getBoolean(basePath() + ".host-monitor", hostMonitor); // Kindling - live host diagnostics
    }
}
