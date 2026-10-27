package net.kindlingmc.bench;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Fake players that join through {@code PlayerList.placeNewPlayer} like a client after its configuration phase, and
 * move by handing the server the movement packets a client would send. Their connection drops every packet; it only
 * answers keep-alives and teleports, a tick later, so the server neither kicks them nor ignores their movement.
 * It counts the chunk packets it is sent and, if asked to, acknowledges chunk batches like a client that processes a
 * fixed number of chunks per tick.
 * The chunk, entity and packet work up to the network stays real; the encoding and compression on the netty threads is
 * not part of the measurement.
 */
final class ServerFakePlayers implements FakePlayers {

    private final MinecraftServer server = MinecraftServer.getServer();
    private final TickScheduler scheduler;
    private final double chunksPerTick;
    private final ClientInformation client;
    private final Map<UUID, FakeConnection> connections = new ConcurrentHashMap<>();

    /**
     * @param scheduler runs actions on the main thread
     * @param viewDistance the view distance the client asks for; the server sends chunks up to the smaller of this and its own
     * @param chunksPerTick how many chunks per tick the simulated client processes, which is what it reports to the
     *        server after each chunk batch and how long it takes to report; 0 for a client that never answers
     */
    ServerFakePlayers(TickScheduler scheduler, int viewDistance, double chunksPerTick) {
        this.scheduler = scheduler;
        this.chunksPerTick = chunksPerTick;
        ClientInformation standard = ClientInformation.createDefault();
        this.client = new ClientInformation(standard.language(), viewDistance, standard.chatVisibility(), standard.chatColors(),
                standard.modelCustomisation(), standard.mainHand(), standard.textFilteringEnabled(), standard.allowsListing(),
                standard.particleStatus());
    }

    @Override
    public Player join(String name, Location at) {
        ServerLevel level = ((CraftWorld) at.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, client);
        player.snapTo(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        FakeConnection connection = new FakeConnection(scheduler, chunksPerTick);
        connection.player = player;
        connections.put(player.getUUID(), connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        // Only connections in this list are ticked, and that tick is what ticks the player and checks its movement.
        server.getConnection().getConnections().add(connection);
        // The movement check measures from the last good position, which is unset until the first tick.
        player.connection.resetPosition();
        // A client reports that it has loaded the world; until then the server ticks the player only partly.
        connection.answer(() -> player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket()));
        return player.getBukkitEntity();
    }

    @Override
    public void move(Player player, Location to) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(to.getX(), to.getY(), to.getZ(), false, false));
        // A client ends every tick with this packet; without it a second position in the next tick counts as a cheat.
        handle.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
    }

    @Override
    public ChunkTraffic chunkTraffic(Player player) {
        FakeConnection connection = connections.get(player.getUniqueId());
        return connection == null ? ChunkTraffic.NONE : connection.traffic();
    }

    private static final class FakeConnection extends Connection {

        private final TickScheduler scheduler;
        private final double chunksPerTick;
        private final AtomicLong chunkPackets = new AtomicLong();
        private final AtomicLong batches = new AtomicLong();
        private @Nullable ServerPlayer player;

        FakeConnection(TickScheduler scheduler, double chunksPerTick) {
            super(PacketFlow.SERVERBOUND);
            this.scheduler = scheduler;
            this.chunksPerTick = chunksPerTick;
            this.channel = new EmbeddedChannel();
        }

        ChunkTraffic traffic() {
            return new ChunkTraffic(chunkPackets.get(), batches.get());
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
            if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                answer(() -> player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId())));
            } else if (packet instanceof ClientboundPlayerPositionPacket teleport) {
                answer(() -> player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
                        teleport.id(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())));
            } else if (packet instanceof ClientboundLevelChunkWithLightPacket) {
                chunkPackets.incrementAndGet();
            } else if (packet instanceof ClientboundChunkBatchFinishedPacket batch) {
                batches.incrementAndGet();
                acknowledge(batch);
            } else if (packet instanceof ClientboundDisconnectPacket disconnect) {
                // The server closes the connection only once this packet is out; a kick must not leave a frozen player.
                MinecraftServer.LOGGER.warn("Fake player {} kicked: {}", player.getPlainTextName(), disconnect.reason().getString());
                channel.close();
            }
        }

        @Override
        public void sendPacketAndScheduleNettyTask(Packet<?> packet, boolean flush, @Nullable Runnable after) {
            if (after != null) {
                after.run();
            }
        }

        /** A client reports a batch once it has processed it, and tells the server how many chunks per tick it manages. */
        private void acknowledge(ClientboundChunkBatchFinishedPacket batch) {
            if (chunksPerTick > 0) {
                int processingTicks = (int) Math.ceil(batch.batchSize() / chunksPerTick);
                answer(processingTicks, () -> player.connection.handleChunkBatchReceived(
                        new ServerboundChunkBatchReceivedPacket((float) chunksPerTick)));
            }
        }

        private void answer(Runnable reply) {
            answer(0, reply);
        }

        private void answer(int delayTicks, Runnable reply) {
            scheduler.runLater(delayTicks, () -> {
                if (player.connection != null) {
                    reply.run();
                }
            });
        }
    }
}
