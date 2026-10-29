package net.kindlingmc.stress;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The connection of a fake player: it drops every packet the server sends and answers only what a client must answer
 * so the server neither kicks the player nor ignores its movement (keep-alives, teleports, chunk batches), one tick
 * later. Packets may be sent from any thread, so the answers wait in a queue for the main thread.
 */
final class FakeConnection extends Connection {

    /** What the fake client reports after each chunk batch: a fast client, so the server is never held back by it. */
    private static final float CHUNKS_PER_TICK = 10;

    private final ServerPlayer player;
    private final Queue<Runnable> answers = new ConcurrentLinkedQueue<>();

    FakeConnection(final ServerPlayer player) {
        super(PacketFlow.SERVERBOUND);
        this.player = player;
        this.channel = new EmbeddedChannel();
        // Plugins read a joining player's address; without one, Player#getAddress would be null.
        this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    static boolean drives(final Player player) {
        return ((CraftPlayer) player).getHandle().connection.connection instanceof FakeConnection;
    }

    @Override
    public void send(final Packet<?> packet, final @Nullable ChannelFutureListener listener, final boolean flush) {
        if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
            this.answer(() -> this.player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId())));
        } else if (packet instanceof ClientboundPlayerPositionPacket teleport) {
            this.answer(() -> this.player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
                teleport.id(), this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot())));
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
            this.answer(() -> this.player.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(CHUNKS_PER_TICK)));
        } else if (packet instanceof ClientboundDisconnectPacket disconnect) {
            this.closeOnKick(disconnect);
        }
    }

    /**
     * A kick only closes the connection once this packet is out; without this the player would stay in the list. Kicks
     * are sent from the main thread, so the connection closes at once, even after the test has ended.
     */
    private void closeOnKick(final ClientboundDisconnectPacket disconnect) {
        if (MinecraftServer.getServer().isSameThread()) {
            this.disconnect(disconnect.reason());
        } else {
            this.answer(() -> this.disconnect(disconnect.reason()));
        }
    }

    @Override
    public void sendPacketAndScheduleNettyTask(final Packet<?> packet, final boolean flush, final @Nullable Runnable after) {
        if (after != null) {
            after.run();
        }
    }

    /** Hands the server what the client answered since the last tick; on the main thread only. */
    void deliverAnswers() {
        Runnable answer;
        while ((answer = this.answers.poll()) != null) {
            if (this.isConnected() && this.player.connection != null) {
                answer.run();
            }
        }
    }

    void answer(final Runnable reply) {
        this.answers.add(reply);
    }
}
