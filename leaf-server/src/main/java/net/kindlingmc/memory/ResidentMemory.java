package net.kindlingmc.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The physical memory the process holds, read from {@code /proc/self/status} (Linux only). */
final class ResidentMemory {

    private static final Path STATUS = Path.of("/proc/self/status");
    private static final Pattern VM_RSS = Pattern.compile("^VmRSS:\\s+(\\d+) kB$", Pattern.MULTILINE);
    private static final long BYTES_PER_KIB = 1024;

    private ResidentMemory() {
    }

    /** The resident set in bytes; empty where there is no {@code /proc}. */
    static OptionalLong currentBytes() {
        try {
            return parseBytes(Files.readString(STATUS));
        } catch (final IOException | RuntimeException e) {
            return OptionalLong.empty();
        }
    }

    static OptionalLong parseBytes(final String status) {
        final Matcher matcher = VM_RSS.matcher(status);
        return matcher.find() ? OptionalLong.of(Long.parseLong(matcher.group(1)) * BYTES_PER_KIB) : OptionalLong.empty();
    }
}
