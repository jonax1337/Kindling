package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - /kindling doctor
public class StartupDoctor extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".doctor";
    }

    public static boolean startupCheck = true;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            `/kindling doctor` lists the settings of this machine that cost performance or memory, each with a fix.
            With `startup-check` the server logs the warnings of that report once it has started.""",
            """
            /kindling doctor 列出本机上影响性能或内存的设置.""");
        startupCheck = globalConfig.getBoolean(basePath() + ".startup-check", startupCheck);
    }
}
