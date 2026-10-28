package org.dreeam.leaf.config.modules.async;

import org.dreeam.leaf.async.path.PathfindTaskRejectPolicy;
import org.dreeam.leaf.config.ConfigModule;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.LeafConfig;

public class AsyncPathfinding extends ConfigModule {

    public String basePath() {
        return ConfigCategory.ASYNC.basePath() + ".async-pathfinding";
    }

    public static boolean enabled = false; // Kindling - vanilla default: with async pathfinding a mob follows a new path a tick or more late
    public static int asyncPathfindingMaxThreads = 0;
    public static int asyncPathfindingKeepalive = 60;
    public static int asyncPathfindingQueueSize = 0;
    public static PathfindTaskRejectPolicy asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.FLUSH_ALL;
    private static boolean asyncPathfindingInitialized;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath() + ".enabled", """
                Whether or not paths should be computed asynchronously.
                Off by default (Kindling): a mob follows a newly computed path a tick or more late,
                so its movement differs slightly from vanilla.""",
            """
                是否异步计算寻路.
                Kindling 默认关闭: 生物会晚一刻或更久才沿着新计算出的路径移动, 与原版略有不同."""
        );
        globalConfig.addCommentRegionBased(basePath() + ".reject-policy", """
                The policy to use when the queue is full and a new task is submitted.
                FLUSH_ALL: All pending tasks will be run on server thread.
                CALLER_RUNS: Newly submitted task will be run on server thread.""",
            """
                当队列满时, 新提交的任务将使用以下策略处理.
                FLUSH_ALL: 所有等待中的任务都将在主线程上运行.
                CALLER_RUNS: 新提交的任务将在主线程上运行."""
        );
        if (asyncPathfindingInitialized) {
            globalConfig.getConfigSection(basePath());
            return;
        }
        asyncPathfindingInitialized = true;

        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        asyncPathfindingMaxThreads = globalConfig.getInt(basePath() + ".max-threads", asyncPathfindingMaxThreads);
        asyncPathfindingKeepalive = globalConfig.getInt(basePath() + ".keepalive", asyncPathfindingKeepalive);
        asyncPathfindingQueueSize = globalConfig.getInt(basePath() + ".queue-size", asyncPathfindingQueueSize);

        if (asyncPathfindingMaxThreads <= 0) {
            asyncPathfindingMaxThreads = Math.max(availableProcessors / 4, 1);
        }

        if (!enabled) {
            asyncPathfindingMaxThreads = 0;
        }

        if (asyncPathfindingQueueSize <= 0) {
            asyncPathfindingQueueSize = asyncPathfindingMaxThreads * 256;
        }

        asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.fromString(globalConfig.getString(basePath() + ".reject-policy",
            availableProcessors >= 12 && asyncPathfindingQueueSize < 512
                ? PathfindTaskRejectPolicy.FLUSH_ALL.toString()
                : PathfindTaskRejectPolicy.CALLER_RUNS.toString())
        );

        if (enabled) {
            LeafConfig.LOGGER.info("Using {} threads for Async Pathfinding", asyncPathfindingMaxThreads);
            org.dreeam.leaf.async.path.AsyncPathProcessor.init();
        }
    }
}
