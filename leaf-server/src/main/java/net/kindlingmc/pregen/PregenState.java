package net.kindlingmc.pregen;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;

/**
 * The progress of the pregenerations as kept in {@code pregeneration.properties}: one {@link Run} per world.
 * Reading never throws; whatever cannot be used is left out and named in {@link #problems()}. Writing replaces the
 * file atomically, so a crash during a write leaves the previous file intact.
 */
final class PregenState {

    enum Phase {
        RUNNING, PAUSED, DONE;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Phase of(final String key) {
            for (final Phase phase : values()) {
                if (phase.key().equals(key)) {
                    return phase;
                }
            }
            throw new IllegalArgumentException("state is unknown: '" + key + "'");
        }
    }

    /** One world's run; {@code next} counts in the order of {@link TileSpiral}; {@code seed} is empty in runs saved before it was recorded. */
    record Run(int centerX, int centerZ, int radius, long next, Phase phase, OptionalLong seed) {

        /**
         * Whether this run is done and covers {@code configuredRadius} chunks around its centre as far as the border lets
         * that area reach; a run is saved with the radius clipped by the border, so it is compared with the clipped one.
         */
        boolean isDoneFor(final int configuredRadius, final ChunkBorder border) {
            return this.phase == Phase.DONE && this.radius >= Math.min(configuredRadius, border.reachFrom(this.centerX, this.centerZ));
        }
    }

    private static final String HEADER = "Progress of the chunk pregeneration; '/pregen' changes it";
    private static final String CENTER_X = "center-x";
    private static final String CENTER_Z = "center-z";
    private static final String RADIUS = "radius";
    private static final String NEXT = "next";
    private static final String ORDER = "order";
    private static final String STATE = "state";
    private static final String SEED = "seed";
    private static final String TILE_ORDER = "tiles-" + TileSpiral.TILE_SIDE;
    private static final List<String> SUFFIXES = List.of(CENTER_X, CENTER_Z, RADIUS, NEXT, ORDER, STATE, SEED);

    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    /** Reads the file; a missing file gives an empty state without problems. */
    static PregenState load(final Path file) {
        final PregenState state = new PregenState();
        if (Files.exists(file)) {
            state.readFrom(file);
        }
        return state;
    }

    /** Keeps the unusable file next to the original for diagnosis. */
    static void keepCorrupt(final Path file) throws IOException {
        Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
    }

    /** The runs as they are now, independent of later changes, so another thread can write them. */
    PregenState snapshot() {
        final PregenState copy = new PregenState();
        copy.runs.putAll(this.runs);
        return copy;
    }

    Optional<Run> run(final String world) {
        return Optional.ofNullable(this.runs.get(world));
    }

    void put(final String world, final Run run) {
        this.runs.put(world, run);
    }

    void remove(final String world) {
        this.runs.remove(world);
    }

    /** What could not be used when reading, one line each; empty if the file was fine. */
    List<String> problems() {
        return Collections.unmodifiableList(this.problems);
    }

    /** Writes the file; a state without any run deletes it, because a file without runs counts as damaged. */
    void save(final Path file) throws IOException {
        if (this.runs.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            writeDurably(temporary, serialize());
            replace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Forces the bytes to disk, so a power loss after the move cannot leave an empty or truncated file. */
    private static void writeDurably(final Path file, final String text) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
            final ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    private static void replace(final Path temporary, final Path file) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String serialize() throws IOException {
        final Properties properties = new Properties();
        this.runs.forEach((world, run) -> {
            final String prefix = world + ".";
            properties.setProperty(prefix + CENTER_X, Integer.toString(run.centerX()));
            properties.setProperty(prefix + CENTER_Z, Integer.toString(run.centerZ()));
            properties.setProperty(prefix + RADIUS, Integer.toString(run.radius()));
            properties.setProperty(prefix + NEXT, Long.toString(run.next()));
            properties.setProperty(prefix + ORDER, TILE_ORDER);
            properties.setProperty(prefix + STATE, run.phase().key());
            run.seed().ifPresent(seed -> properties.setProperty(prefix + SEED, Long.toString(seed)));
        });
        final StringWriter writer = new StringWriter();
        properties.store(writer, HEADER);
        return writer.toString();
    }

    private void readFrom(final Path file) {
        final String text;
        try {
            text = Files.readString(file);
        } catch (final IOException e) {
            this.problems.add("cannot be read: " + e);
            return;
        }
        if (text.isBlank()) {
            this.problems.add("the file is empty");
            return;
        }
        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (final IOException | IllegalArgumentException e) {
            this.problems.add("is not a properties file: " + e.getMessage());
            return;
        }
        final Set<String> worlds = worldsIn(properties);
        if (worlds.isEmpty()) {
            this.problems.add("contains no pregeneration run");
        }
        for (final String key : properties.stringPropertyNames()) {
            if (SUFFIXES.stream().noneMatch(suffix -> key.endsWith("." + suffix))) {
                this.problems.add("unknown key: '" + key + "'");
            }
        }
        for (final String world : worlds) {
            try {
                this.runs.put(world, parseRun(properties, world + "."));
            } catch (final IllegalArgumentException e) {
                this.problems.add("world " + world + ": " + e.getMessage());
            }
        }
    }

    private static Set<String> worldsIn(final Properties properties) {
        final Set<String> worlds = new LinkedHashSet<>();
        for (final String key : properties.stringPropertyNames()) {
            for (final String suffix : SUFFIXES) {
                if (key.endsWith("." + suffix)) {
                    worlds.add(key.substring(0, key.length() - suffix.length() - 1));
                }
            }
        }
        return worlds;
    }

    private static Run parseRun(final Properties properties, final String prefix) {
        final long next = nonNegative(NEXT, longOf(properties, prefix, NEXT));
        // Runs saved before the tile order count rings and are converted.
        final String order = properties.getProperty(prefix + ORDER);
        if (order != null && !TILE_ORDER.equals(order)) {
            throw new IllegalArgumentException("order is unknown: '" + order + "'");
        }
        final boolean tileOrder = order != null;
        return new Run(
            intOf(properties, prefix, CENTER_X),
            intOf(properties, prefix, CENTER_Z),
            radiusOf(properties, prefix),
            tileOrder ? next : TileSpiral.fromRingIndex(next),
            Phase.of(valueOf(properties, prefix, STATE)),
            properties.containsKey(prefix + SEED) ? OptionalLong.of(longOf(properties, prefix, SEED)) : OptionalLong.empty());
    }

    private static int radiusOf(final Properties properties, final String prefix) {
        final int radius = (int) nonNegative(RADIUS, intOf(properties, prefix, RADIUS));
        if (radius > TileSpiral.MAX_RADIUS) {
            throw new IllegalArgumentException(RADIUS + " is larger than any world border: " + radius + " > " + TileSpiral.MAX_RADIUS);
        }
        return radius;
    }

    private static long nonNegative(final String name, final long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " is negative: " + value);
        }
        return value;
    }

    private static int intOf(final Properties properties, final String prefix, final String name) {
        final long value = longOf(properties, prefix, name);
        if ((int) value != value) {
            throw new IllegalArgumentException(name + " is out of range: " + value);
        }
        return (int) value;
    }

    private static long longOf(final Properties properties, final String prefix, final String name) {
        final String text = valueOf(properties, prefix, name);
        try {
            return Long.parseLong(text.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a number: '" + text + "'");
        }
    }

    private static String valueOf(final Properties properties, final String prefix, final String name) {
        final String value = properties.getProperty(prefix + name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return value;
    }
}
