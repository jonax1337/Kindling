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
        logWarnings(Doctor.examineThisServer());
    }

    private static void logWarnings(final List<Finding> findings) {
        findings.stream()
            .filter(finding -> finding.severity() == Severity.WARN)
            .forEach(finding -> LOGGER.warn("Kindling doctor: {} [{}] Fix: {}", finding.what(), finding.basis().label(), finding.fix()));
    }
}
