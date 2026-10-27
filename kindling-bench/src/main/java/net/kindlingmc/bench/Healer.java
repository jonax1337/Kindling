package net.kindlingmc.bench;

import java.util.Collection;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Keeps survival fake players alive and fed, so mobs keep going for them and the run does not lose its players. */
final class Healer {

    private static final int PERIOD_TICKS = 5;
    private static final int FULL_FOOD = 20;

    private Healer() {
    }

    /** Heals the players every few ticks for as long as they are online. */
    static void keepAlive(Plugin plugin, Collection<Player> players) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> players.forEach(Healer::restore), PERIOD_TICKS, PERIOD_TICKS);
    }

    private static void restore(Player player) {
        if (player.isOnline() && !player.isDead()) {
            player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
            player.setFoodLevel(FULL_FOOD);
        }
    }
}
