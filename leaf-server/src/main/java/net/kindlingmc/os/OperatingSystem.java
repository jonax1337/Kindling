package net.kindlingmc.os;

import java.util.Locale;

/** Which operating system the server runs on, for code that must treat Linux differently. */
public final class OperatingSystem {

    private OperatingSystem() {
    }

    public static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }
}
