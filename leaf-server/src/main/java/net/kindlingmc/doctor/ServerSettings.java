package net.kindlingmc.doctor;

import java.util.List;
import java.util.OptionalInt;

/**
 * The server settings that cost performance when they do not fit the machine.
 *
 * @param workerThreads          chunk worker threads that were set explicitly; empty when Kindling's default applies
 * @param behindProxy            whether Velocity or BungeeCord forwarding is switched on
 * @param compressionThreshold   {@code network-compression-threshold}, -1 for none
 * @param proxyLocation          where the proxy runs; meaningful when {@code behindProxy} is set
 * @param recommendedOptionsOff  the config keys of Kindling's recommended options that {@code leaf-global.yml} has switched off
 * @param vanillaDeviations      the performance settings whose running value differs from vanilla, in all loaded worlds
 * @param idleMemory             whether the server gives memory back while nobody is online
 */
public record ServerSettings(
    OptionalInt workerThreads,
    int viewDistance,
    int simulationDistance,
    int maxPlayers,
    boolean behindProxy,
    int compressionThreshold,
    ProxyLocation proxyLocation,
    List<String> recommendedOptionsOff,
    List<VanillaParity.Deviation> vanillaDeviations,
    boolean idleMemory
) {
}
