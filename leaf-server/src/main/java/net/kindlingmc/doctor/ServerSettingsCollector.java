package net.kindlingmc.doctor;

import io.papermc.paper.configuration.GlobalConfiguration;
import java.util.OptionalInt;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.spigotmc.SpigotConfig;

/** Reads the settings of the running server. */
final class ServerSettingsCollector {

    private ServerSettingsCollector() {
    }

    static ServerSettings collect() {
        return new ServerSettings(
            explicitWorkerThreads(),
            Bukkit.getViewDistance(),
            Bukkit.getSimulationDistance(),
            Bukkit.getMaxPlayers(),
            behindProxy(),
            ((CraftServer) Bukkit.getServer()).getServer().getCompressionThreshold());
    }

    private static OptionalInt explicitWorkerThreads() {
        final int configured = GlobalConfiguration.get().chunkSystem.workerThreads;
        return configured > 0 ? OptionalInt.of(configured) : OptionalInt.empty();
    }

    private static boolean behindProxy() {
        return GlobalConfiguration.get().proxies.velocity.enabled || SpigotConfig.bungee;
    }
}
