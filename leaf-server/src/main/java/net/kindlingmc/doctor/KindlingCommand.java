package net.kindlingmc.doctor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kindlingmc.stress.StressTest;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/** {@code /kindling doctor | stress ...}: the top-level command of Kindling's own tools. */
public final class KindlingCommand extends Command {

    public static final String LABEL = "kindling";
    private static final String DOCTOR = "doctor";
    private static final String STRESS = "stress";
    private static final String DOCTOR_PERMISSION = "kindling.command.doctor";
    private static final String STRESS_PERMISSION = "kindling.command.stress";
    private static final String USAGE = "/kindling doctor | " + StressTest.USAGE.substring("/kindling ".length());

    public KindlingCommand() {
        super(LABEL);
        this.description = "Kindling's tools";
        this.usageMessage = USAGE;
        // Bukkit lets a sender with either permission use the command; each tool checks its own below.
        this.setPermission(DOCTOR_PERMISSION + ";" + STRESS_PERMISSION);
        Bukkit.getServer().getPluginManager().addPermission(new Permission(DOCTOR_PERMISSION, PermissionDefault.OP));
        Bukkit.getServer().getPluginManager().addPermission(new Permission(STRESS_PERMISSION, PermissionDefault.OP));
        StressTest.install(MinecraftServer.getServer());
    }

    @Override
    public boolean execute(final CommandSender sender, final String label, final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        final String tool = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (tool.equals(DOCTOR) && sender.hasPermission(DOCTOR_PERMISSION)) {
            sender.sendPlainMessage(String.join("\n", DoctorReport.lines(Doctor.examineThisServer())));
        } else if (tool.equals(STRESS) && sender.hasPermission(STRESS_PERMISSION)) {
            sender.sendPlainMessage(StressTest.order(sender, Arrays.copyOfRange(args, 1, args.length)));
        } else {
            sender.sendPlainMessage("Usage: " + USAGE);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) {
        if (args.length == 1) {
            return permittedTools(sender).stream().filter(tool -> tool.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length > 1 && args[0].equalsIgnoreCase(STRESS) && sender.hasPermission(STRESS_PERMISSION)) {
            return StressTest.tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    private static List<String> permittedTools(final CommandSender sender) {
        final List<String> tools = new ArrayList<>();
        if (sender.hasPermission(DOCTOR_PERMISSION)) {
            tools.add(DOCTOR);
        }
        if (sender.hasPermission(STRESS_PERMISSION)) {
            tools.add(STRESS);
        }
        return tools;
    }
}
