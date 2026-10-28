package org.dreeam.leaf.config.modules.opt;

import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;

public class SkipMapItemDataUpdates extends ConfigModule {

    public String basePath() {
        return ConfigCategory.PERF.basePath();
    }

    public static boolean enabled = false; // Kindling - vanilla default: maps without the default renderer keep updating their colours

    @Override
    public void onLoaded() {
        enabled = globalConfig.getBoolean(basePath() + ".skip-map-item-data-updates-if-map-does-not-have-craftmaprenderer", enabled);
    }
}
