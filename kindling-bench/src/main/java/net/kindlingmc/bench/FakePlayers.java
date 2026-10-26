package net.kindlingmc.bench;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Players without a client: the server loads, generates, ticks and sends for them as for real ones. */
interface FakePlayers {

    Player join(String name, Location at);

    /** Moves the player as its client would, through a movement packet the server checks like any other. */
    void move(Player player, Location to);
}
