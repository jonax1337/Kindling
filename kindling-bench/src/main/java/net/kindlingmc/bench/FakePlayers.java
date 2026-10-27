package net.kindlingmc.bench;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Players without a client: the server loads, generates, ticks and sends for them as for real ones. */
interface FakePlayers {

    Player join(String name, Location at);

    /** Joins a player in creative mode and flying, so it stays where it was put until it is moved. */
    default Player joinFlying(String name, Location at) {
        Player player = join(name, at);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        return player;
    }

    /** Moves the player as its client would, through a movement packet the server checks like any other. */
    void move(Player player, Location to);
}
