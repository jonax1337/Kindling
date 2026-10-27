package net.kindlingmc.doctor;

import java.util.Locale;

/** Binary sizes as an operator writes them in JVM flags. */
public final class ByteSize {

    public static final long MIB = 1024L * 1024L;
    public static final long GIB = 1024L * MIB;

    private ByteSize() {
    }

    public static String format(final long bytes) {
        return bytes >= GIB
            ? String.format(Locale.ROOT, "%.1f GiB", (double) bytes / GIB)
            : String.format(Locale.ROOT, "%d MiB", bytes / MIB);
    }

    /** A value for {@code -Xmx}, rounded down to whole mebibytes. */
    static String asXmx(final long bytes) {
        return "-Xmx" + (bytes / MIB) + "M";
    }
}
