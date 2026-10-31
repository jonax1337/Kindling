package net.kindlingmc.bench;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** The figures of one run, in the order they were taken; written as {@value #FILE_NAME}, which run_bench.py waits for. */
final class RunResult {

    static final String FILE_NAME = "bench-result.json";

    private final Map<String, Object> figures = new LinkedHashMap<>();

    /** A result starts with the Java runtime it ran on, so runs on different JDK builds cannot be mixed up. */
    RunResult() {
        figures.put("java_version", Runtime.version().toString());
    }

    void put(String name, Object value) {
        figures.put(name, value);
    }

    void putAll(Map<String, ?> more) {
        figures.putAll(more);
    }

    /** Writes the result into the server folder and stops the server, which ends the run. */
    void writeAndStop(Plugin plugin) {
        write(plugin.getServer().getWorldContainer().toPath().resolve(FILE_NAME));
        Bukkit.shutdown();
    }

    private void write(Path file) {
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(figures));
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + file, e);
        }
    }
}
