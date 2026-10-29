package net.kindlingmc.stress;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * A player without a client, flying in creative mode. It joins through {@code PlayerList.placeNewPlayer} like a client
 * after its configuration phase and moves through the packets a client sends, so the server loads, generates, ticks
 * and sends for it as for a real player. It is never saved: its data must not outlive the test.
 */
final class FakePlayer {

    private static final String NAME_PREFIX = "kindling_";
    /** The render distance a vanilla client asks for by default; the server sends up to the smaller of this and its own. */
    private static final int CLIENT_VIEW_DISTANCE = 12;

    private final ServerPlayer handle;
    private final FakeConnection connection;
    private final int index;
    private final Flight flight = new Flight();

    private FakePlayer(final ServerPlayer handle, final FakeConnection connection, final int index) {
        this.handle = handle;
        this.connection = connection;
        this.index = index;
    }

    static String nameOf(final int serial) {
        return NAME_PREFIX + serial;
    }

    /** Whether no player with this name or its offline id is online, so a fake player of that name collides with no one. */
    static boolean isFree(final String name) {
        return Bukkit.getPlayerExact(name) == null && Bukkit.getPlayer(UUIDUtil.createOfflinePlayerUUID(name)) == null;
    }

    /** Joins a fake player called {@code name} into {@code level} at the start of the line of {@code index}. */
    static FakePlayer join(final ServerLevel level, final int index, final String name) {
        final MinecraftServer server = level.getServer();
        final GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        final ServerPlayer player = new ServerPlayer(server, level, profile, clientInformation());
        // Not persistent from the start: whatever goes wrong while joining, the server never saves this player.
        player.getBukkitEntity().setPersistent(false);
        final PlayerLayout.Spot spot = PlayerLayout.spot(index, 0);
        player.snapTo(spot.x(), PlayerLayout.HEIGHT, spot.z(), 0, 0);
        final FakeConnection connection = new FakeConnection(player);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        } catch (final RuntimeException e) {
            // Closed before it is listed below, the connection is removed at the next network tick, and the player with it.
            connection.disconnect(Component.literal("Could not join the stress test"));
            throw e;
        } finally {
            // Only connections in this list are ticked, and that tick is what ticks the player and checks its movement.
            server.getConnection().getConnections().add(connection);
        }
        final FakePlayer fake = new FakePlayer(player, connection, index);
        try {
            fake.getReady();
        } catch (final RuntimeException e) {
            // Listed but not yet known to the load, the player would otherwise never leave.
            fake.leave();
            throw e;
        }
        return fake;
    }

    private void getReady() {
        // The movement check measures from the last good position, which is unset until the first tick.
        this.handle.connection.resetPosition();
        // A client reports that it has loaded the world; until then the server ticks the player only partly.
        this.connection.answer(() -> this.handle.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket()));
        this.fly();
    }

    private static ClientInformation clientInformation() {
        final ClientInformation standard = ClientInformation.createDefault();
        return new ClientInformation(standard.language(), CLIENT_VIEW_DISTANCE, standard.chatVisibility(), standard.chatColors(),
            standard.modelCustomisation(), standard.mainHand(), standard.textFilteringEnabled(), standard.allowsListing(),
            standard.particleStatus());
    }

    private void fly() {
        final CraftPlayer player = this.handle.getBukkitEntity();
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    /** One tick of the client: its answers to the server, and its next position if it is flying. */
    void tick(final PlayerMotion motion) {
        this.connection.deliverAnswers();
        if (motion == PlayerMotion.FLYING && this.isPresent()) {
            final double offset = PlayerLayout.offsetAt(this.index, this.handle.getX(), this.handle.getZ());
            this.moveTo(PlayerLayout.spot(this.index, this.flight.next(offset)));
        }
    }

    private void moveTo(final PlayerLayout.Spot spot) {
        this.handle.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(spot.x(), PlayerLayout.HEIGHT, spot.z(), false, false));
        // A client ends every tick with this packet; without it a second position in the next tick counts as a cheat.
        this.handle.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
    }

    boolean flies(final int line) {
        return this.index == line;
    }

    /** Whether the player is still online and in the test world, where only a plugin could have taken it from. */
    boolean isIn(final ServerLevel level) {
        return this.isPresent() && this.handle.level() == level;
    }

    boolean hasLeft() {
        return !this.connection.isConnected();
    }

    private boolean isPresent() {
        return this.connection.isConnected() && this.handle.connection != null;
    }

    /** Disconnects the player; the server removes it at its next network tick. */
    void leave() {
        this.connection.disconnect(Component.literal("The stress test is over"));
    }
}
