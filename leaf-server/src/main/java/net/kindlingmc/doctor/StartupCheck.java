package net.kindlingmc.doctor;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.dreeam.leaf.config.modules.opt.StartupDoctor;
import org.slf4j.Logger;

/** Logs the warnings of the doctor once, on the first tick, which is after the server has finished starting. */
public final class StartupCheck {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static boolean done;

    private StartupCheck() {
    }

    public static void install(final MinecraftServer server) {
        if (StartupDoctor.startupCheck) {
            server.addTickable(StartupCheck::runOnce);
        }
    }

    private static void runOnce() {
        if (done) {
            return;
        }
        done = true;
        try {
            warningLines(Doctor.examineThisServer()).forEach(LOGGER::warn);
        } catch (final RuntimeException | LinkageError e) {
            // A diagnostic must never stop the server.
            LOGGER.warn("Kindling doctor: the start-up check could not run", e);
        }
    }

    /** One log line per WARN finding; findings of other severities are left to {@code /kindling doctor}. */
    static List<String> warningLines(final List<Finding> findings) {
        return findings.stream()
            .filter(finding -> finding.severity() == Severity.WARN)
            .map(finding -> "Kindling doctor: " + finding.what() + " [" + finding.basis().label() + "] Fix: " + finding.fix())
            .toList();
    }
}
