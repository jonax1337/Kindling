package net.kindlingmc.bench;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The settings of the server under test that decide what the parity scenarios mean, read back from its configuration
 * files after startup: a parity run with an option on and one with it off is only worth anything if the files show that
 * each really had it that way. A setting missing from its file (the server did not write its default) is not reported.
 */
final class ServerOptions {

    private static final String LEAF_FILE = "config/leaf-global.yml";
    private static final String SPIGOT_FILE = "spigot.yml";
    private static final String SPIGOT_WORLD = "world-settings.default.";

    private static final List<Option> REPORTED = List.of(
            new Option("performance.sleeping-block-entity", LEAF_FILE, "performance.sleeping-block-entity"),
            new Option("async.async-pathfinding.enabled", LEAF_FILE, "async.async-pathfinding.enabled"),
            new Option("spigot.hopper-amount", SPIGOT_FILE, SPIGOT_WORLD + "hopper-amount"),
            new Option("spigot.ticks-per.hopper-transfer", SPIGOT_FILE, SPIGOT_WORLD + "ticks-per.hopper-transfer"),
            new Option("spigot.entity-activation-range.villagers", SPIGOT_FILE,
                    SPIGOT_WORLD + "entity-activation-range.villagers"));

    private ServerOptions() {
    }

    /** The reported settings as the files in {@code serverFolder} hold them. */
    static Map<String, Object> read(Path serverFolder) {
        Map<String, YamlConfiguration> files = new HashMap<>();
        Map<String, Object> options = new LinkedHashMap<>();
        for (Option option : REPORTED) {
            YamlConfiguration file = files.computeIfAbsent(option.file(),
                    name -> YamlConfiguration.loadConfiguration(serverFolder.resolve(name).toFile()));
            options.put(option.name(), file.get(option.path()));
        }
        return options;
    }

    private record Option(String name, String file, String path) {
    }
}
