package net.kindlingmc.os;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Which operating system the server runs on, for code that must treat Linux differently. */
public final class OperatingSystem {

    private static final Path LIBRARY_DIRECTORY = Path.of("/lib");

    private OperatingSystem() {
    }

    public static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    /** Whether this Linux uses musl instead of glibc, which has a musl loader in {@code /lib}. */
    public static boolean usesMusl() {
        try (DirectoryStream<Path> loaders = Files.newDirectoryStream(LIBRARY_DIRECTORY, "ld-musl-*")) {
            return loaders.iterator().hasNext();
        } catch (final IOException e) {
            return false;
        }
    }
}
