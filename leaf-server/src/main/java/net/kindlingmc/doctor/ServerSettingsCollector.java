package net.kindlingmc.doctor;

import io.papermc.paper.configuration.GlobalConfiguration;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.galemc.gale.configuration.GaleGlobalConfiguration;
import org.galemc.gale.configuration.GaleWorldConfiguration;
import org.dreeam.leaf.config.ConfigCategory;
import org.dreeam.leaf.config.modules.async.AsyncMobSpawning;
import org.dreeam.leaf.config.modules.async.AsyncPathfinding;
import org.dreeam.leaf.config.modules.async.AsyncPlayerDataSave;
import org.dreeam.leaf.config.modules.async.MultithreadedTracker;
import org.dreeam.leaf.config.modules.opt.IdleMemory;
import org.dreeam.leaf.config.modules.opt.OptimizePlayerMovementProcessing;
import org.dreeam.leaf.config.modules.opt.SkipMapItemDataUpdates;
import org.dreeam.leaf.config.modules.opt.SleepingBlockEntity;
import org.spigotmc.SpigotConfig;

/** Reads the settings of the running server. */
final class ServerSettingsCollector {

    private ServerSettingsCollector() {
    }

    static ServerSettings collect() {
        final MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        return new ServerSettings(
            explicitWorkerThreads(),
            Bukkit.getViewDistance(),
            Bukkit.getSimulationDistance(),
            Bukkit.getMaxPlayers(),
            behindProxy(),
            server.getCompressionThreshold(),
            proxyLocation(server),
            recommendedOptionsOff(),
            vanillaDeviations(server),
            IdleMemory.enabled);
    }

    private static OptionalInt explicitWorkerThreads() {
        final int configured = GlobalConfiguration.get().chunkSystem.workerThreads;
        return configured > 0 ? OptionalInt.of(configured) : OptionalInt.empty();
    }

    private static boolean behindProxy() {
        return GlobalConfiguration.get().proxies.velocity.enabled || SpigotConfig.bungee;
    }

    /** The players' connections end at the proxy; without players, the address the server listens on tells at most that it is local. */
    private static ProxyLocation proxyLocation(final MinecraftServer server) {
        int onThisHost = 0;
        int onOtherHosts = 0;
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isOnThisHost(player.connection.connection.channel.remoteAddress())) {
                onThisHost++;
            } else {
                onOtherHosts++;
            }
        }
        return ProxyLocation.fromPeers(onThisHost, onOtherHosts)
            .orElse(listensOnLoopbackOnly(server.getLocalIp()) ? ProxyLocation.SAME_HOST : ProxyLocation.UNKNOWN);
    }

    /** A peer that is not an internet address, such as a Unix domain socket, is on this host. */
    private static boolean isOnThisHost(final SocketAddress peer) {
        if (!(peer instanceof InetSocketAddress inet) || inet.getAddress() == null) {
            return true;
        }
        try {
            return inet.getAddress().isLoopbackAddress() || NetworkInterface.getByInetAddress(inet.getAddress()) != null;
        } catch (final SocketException e) {
            return false;
        }
    }

    private static boolean listensOnLoopbackOnly(final String serverIp) {
        try {
            return !serverIp.isEmpty() && InetAddress.getByName(serverIp).isLoopbackAddress();
        } catch (final UnknownHostException e) {
            return false;
        }
    }

    /** Kindling switches these on in a fresh {@code leaf-global.yml}; a file from before keeps the old values. */
    private static List<String> recommendedOptionsOff() {
        final List<String> off = new ArrayList<>();
        addIfOff(off, MultithreadedTracker.enabled, ConfigCategory.ASYNC.basePath() + ".async-entity-tracker.enabled");
        addIfOff(off, AsyncPlayerDataSave.enabled, ConfigCategory.ASYNC.basePath() + ".async-playerdata-save.enabled");
        addIfOff(off, SleepingBlockEntity.enabled, ConfigCategory.PERF.basePath() + ".sleeping-block-entity");
        return off;
    }

    /** Gale's world settings can differ per world, so every world is read and a value that differs shows up on its own. */
    private static List<VanillaParity.Deviation> vanillaDeviations(final MinecraftServer server) {
        final Set<VanillaParity.Deviation> deviations = new LinkedHashSet<>();
        for (final ServerLevel level : server.getAllLevels()) {
            deviations.addAll(VanillaParity.deviations(runningValues(level.galeConfig())));
        }
        return List.copyOf(deviations);
    }

    private static VanillaParity.Values runningValues(final GaleWorldConfiguration world) {
        final var projectileChunkLoads = world.smallOptimizations.maxProjectileChunkLoads;
        final var reducedIntervals = world.smallOptimizations.reducedIntervals;
        return new VanillaParity.Values(
            projectileChunkLoads.perTick,
            projectileChunkLoads.perProjectile.max,
            reducedIntervals.checkStuckInWall,
            GaleGlobalConfiguration.get().smallOptimizations.reducedIntervals.updateEntityLineOfSight,
            reducedIntervals.villagerItemRepickup,
            world.gameplayMechanics.entityWakeUpDurationRatioStandardDeviation,
            SkipMapItemDataUpdates.enabled,
            OptimizePlayerMovementProcessing.enabled,
            AsyncPathfinding.enabled,
            AsyncMobSpawning.enabled);
    }

    private static void addIfOff(final List<String> off, final boolean enabled, final String configKey) {
        if (!enabled) {
            off.add(configKey);
        }
    }
}
