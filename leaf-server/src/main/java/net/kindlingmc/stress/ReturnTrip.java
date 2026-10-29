package net.kindlingmc.stress;

import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.purpurmc.purpur.util.MinecraftInternalPlugin;

/**
 * The way back for real players the stress test takes into its world. Where a player came from is kept in its saved
 * data until it is back, so a player who logs out during a test, or is online when the server stops, is sent back when
 * it joins again; without this, the server would place it in the main world at its coordinates in the deleted test
 * world.
 */
final class ReturnTrip implements Listener {

    private static final NamespacedKey ORIGIN = new NamespacedKey("kindling", "stress_origin");

    private ReturnTrip() {
    }

    static void install() {
        Bukkit.getPluginManager().registerEvent(PlayerJoinEvent.class, new ReturnTrip(), EventPriority.NORMAL,
            (listener, event) -> ((ReturnTrip) listener).onJoin(event), new MinecraftInternalPlugin(), false);
    }

    /** Takes the player to {@code destination} in the test world and remembers where it came from. */
    static void takeInto(final Player player, final Location destination) {
        final PersistentDataContainer data = player.getPersistentDataContainer();
        if (!data.has(ORIGIN)) {
            data.set(ORIGIN, PersistentDataType.STRING, Origin.of(player.getLocation()).write());
        }
        player.teleport(destination);
    }

    /** Sends the player back where it came from, or to the world spawn; answers whether it got there. */
    static boolean sendBack(final Player player) {
        final Location destination = originOf(player).flatMap(Origin::location)
            .orElseGet(() -> Bukkit.getWorlds().getFirst().getSpawnLocation());
        if (!player.teleport(destination)) {
            return false;
        }
        player.getPersistentDataContainer().remove(ORIGIN);
        return true;
    }

    private static Optional<Origin> originOf(final Player player) {
        final String written = player.getPersistentDataContainer().get(ORIGIN, PersistentDataType.STRING);
        return written == null ? Optional.empty() : Origin.parse(written);
    }

    /** A player still on its way back who did not join into a running test is sent back now. */
    private void onJoin(final Event event) {
        // Paper reports a listener's exception through the plugin's logger, which the internal plugin does not have.
        try {
            if (event instanceof PlayerJoinEvent join && isOnItsWayBack(join.getPlayer())) {
                sendBack(join.getPlayer());
            }
        } catch (final RuntimeException e) {
            MinecraftServer.LOGGER.error("Could not send a player back from the stress test world", e);
        }
    }

    private static boolean isOnItsWayBack(final Player player) {
        return player.getPersistentDataContainer().has(ORIGIN) && !player.getWorld().getKey().equals(StressWorld.KEY);
    }
}
