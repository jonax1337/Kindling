package net.kindlingmc.doctor;

import java.util.Optional;

/** Checks of server settings against the machine. */
final class ServerSettingChecks {

    private static final int VIEW_DISTANCE_BASE = 6;
    private static final int VIEW_DISTANCE_PER_PROCESSOR = 2;
    private static final int SIMULATION_DISTANCE_BASE = 4;
    private static final int NO_COMPRESSION = -1;

    private ServerSettingChecks() {
    }

    /**
     * The chunk worker count Kindling picks when {@code worker-threads} stays at -1: five eighths of the processors,
     * at most five, and never fewer than Paper's quarter. Mirrors the patch "Measured default chunk worker count".
     */
    static int measuredDefaultWorkerThreads(final int processors) {
        final int enoughForFastPlayers = 5;
        return Math.max(1, Math.max(Math.min(enoughForFastPlayers, processors * 5 / 8), processors / 4));
    }

    static Optional<Finding> workerThreads(final MachineFacts machine, final ServerSettings server) {
        final int measuredDefault = measuredDefaultWorkerThreads(machine.availableProcessors());
        if (server.workerThreads().isEmpty() || server.workerThreads().getAsInt() >= measuredDefault) {
            return Optional.of(Finding.ok("Chunk worker threads: " + server.workerThreads().orElse(measuredDefault)
                + " (Kindling's measured default is " + measuredDefault + ")"));
        }
        return Optional.of(Finding.warn(
            "Chunk worker threads are set to " + server.workerThreads().getAsInt() + ", below Kindling's measured default of "
                + measuredDefault + " for " + machine.availableProcessors() + " processors: "
                + "with 2 workers an elytra flight spent 92 % of its time over void chunks, with 5 workers 0 %",
            "Set chunk-system.worker-threads in config/paper-global.yml to -1 (or at least " + measuredDefault + "); "
                + "keep it lower only when several servers generate chunks on this machine at the same time",
            Basis.KINDLING_MEASUREMENT));
    }

    static Optional<Finding> viewDistance(final MachineFacts machine, final ServerSettings server) {
        final int processors = machine.usableProcessors();
        final int viewLimit = VIEW_DISTANCE_BASE + VIEW_DISTANCE_PER_PROCESSOR * processors;
        final int simulationLimit = SIMULATION_DISTANCE_BASE + processors;
        if (server.viewDistance() <= viewLimit && server.simulationDistance() <= simulationLimit) {
            return Optional.of(Finding.ok("View distance " + server.viewDistance() + " and simulation distance "
                + server.simulationDistance() + " fit " + processors + " processors"));
        }
        return Optional.of(Finding.warn(
            "View distance " + server.viewDistance() + " and simulation distance " + server.simulationDistance()
                + " are high for " + processors + " processors: the chunks to send and tick grow with the square of the distance",
            "Keep view-distance at " + viewLimit + " or less and simulation-distance at " + simulationLimit + " or less, or add processors",
            Basis.GENERAL_ADVICE));
    }

    static Optional<Finding> proxyCompression(final ServerSettings server) {
        if (!server.behindProxy()) {
            return Optional.empty();
        }
        if (server.compressionThreshold() == NO_COMPRESSION) {
            return Optional.of(Finding.ok("Behind a proxy with network compression off"));
        }
        return Optional.of(Finding.warn(
            "The server sits behind a proxy and compresses its packets (network-compression-threshold="
                + server.compressionThreshold() + "): the proxy compresses for the player already, so this is wasted work on the link between proxy and server",
            "Set network-compression-threshold=-1 in server.properties and restart (applies when the proxy runs on the same machine or network)",
            Basis.GENERAL_ADVICE));
    }
}
