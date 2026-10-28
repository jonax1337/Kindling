package net.kindlingmc.memory;

import java.lang.management.ManagementFactory;
import javax.management.JMException;
import javax.management.ObjectName;

/** Runs HotSpot diagnostic commands through their MBean; unlike {@code System.gc()} they work with {@code -XX:+DisableExplicitGC}. */
final class DiagnosticCommands {

    private static final String MBEAN = "com.sun.management:type=DiagnosticCommand";
    private static final Object[] NO_ARGUMENTS = {null};
    private static final String[] ARGUMENT_TYPES = {String[].class.getName()};

    private DiagnosticCommands() {
    }

    /** Runs a command such as {@code gcRun}; false when this JVM does not have it. */
    static boolean run(final String operation) {
        try {
            ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName(MBEAN), operation, NO_ARGUMENTS, ARGUMENT_TYPES);
            return true;
        } catch (final JMException | RuntimeException e) {
            return false;
        }
    }
}
