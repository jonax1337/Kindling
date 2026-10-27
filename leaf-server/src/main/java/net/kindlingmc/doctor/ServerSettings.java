package net.kindlingmc.doctor;

import java.util.OptionalInt;

/**
 * The server settings that cost performance when they do not fit the machine.
 *
 * @param workerThreads         chunk worker threads that were set explicitly; empty when Kindling's default applies
 * @param behindProxy           whether Velocity or BungeeCord forwarding is switched on
 * @param compressionThreshold  {@code network-compression-threshold}, -1 for none
 */
public record ServerSettings(
    OptionalInt workerThreads,
    int viewDistance,
    int simulationDistance,
    int maxPlayers,
    boolean behindProxy,
    int compressionThreshold
) {
}
