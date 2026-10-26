package net.kindlingmc.bench;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
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
 * The chunk, entity and packet work up to the network stays real; the encoding and compression on the netty threads is
 * not part of the measurement.
 */
final class ServerFakePlayers implements FakePlayers {

    private final MinecraftServer server = MinecraftServer.getServer();
    private final Consumer<Runnable> nextTick;
    private final ClientInformation client;

    /**
     * @param nextTick runs an action on the main thread in the next tick
     * @param viewDistance the view distance the client asks for; the server sends chunks up to the smaller of this and its own
     */
    ServerFakePlayers(Consumer<Runnable> nextTick, int viewDistance) {
        this.nextTick = nextTick;
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
        FakeConnection connection = new FakeConnection(nextTick);
        connection.player = player;
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

    private static final class FakeConnection extends Connection {

        private final Consumer<Runnable> nextTick;
        private @Nullable ServerPlayer player;

        FakeConnection(Consumer<Runnable> nextTick) {
            super(PacketFlow.SERVERBOUND);
            this.nextTick = nextTick;
            this.channel = new EmbeddedChannel();
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
            if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                answer(() -> player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId())));
            } else if (packet instanceof ClientboundPlayerPositionPacket teleport) {
                answer(() -> player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
                        teleport.id(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())));
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

        private void answer(Runnable reply) {
            nextTick.accept(() -> {
                if (player.connection != null) {
                    reply.run();
                }
            });
        }
    }
}
