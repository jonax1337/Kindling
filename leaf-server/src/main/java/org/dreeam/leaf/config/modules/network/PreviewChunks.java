package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - preview chunks beyond the view distance
public class PreviewChunks extends ConfigModule {
    private static final int MAX_CLIENT_RADIUS = 32;

    public String basePath() {
        return ConfigCategory.NETWORK.basePath() + ".preview-chunks";
    }

    public static boolean enabled = false;
    public static int radius = 32;
    public static int threads = 2;
    public static int maxPendingPerPlayer = 256;
    public static int sendPerTick = 40;
    public static boolean keepRealChunks = true;
    public static boolean fillGaps = true;
    public static boolean trees = true;
    public static int cacheSize = 2048;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Shows players terrain up to `radius` chunks, beyond the view distance, without loading or saving those chunks:
            the server sends previews computed from the world seed, the terrain around the surface only (no structures,
            ores or carved caves; with `trees`, vanilla's own vegetation placed by the world seed: trees, grass and
            flowers, exact inside a chunk where the chunk alone decides it but missing what neighbouring chunks spill
            in, with approximate trees where that cannot be set up), and the real chunk replaces a preview when the
            player comes within the view distance. With `fill-gaps`, chunks within the view distance that the server has not loaded yet also get a preview until
            the real one arrives, except the closest two chunks around the player. Real chunks a player leaves stay on
            the client as their own preview while within `radius` (`keep-real-chunks`); plugins still see the usual
            chunk unload event for them, and they get no block updates. Players see the previews beyond the view
            distance only with a render distance above it. Overworld-like worlds only. Costs CPU on `threads`
            background threads; the world itself, its saves and gameplay do not change.""",
            """
            在视距之外向玩家发送根据种子计算的地形预览区块 (仅地表附近, 树木与植被按种子放置 (邻近区块溢出的部分缺失), 无结构), 不加载也不保存这些区块.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        radius = Math.clamp(globalConfig.getInt(basePath() + ".radius", radius), 2, MAX_CLIENT_RADIUS);
        threads = Math.max(1, globalConfig.getInt(basePath() + ".threads", threads));
        maxPendingPerPlayer = Math.max(1, globalConfig.getInt(basePath() + ".max-pending-per-player", maxPendingPerPlayer));
        sendPerTick = Math.max(1, globalConfig.getInt(basePath() + ".send-per-tick", sendPerTick));
        keepRealChunks = globalConfig.getBoolean(basePath() + ".keep-real-chunks", keepRealChunks);
        fillGaps = globalConfig.getBoolean(basePath() + ".fill-gaps", fillGaps);
        trees = globalConfig.getBoolean(basePath() + ".trees", trees);
        cacheSize = Math.max(0, globalConfig.getInt(basePath() + ".cache-size", cacheSize));
    }
}
