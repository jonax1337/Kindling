package net.kindlingmc.doctor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the few {@code /proc} and {@code /sys} files the doctor needs and understands their formats. */
final class LinuxFiles {

    private static final Pattern CPU_MAX = Pattern.compile("(\\d+) (\\d+)");
    private static final Pattern SELECTED_MODE = Pattern.compile("\\[(\\w+)]");
    private static final int CPU_IDLE_FIELD = 4;
    private static final int CPU_IOWAIT_FIELD = 5;
    private static final int CPU_STEAL_FIELD = 8;
    private static final long NANOS_PER_MICRO = 1000;
    private static final String CGROUP_V2_PREFIX = "0::";
    private static final String ARENA_MAX_TUNABLE = "glibc.malloc.arena_max=";

    private LinuxFiles() {
    }

    static boolean runsOnLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
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

    /** The CPU time of the whole machine from {@code /proc/stat}: its first line, "cpu user nice system idle iowait irq softirq steal ...". */
    static Optional<CpuTimes> parseCpuTimes(final String procStat) {
        final String[] fields = procStat.strip().lines().findFirst().orElse("").split("\\s+");
        if (!fields[0].equals("cpu") || fields.length <= CPU_IDLE_FIELD) {
            return Optional.empty();
        }
        try {
            long total = 0;
            for (int field = 1; field <= Math.min(CPU_STEAL_FIELD, fields.length - 1); field++) {
                total += Long.parseLong(fields[field]);
            }
            final long idle = Long.parseLong(fields[CPU_IDLE_FIELD]) + ticksOrZero(fields, CPU_IOWAIT_FIELD);
            return Optional.of(new CpuTimes(total, idle, ticksOrZero(fields, CPU_STEAL_FIELD)));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Fields that older kernels do not have count as zero. */
    private static long ticksOrZero(final String[] fields, final int field) {
        return field < fields.length ? Long.parseLong(fields[field]) : 0;
    }

    /** Run time and run-queue wait of a thread from its {@code schedstat} file: "run-ns wait-ns timeslices". */
    static Optional<SchedTime> parseSchedstat(final String content) {
        final String[] fields = content.strip().split("\\s+");
        if (fields.length < 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SchedTime(Long.parseLong(fields[0]), Long.parseLong(fields[1])));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * The name in a thread's {@code stat} file: "tid (name) state ...". The name may contain spaces and brackets, so it
     * runs from the first opening to the last closing bracket.
     */
    static Optional<String> parseThreadName(final String stat) {
        final int open = stat.indexOf('(');
        final int close = stat.lastIndexOf(')');
        return open >= 0 && close > open ? Optional.of(stat.substring(open + 1, close)) : Optional.empty();
    }

    /** The thread id at the end of the link target of {@code /proc/thread-self}: "1234/task/5678" is 5678. */
    static OptionalLong parseThreadId(final String threadSelfTarget) {
        try {
            return OptionalLong.of(Long.parseLong(threadSelfTarget.substring(threadSelfTarget.lastIndexOf('/') + 1)));
        } catch (final NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** The throttling counters of a cgroup {@code cpu.stat} file, version 2 (microseconds) or version 1 (nanoseconds). */
    static Optional<CpuThrottling> parseCpuStat(final String content) {
        final Map<String, Long> counters = new HashMap<>();
        content.lines().forEach(line -> putCounter(counters, line));
        if (!counters.containsKey("nr_throttled")) {
            return Optional.empty();
        }
        final long throttledMicros = counters.containsKey("throttled_usec")
            ? counters.get("throttled_usec")
            : counters.getOrDefault("throttled_time", 0L) / NANOS_PER_MICRO;
        return Optional.of(new CpuThrottling(counters.getOrDefault("nr_periods", 0L), counters.get("nr_throttled"), throttledMicros));
    }

    private static void putCounter(final Map<String, Long> counters, final String line) {
        final String[] keyAndValue = line.strip().split("\\s+");
        if (keyAndValue.length != 2) {
            return;
        }
        try {
            counters.put(keyAndValue[0], Long.parseLong(keyAndValue[1]));
        } catch (final NumberFormatException e) {
            // Not a counter: the line is ignored.
        }
    }

    /** The cgroup of this process from {@code /proc/self/cgroup}: "0::/system.slice/server.service" is "/system.slice/server.service". */
    static Optional<String> parseCgroupV2Path(final String procSelfCgroup) {
        return procSelfCgroup.lines()
            .filter(line -> line.startsWith(CGROUP_V2_PREFIX))
            .map(line -> line.substring(CGROUP_V2_PREFIX.length()).strip())
            .findFirst();
    }

    /** The value of {@code glibc.malloc.arena_max} in a {@code GLIBC_TUNABLES} list such as "glibc.malloc.arena_max=2:glibc.malloc.tcache_count=0". */
    static Optional<String> parseArenaMaxTunable(final String glibcTunables) {
        return Arrays.stream(glibcTunables.split(":"))
            .filter(tunable -> tunable.startsWith(ARENA_MAX_TUNABLE))
            .map(tunable -> tunable.substring(ARENA_MAX_TUNABLE.length()))
            .findFirst();
    }
}
