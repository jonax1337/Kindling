package previewchunks;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A hands-free straight flight for looking at previews: spectator mode, a fixed height and direction (where the player
 * looked when it started), a fixed speed. The player keeps control of where they look. Main thread only.
 */
final class Tour {
    private static final double MIN_HEIGHT = 150;
    private static final int TICKS_PER_SECOND = 20;

    private final Player player;
    private final GameMode previousMode;
    private final double stepX;
    private final double stepZ;
    private final double height;

    private Tour(Player player, double blocksPerSecond) {
        this.player = player;
        this.previousMode = player.getGameMode();
        double yaw = Math.toRadians(player.getLocation().getYaw());
        double perTick = blocksPerSecond / TICKS_PER_SECOND;
        this.stepX = -Math.sin(yaw) * perTick;
        this.stepZ = Math.cos(yaw) * perTick;
        this.height = Math.max(MIN_HEIGHT, player.getLocation().getY());
    }

    static Tour start(Player player, double blocksPerSecond) {
        Tour tour = new Tour(player, blocksPerSecond);
        player.setGameMode(GameMode.SPECTATOR);
        return tour;
    }

    void step() {
        Location next = this.player.getLocation().add(this.stepX, 0, this.stepZ);
        next.setY(this.height);
        this.player.teleport(next, TeleportFlag.Relative.VELOCITY_ROTATION);
    }

    /** Lands the player on the ground below before giving back their game mode, so a survival player does not fall. */
    void stop() {
        Location here = this.player.getLocation();
        Location ground = here.getWorld().getHighestBlockAt(here).getLocation().add(0.5, 1, 0.5);
        ground.setYaw(here.getYaw());
        ground.setPitch(here.getPitch());
        this.player.teleport(ground);
        this.player.setGameMode(this.previousMode);
    }
}
