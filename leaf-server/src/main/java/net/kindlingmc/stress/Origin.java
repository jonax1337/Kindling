package net.kindlingmc.stress;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

/**
 * Where a real player was before the stress test took it into the test world, written into the player's saved data
 * as {@code <world key>;<x>;<y>;<z>;<yaw>;<pitch>}. Saved with the player, it outlasts a logout, a stop or a crash of
 * the server, so the player can be sent back even after the test world is gone.
 */
record Origin(NamespacedKey world, double x, double y, double z, float yaw, float pitch) {

    private static final String SEPARATOR = ";";
    private static final int FIELDS = 6;

    static Origin of(final Location location) {
        return new Origin(location.getWorld().getKey(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
    }

    /** The origin written as {@code text}, or nothing if the text is not one. */
    static Optional<Origin> parse(final String text) {
        final String[] fields = text.split(SEPARATOR);
        final NamespacedKey world = fields.length == FIELDS ? NamespacedKey.fromString(fields[0]) : null;
        if (world == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Origin(world, Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                Double.parseDouble(fields[3]), Float.parseFloat(fields[4]), Float.parseFloat(fields[5])));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    String write() {
        return String.join(SEPARATOR, this.world.asString(), String.valueOf(this.x), String.valueOf(this.y),
            String.valueOf(this.z), String.valueOf(this.yaw), String.valueOf(this.pitch));
    }

    /** The place to go back to, if its world is still loaded. */
    Optional<Location> location() {
        final World loaded = Bukkit.getWorld(this.world);
        return loaded == null ? Optional.empty() : Optional.of(new Location(loaded, this.x, this.y, this.z, this.yaw, this.pitch));
    }
}
