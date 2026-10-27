package net.kindlingmc.bench;

import java.util.Collection;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Players without a client: the server loads, generates, ticks and sends for them as for real ones. */
interface FakePlayers {

    Player join(String name, Location at);

    /** Joins a player in the given mode and flying, so it stays where it was put until it is moved. */
    default Player joinFlying(String name, Location at, GameMode mode) {
        Player player = join(name, at);
        player.setGameMode(mode);
        player.setAllowFlight(true);
        player.setFlying(true);
        return player;
    }

    /** Moves the player as its client would, through a movement packet the server checks like any other. */
    void move(Player player, Location to);

    /** The chunk packets and batches the server has sent the player since it joined. */
    ChunkTraffic chunkTraffic(Player player);

    /** The chunk traffic of all given players together. */
    default ChunkTraffic chunkTraffic(Collection<Player> players) {
        ChunkTraffic total = ChunkTraffic.NONE;
        for (Player player : players) {
            total = total.plus(chunkTraffic(player));
        }
        return total;
    }
}
