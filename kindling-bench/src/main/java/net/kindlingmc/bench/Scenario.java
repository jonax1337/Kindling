package net.kindlingmc.bench;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What one benchmark run does, read from {@code plugins/KindlingBench/scenario.properties}; every key has a default.
 *
 * @param players fake players that join, spread on a circle around the world origin
 * @param circleRadius blocks from the origin to each player's start
 * @param warmupSeconds time after the joins before anything is measured, so the JIT and the spawn chunks settle
 * @param travelSeconds time the players fly straight outward, which makes the server generate and send chunks
 * @param blocksPerTick flying speed; 1.0 is about an elytra in level flight
 * @param mobsPerPlayer mobs spawned around each player after the flight, a mix of farm animals, villagers and zombies
 * @param settleSeconds time after the spawn before the steady phase is measured
 * @param steadySeconds time the players stand still among the mobs while the tick times are measured
 * @param idleSeconds time the server runs empty after everyone left, before what it still holds is measured
 * @param clientViewDistance the view distance the fake clients ask for (a vanilla client defaults to 12)
 */
record Scenario(int players, int circleRadius, int warmupSeconds, int travelSeconds, double blocksPerTick,
                int mobsPerPlayer, int settleSeconds, int steadySeconds, int idleSeconds, int clientViewDistance) {

    static final String FILE_NAME = "scenario.properties";

    static Scenario read(Path folder) {
        Properties properties = new Properties();
        Path file = folder.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + file, e);
            }
        }
        return new Scenario(
                intOf(properties, "players", 8),
                intOf(properties, "circle-radius", 512),
                intOf(properties, "warmup-seconds", 30),
                intOf(properties, "travel-seconds", 90),
                Double.parseDouble(properties.getProperty("blocks-per-tick", "1.0")),
                intOf(properties, "mobs-per-player", 150),
                intOf(properties, "settle-seconds", 15),
                intOf(properties, "steady-seconds", 90),
                intOf(properties, "idle-seconds", 60),
                intOf(properties, "client-view-distance", 12));
    }

    private static int intOf(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
    }
}
