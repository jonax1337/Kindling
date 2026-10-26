package net.kindlingmc.pregen;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jspecify.annotations.Nullable;

/** {@code /pregen start <world> <radius> [<x> <z>] | pause <world> | resume <world> | cancel <world> | status}. */
public final class PregenCommand extends Command {

    public static final String LABEL = "pregen";
    private static final String PERMISSION = "kindling.command.pregen";
    private static final String USAGE = "/pregen start <world> <radius in blocks> [<x> <z>] | pause <world> | resume <world> | cancel <world> | status";
    private static final List<String> ACTIONS = List.of("start", "pause", "resume", "cancel", "status");

    public PregenCommand() {
        super(LABEL);
        this.description = "Generates chunks ahead of time without slowing the server down";
        this.usageMessage = USAGE;
        this.setPermission(PERMISSION);
        Bukkit.getServer().getPluginManager().addPermission(new Permission(PERMISSION, PermissionDefault.OP));
    }

    @Override
    public boolean execute(final CommandSender sender, final String label, final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        final String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            final List<String> lines = Pregenerator.status();
            sender.sendPlainMessage(lines.isEmpty() ? "No pregeneration runs." : String.join("\n", lines));
            return true;
        }
        final ServerLevel level = args.length >= 2 ? levelOf(args[1]) : null;
        if (level == null || !ACTIONS.contains(action)) {
            sender.sendPlainMessage("Usage: " + USAGE);
            return true;
        }
        sender.sendPlainMessage(switch (action) {
            case "start" -> start(level, args);
            case "pause" -> Pregenerator.pause(level) ? "Paused." : "No run in that world.";
            case "resume" -> Pregenerator.resume(level) ? "Resumed." : "No run in that world.";
            default -> Pregenerator.cancel(level) ? "Cancelled." : "No run in that world.";
        });
        return true;
    }

    private static String start(final ServerLevel level, final String[] args) {
        try {
            final int radius = Integer.parseInt(args[2]);
            final BlockPos center = args.length >= 5
                ? new BlockPos(Integer.parseInt(args[3]), 0, Integer.parseInt(args[4]))
                : level.getRespawnData().pos();
            return "Started: " + Pregenerator.start(level, center, radius);
        } catch (final ArrayIndexOutOfBoundsException | NumberFormatException e) {
            return "Usage: " + USAGE;
        }
    }

    private static @Nullable ServerLevel levelOf(final String name) {
        final World world = Bukkit.getWorld(name);
        return world == null ? null : ((CraftWorld) world).getHandle();
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream().filter(action -> action.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("status")) {
            return Bukkit.getWorlds().stream().map(World::getName).filter(name -> name.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
