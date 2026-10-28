package net.kindlingmc.pregen;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * What the frontier pregeneration remembers, as kept in {@code pregeneration-frontier.properties}: per world the tiles
 * players were in and the tiles generated since. Reading never throws; whatever cannot be used is left out and named in
 * {@link #problems()}. Writing replaces the file atomically.
 */
final class FrontierState {

    /**
     * One world's tiles; {@code seed} tells a regenerated world from the one the tiles belong to. The sets are not
     * changed once the area is handed out: a {@link FrontierPlan} gives copies.
     */
    record Area(long seed, TileSet visited, TileSet done) {
    }

    private static final String HEADER = "Tiles the frontier pregeneration watches: where players were and what it generated since";
    private static final String SEED = "seed";
    private static final String VISITED = "visited";
    private static final String DONE = "done";
    private static final List<String> SUFFIXES = List.of(SEED, VISITED, DONE);

    private final Map<String, Area> areas = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    /** Reads the file; a missing file gives an empty state without problems. */
    static FrontierState load(final Path file) {
        final FrontierState state = new FrontierState();
        if (Files.exists(file)) {
            state.readFrom(file);
        }
        return state;
    }

    /** The areas as they are now, so another thread can write them while new ones replace them here. */
    FrontierState snapshot() {
        final FrontierState copy = new FrontierState();
        copy.areas.putAll(this.areas);
        return copy;
    }

    Optional<Area> area(final String world) {
        return Optional.ofNullable(this.areas.get(world));
    }

    void put(final String world, final Area area) {
        this.areas.put(world, area);
    }

    void remove(final String world) {
        this.areas.remove(world);
    }

    /** What could not be used when reading, one line each; empty if the file was fine. */
    List<String> problems() {
        return Collections.unmodifiableList(this.problems);
    }

    /** Writes the file; a state without any area deletes it, because a file without areas counts as damaged. */
    void save(final Path file) throws IOException {
        if (this.areas.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        DurableFile.replace(file, this.serialize());
    }

    private String serialize() throws IOException {
        final Properties properties = new Properties();
        this.areas.forEach((world, area) -> {
            properties.setProperty(world + "." + SEED, Long.toString(area.seed()));
            properties.setProperty(world + "." + VISITED, area.visited().toText());
            properties.setProperty(world + "." + DONE, area.done().toText());
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
            this.problems.add("contains no world");
        }
        for (final String key : properties.stringPropertyNames()) {
            if (SUFFIXES.stream().noneMatch(suffix -> key.endsWith("." + suffix))) {
                this.problems.add("unknown key: '" + key + "'");
            }
        }
        for (final String world : worlds) {
            try {
                this.areas.put(world, parseArea(properties, world + "."));
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

    private static Area parseArea(final Properties properties, final String prefix) {
        return new Area(seedOf(properties, prefix), tilesOf(properties, prefix, VISITED), tilesOf(properties, prefix, DONE));
    }

    private static long seedOf(final Properties properties, final String prefix) {
        final String text = properties.getProperty(prefix + SEED);
        if (text == null) {
            throw new IllegalArgumentException(SEED + " is missing");
        }
        try {
            return Long.parseLong(text.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(SEED + " is not a number: '" + text + "'");
        }
    }

    private static TileSet tilesOf(final Properties properties, final String prefix, final String name) {
        try {
            return TileSet.parse(properties.getProperty(prefix + name, ""));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(name + ": " + e.getMessage());
        }
    }
}
