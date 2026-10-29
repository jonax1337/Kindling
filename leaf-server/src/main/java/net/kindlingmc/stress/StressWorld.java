package net.kindlingmc.stress;

import io.papermc.paper.configuration.GlobalConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;

/**
 * The world a stress test runs in: created fresh for every test and deleted after it, so the test never changes a
 * world of the server. A player who dies in it keeps the inventory. Where every world has its own time, it stays night
 * without weather, so monsters neither burn nor hide; with Paper's {@code time.affects-all-worlds} the test leaves the
 * shared time alone.
 */
final class StressWorld {

    static final NamespacedKey KEY = new NamespacedKey("kindling", "stress");

    private static final long NIGHT = 18000;

    private StressWorld() {
    }

    static World create() {
        final World world = WorldCreator.ofKey(KEY).createWorld();
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        if (!GlobalConfiguration.get().time.affectsAllWorlds) {
            world.setTime(NIGHT);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
        }
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        return world;
    }

    /** Whether a test that ended with the server left its world behind; it must be deleted before a new test starts. */
    static boolean isLeftOver() {
        return Files.exists(folder());
    }

    /**
     * Whether an old Bukkit world folder of the test world's name exists; creating the world would move it into the
     * test world, which is then deleted.
     */
    static boolean hasLegacyFolder() {
        return Files.exists(Bukkit.getWorldContainer().toPath().resolve(KEY.namespace() + "_" + KEY.value()));
    }

    /** Deletes the folder of the test world, which must not be loaded, on another thread. */
    static CompletableFuture<Void> deleteInBackground() {
        return CompletableFuture.runAsync(StressWorld::deleteFolder);
    }

    /**
     * Unloads the world without saving it and deletes its folder on another thread, completing {@code deleted} then.
     * Answers false, and does nothing, while the world cannot be unloaded yet, as long as players are still in it.
     */
    static boolean discard(final World world, final CompletableFuture<Void> deleted) {
        if (!Bukkit.unloadWorld(world, false)) {
            return false;
        }
        deleteInBackground().whenComplete((done, failure) -> {
            if (failure == null) {
                deleted.complete(null);
            } else {
                deleted.completeExceptionally(failure);
            }
        });
        return true;
    }

    private static void deleteFolder() {
        final Path folder = folder();
        if (Files.exists(folder)) {
            try (Stream<Path> paths = Files.walk(folder)) {
                paths.sorted(Comparator.reverseOrder()).forEach(StressWorld::delete);
            } catch (final IOException e) {
                throw new UncheckedIOException("Could not delete the stress test world " + folder, e);
            }
        }
        deleteIfEmpty(folder.getParent());
    }

    private static void deleteIfEmpty(final Path namespaceFolder) {
        try (Stream<Path> entries = Files.list(namespaceFolder)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(namespaceFolder);
            }
        } catch (final IOException e) {
            MinecraftServer.LOGGER.debug("Kept the folder {} of the stress test world", namespaceFolder, e);
        }
    }

    private static void delete(final Path path) {
        try {
            Files.delete(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not delete " + path + " of the stress test world", e);
        }
    }

    /** The dimension folder of the test world; anything else would be a real world, which must never be deleted. */
    static Path folder() {
        final Path folder = MinecraftServer.getServer().storageSource.getDimensionPath(CraftNamespacedKey.toResourceKey(Registries.DIMENSION, KEY));
        if (!folder.endsWith(Path.of("dimensions", KEY.namespace(), KEY.value()))) {
            throw new IllegalStateException("The stress test world would be at " + folder + ", which is not its own folder");
        }
        return folder;
    }
}
