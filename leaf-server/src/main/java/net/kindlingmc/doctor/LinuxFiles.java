package net.kindlingmc.doctor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the few {@code /proc} and {@code /sys} files the doctor needs and understands their formats. */
final class LinuxFiles {

    private static final Pattern CPU_MAX = Pattern.compile("(\\d+) (\\d+)");
    private static final Pattern SELECTED_MODE = Pattern.compile("\\[(\\w+)]");

    private LinuxFiles() {
    }

    /** The content of a small text file; empty when it does not exist or cannot be read. */
    static Optional<String> read(final Path file) {
        try {
            return Optional.of(Files.readString(file).strip());
        } catch (final IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The CPUs of a cgroup v2 {@code cpu.max} file ("200000 100000" is two CPUs); empty for "max". */
    static OptionalDouble parseCpuMax(final String content) {
        final Matcher matcher = CPU_MAX.matcher(content.strip());
        if (!matcher.matches()) {
            return OptionalDouble.empty();
        }
        final double period = Double.parseDouble(matcher.group(2));
        return period == 0 ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(matcher.group(1)) / period);
    }

    /** The bytes of a cgroup v2 {@code memory.max} file; empty for "max". */
    static OptionalLong parseMemoryMax(final String content) {
        try {
            return OptionalLong.of(Long.parseLong(content.strip()));
        } catch (final NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** The selected entry of a kernel choice list: "always [madvise] never" is "madvise". */
    static Optional<String> parseSelectedMode(final String content) {
        final Matcher matcher = SELECTED_MODE.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
