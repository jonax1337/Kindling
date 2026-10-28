package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - memory return while nobody is online
public class IdleMemory extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".idle-memory";
    }

    public static boolean enabled = false;
    public static int afterSeconds = 300;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Gives memory back to the system while nobody is online. After `after-seconds` without players, once per idle
            period, the server runs a full garbage collection (which lets the heap shrink down to its live data) and, on
            glibc Linux, trims the native heap; it logs the resident memory before and after. Nothing runs while players
            are online or a pregeneration is working, and the period starts over when either begins. A heap with `-Xms`
            equal to `-Xmx` cannot shrink: use a lower `-Xms` (for example `-Xms512M -Xmx2G`) to get heap back. The
            collection pauses the server for a moment; with nobody online nobody notices. Read at start-up: a change takes
            effect after a restart.""",
            """
            无玩家在线一段时间后执行一次完整 GC 并整理本机堆, 把内存还给系统.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        afterSeconds = Math.max(1, globalConfig.getInt(basePath() + ".after-seconds", afterSeconds));
    }
}
