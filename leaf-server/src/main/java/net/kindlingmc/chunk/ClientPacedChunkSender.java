package net.kindlingmc.chunk;

import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.dreeam.leaf.config.modules.opt.ClientPacedChunkSending;

/**
 * While {@code performance.client-paced-chunk-sending} is on, wraps the chunks Moonrise sends a player in one tick into
 * one vanilla chunk batch and limits them by {@link ClientChunkPacer}. Off, or for a client that is not paced, it adds
 * no packet and no limit. One per connection, like vanilla's chunk sender, so the pacing survives world changes and
 * respawns and starts over after a reconfiguration.
 */
public final class ClientPacedChunkSender {

    private static final long UNLIMITED = Long.MAX_VALUE;

    private final ClientChunkPacer pacer = new ClientChunkPacer();
    private final ServerGamePacketListenerImpl connection;
    private boolean pacedTick;
    private long tick;
    private int batchSize;

    public ClientPacedChunkSender(final ServerGamePacketListenerImpl connection) {
        this.connection = connection;
    }

    /**
     * Starts this tick's sends and returns how many chunks the client may get in it.
     */
    public long takeAllowance(final long tick) {
        this.tick = tick;
        this.pacedTick = ClientPacedChunkSending.enabled && this.pacer.isPacing(tick);
        if (!this.pacedTick) {
            return UNLIMITED;
        }
        this.pacer.refillQuota(ClientPacedChunkSending.maxChunksPerTick);
        return this.pacer.chunksAllowed();
    }

    public void beforeChunk() {
        if (!this.pacedTick) {
            return;
        }
        if (this.batchSize == 0) {
            this.connection.send(ClientboundChunkBatchStartPacket.INSTANCE);
        }
        this.batchSize++;
    }

    /**
     * Closes this tick's batch; also needed when the sends of the tick stop early.
     */
    public void finishBatch() {
        if (this.batchSize == 0) {
            return;
        }
        this.connection.send(new ClientboundChunkBatchFinishedPacket(this.batchSize));
        this.pacer.onBatchSent(this.batchSize, this.tick);
        this.batchSize = 0;
    }

    public void onBatchAnswered(final float desiredChunksPerTick) {
        this.pacer.onBatchAnswered(desiredChunksPerTick);
    }
}
