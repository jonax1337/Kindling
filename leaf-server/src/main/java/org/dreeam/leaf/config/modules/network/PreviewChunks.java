package org.dreeam.leaf.config.modules.network;

import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - preview chunks beyond the view distance
public class PreviewChunks extends ConfigModule {
    private static final int MAX_CLIENT_RADIUS = 32;
    /** Below this a player would fly into chunks the server has not sent yet. */
    private static final int MIN_TRAVEL_VIEW_DISTANCE = 3;

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
    public static boolean savedChunks = true;
    public static int cacheSize = 2048;
    public static int travelViewDistance = 0;
    public static double travelSpeed = 15.0;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Shows players terrain up to `radius` chunks, beyond the view distance, without loading or saving those chunks:
            the server sends previews computed from the world seed, the terrain around the surface only (no structures,
            ores or carved caves; with `trees`, vanilla's own surface features placed by the world seed: lakes, rocks,
            icebergs, ice spikes, desert wells, trees, grass and
            flowers, exact inside a chunk where the chunk alone decides it but missing what neighbouring chunks spill
            in, with approximate trees where that cannot be set up), and the real chunk replaces a preview when the
            player comes within the view distance. With `saved-chunks`, a chunk that is already saved is previewed as
            saved instead (its trees, structures and builds; each column from a little below its surface up, no
            chests, signs, banners or item frames), read from disk at the lowest I/O priority through the chunk
            system's own read path (which, as for a real load, may repair a region header, quarantine an unreadable
            chunk, or with the linear region format open the region file for writing; the preview code writes
            nothing); changes show after the next save, and worlds with Paper's Anti-Xray keep the seed previews
            (packet-based anti-xray plugins are not detected). With `fill-gaps`, chunks within the view distance that the server has
            not loaded yet also get a preview until the real one arrives while the player is moving, except the closest two chunks around the player. Real chunks a player leaves stay on
            the client as their own preview while within `radius` (`keep-real-chunks`); plugins still see the usual
            chunk unload event for them, and they get no block updates. Players see the previews beyond the view
            distance only with a render distance above it. Overworld-like worlds only. Costs CPU on `threads`
            background threads for players on the move and one more shared by all players standing still, including
            just after joining or a teleport (their rings fill more slowly, the more of them stand the slower); the
            world itself, its saves and gameplay do not change, except with `travel-view-distance`.
            With `travel-view-distance` above 0 (off by default), a player moving horizontally faster than
            `travel-speed` blocks a second for two seconds in a row (an elytra, an ice boat or sprint-flying in
            creative, not a horse) gets at most that view and simulation distance until it has been slower for five
            seconds, and previews show the rest, so that fewer new chunks are generated along a flight. Only where
            previews are shown, and only when the previews reach beyond that distance. This changes what the server
            does around such a player: chunks between the lowered and the usual distance are not loaded, so their
            mobs and blocks do not tick, no mobs spawn there and the player does not see their entities; chunks it
            only flies past are never generated; a player flying faster than the chunks within the lowered distance
            are generated reaches chunks that are only previews; and plugins asking for its view, simulation or send
            distance get the lowered one.""",
            """
            在视距之外向玩家发送根据种子计算的地形预览区块 (仅地表附近, 树木与植被按种子放置 (邻近区块溢出的部分缺失), 无结构), 不加载也不保存这些区块. 启用 `saved-chunks` 时, 已保存的区块改用存档中每列地表略下方以上的内容生成预览 (含其树木, 结构与建筑); 启用 Paper Anti-Xray 的世界仍用种子预览 (不检测基于数据包的反矿透插件). `travel-view-distance` 大于 0 时 (默认关闭), 连续两秒水平速度超过 `travel-speed` 格/秒的玩家, 其视距与模拟距离降至该值 (减速五秒后恢复), 其余由预览显示; 仅在显示预览且预览范围超过该值时生效. 其间的区块不加载也不生成, 其中的生物与方块不被刻处理, 不会刷怪, 玩家也看不到其中的实体; 飞得比区块生成更快的玩家会到达仅有预览的区块; 插件读取的视距, 模拟距离与发送距离为降低后的值.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        radius = Math.clamp(globalConfig.getInt(basePath() + ".radius", radius), 2, MAX_CLIENT_RADIUS);
        threads = Math.max(1, globalConfig.getInt(basePath() + ".threads", threads));
        maxPendingPerPlayer = Math.max(1, globalConfig.getInt(basePath() + ".max-pending-per-player", maxPendingPerPlayer));
        sendPerTick = Math.max(1, globalConfig.getInt(basePath() + ".send-per-tick", sendPerTick));
        keepRealChunks = globalConfig.getBoolean(basePath() + ".keep-real-chunks", keepRealChunks);
        fillGaps = globalConfig.getBoolean(basePath() + ".fill-gaps", fillGaps);
        trees = globalConfig.getBoolean(basePath() + ".trees", trees);
        savedChunks = globalConfig.getBoolean(basePath() + ".saved-chunks", savedChunks);
        cacheSize = Math.max(0, globalConfig.getInt(basePath() + ".cache-size", cacheSize));
        final int travel = globalConfig.getInt(basePath() + ".travel-view-distance", travelViewDistance);
        travelViewDistance = travel <= 0 ? 0 : Math.clamp(travel, MIN_TRAVEL_VIEW_DISTANCE, MAX_CLIENT_RADIUS);
        travelSpeed = Math.max(1.0, globalConfig.getDouble(basePath() + ".travel-speed", travelSpeed));
    }
}
