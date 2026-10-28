package net.kindlingmc.os;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.util.Optional;

/** Reads and, for the manageable ones, sets HotSpot flags of the running JVM. */
public final class HotSpotFlags {

    private HotSpotFlags() {
    }

    /** The value of a flag; empty when this JVM does not have it. */
    public static Optional<String> read(final String name) {
        try {
            final HotSpotDiagnosticMXBean diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return diagnostics == null ? Optional.empty() : Optional.of(diagnostics.getVMOption(name).getValue());
        } catch (final IllegalArgumentException | LinkageError e) {
            return Optional.empty();
        }
    }

    /** Sets a manageable flag; false when the JVM refuses the value or the flag. */
    public static boolean set(final String name, final String value) {
        try {
            final HotSpotDiagnosticMXBean diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (diagnostics == null) {
                return false;
            }
            diagnostics.setVMOption(name, value);
            return true;
        } catch (final IllegalArgumentException | UnsupportedOperationException | SecurityException | LinkageError e) {
            return false;
        }
    }
}
