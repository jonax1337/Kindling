package net.kindlingmc.doctor;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/** {@code /kindling doctor}: the top-level command of Kindling's own tools. */
public final class KindlingCommand extends Command {

    public static final String LABEL = "kindling";
    private static final String DOCTOR = "doctor";
    private static final String DOCTOR_PERMISSION = "kindling.command.doctor";
    private static final String USAGE = "/kindling doctor";

    public KindlingCommand() {
        super(LABEL);
        this.description = "Kindling's tools";
        this.usageMessage = USAGE;
        this.setPermission(DOCTOR_PERMISSION);
        Bukkit.getServer().getPluginManager().addPermission(new Permission(DOCTOR_PERMISSION, PermissionDefault.OP));
    }

    @Override
    public boolean execute(final CommandSender sender, final String label, final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase(DOCTOR)) {
            sender.sendPlainMessage("Usage: " + USAGE);
        } else {
            sender.sendPlainMessage(String.join("\n", DoctorReport.lines(Doctor.examineThisServer())));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) {
        final boolean completesDoctor = args.length == 1
            && sender.hasPermission(DOCTOR_PERMISSION)
            && DOCTOR.startsWith(args[0].toLowerCase(Locale.ROOT));
        return completesDoctor ? List.of(DOCTOR) : List.of();
    }
}
