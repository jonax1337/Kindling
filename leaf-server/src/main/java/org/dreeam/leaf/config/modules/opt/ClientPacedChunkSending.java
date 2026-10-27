package org.dreeam.leaf.config.modules.opt;

import net.kindlingmc.chunk.ClientChunkPacer;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.ConfigModule;

// Kindling - optional client-paced chunk sending
public class ClientPacedChunkSending extends ConfigModule {
    public String basePath() {
        return ConfigCategory.PERF.basePath() + ".client-paced-chunk-sending";
    }

    public static boolean enabled = false;
    public static int maxChunksPerTick = 32;

    @Override
    public void onLoaded() {
        globalConfig.addCommentRegionBased(basePath(), """
            Sends the chunks a player gets in one tick as one vanilla chunk batch and paces them by the client's answers, as
            vanilla does: the client reports after each batch how many chunks per tick it can take, and the server waits while
            ten batches are unanswered (one until the client first answers). A slow client then gets fewer chunks instead of a send buffer that grows in front of its
            other packets; a fast client gets more, up to `max-chunks-per-tick` (1 to 64, vanilla allows 64) and Paper's
            `player-max-chunk-send-rate` (if above 0). A client that does not answer its first batch within 3 seconds gets the
            fixed rate until it answers. Which chunks are sent does not change, only when.""",
            """
            按客户端对区块批次的反馈调节区块发送速度.""");
        enabled = globalConfig.getBoolean(basePath() + ".enabled", enabled);
        maxChunksPerTick = Math.clamp(globalConfig.getInt(basePath() + ".max-chunks-per-tick", maxChunksPerTick),
            1, (int) ClientChunkPacer.MAX_CHUNKS_PER_TICK);
    }
}
