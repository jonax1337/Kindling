package net.kindlingmc.doctor;

import java.util.Optional;
import net.kindlingmc.chunk.ChunkWorkers;

/** Checks of server settings against the machine. */
final class ServerSettingChecks {

    /** Both distances default to 10; a default server must not be warned about on a small machine. */
    private static final int VANILLA_DISTANCE = 10;
    /** The elytra test ran on eight processors; there, view distance 10 was already marginal for fast fliers. */
    private static final int MEASURED_PROCESSORS = 8;
    private static final int VIEW_DISTANCE_BASE = 6;
    private static final int VIEW_DISTANCE_PER_PROCESSOR = 2;
    private static final int SIMULATION_DISTANCE_BASE = 4;
    private static final int NO_COMPRESSION = -1;
    private static final String MEASURED_VIEW_DISTANCE_NOTE =
        "Kindling measured view distance 10 on 8 processors as already marginal for fast fliers (one elytra flier needs about 45 new chunks per second)";
    private static final String RULE_OF_THUMB_NOTE =
        "the chunks to send and tick grow with the square of the distance; these limits are a rule of thumb that Kindling did not measure";

    private ServerSettingChecks() {
    }

    static Optional<Finding> workerThreads(final MachineFacts machine, final ServerSettings server) {
        final int measuredDefault = ChunkWorkers.defaultFor(machine.availableProcessors());
        if (server.workerThreads().isEmpty() || server.workerThreads().getAsInt() >= measuredDefault) {
            return Optional.of(Finding.ok("Chunk worker threads: " + server.workerThreads().orElse(measuredDefault)
                + " (Kindling's measured default is " + measuredDefault + ")"));
        }
        return Optional.of(Finding.warn(
            "Chunk worker threads are set to " + server.workerThreads().getAsInt() + ", below Kindling's measured default of "
                + measuredDefault + " for " + machine.availableProcessors() + " processors: in the elytra test with 2 workers a flight spent "
                + "92 % of its time over void chunks, with 5 workers 9 %, and with 5 workers plus directional chunk loading 0 %",
            "Set chunk-system.worker-threads in config/paper-global.yml to -1 (or at least " + measuredDefault + ") and keep "
                + "performance.directional-chunk-loading.enabled on; keep the workers lower only when several servers generate chunks on this machine at the same time",
            Basis.KINDLING_MEASUREMENT));
    }

    static Optional<Finding> viewDistance(final MachineFacts machine, final ServerSettings server) {
        final int processors = machine.usableProcessors();
        final int viewLimit = viewDistanceLimit(processors);
        final int simulationLimit = Math.max(VANILLA_DISTANCE, SIMULATION_DISTANCE_BASE + processors);
        if (server.viewDistance() <= viewLimit && server.simulationDistance() <= simulationLimit) {
            return Optional.of(Finding.ok("View distance " + server.viewDistance() + " and simulation distance "
                + server.simulationDistance() + " are within the limits for " + processors + " processors"));
        }
        final boolean measured = server.viewDistance() > viewLimit && processors <= MEASURED_PROCESSORS;
        return Optional.of(Finding.warn(
            "View distance " + server.viewDistance() + " and simulation distance " + server.simulationDistance() + " are high for "
                + processors + " processors: " + (measured ? MEASURED_VIEW_DISTANCE_NOTE : RULE_OF_THUMB_NOTE),
            "Keep view-distance at " + viewLimit + " or less and simulation-distance at " + simulationLimit + " or less, or add processors"
                + (measured ? "; a world pregenerated around where players fly spares the generation a bigger view distance would need" : ""),
            measured ? Basis.KINDLING_MEASUREMENT : Basis.GENERAL_ADVICE));
    }

    /** Up to the measured processor count the measured 10 is the limit; beyond it a rule of thumb grows with the processors. */
    private static int viewDistanceLimit(final int processors) {
        return processors <= MEASURED_PROCESSORS
            ? VANILLA_DISTANCE
            : Math.max(VANILLA_DISTANCE, VIEW_DISTANCE_BASE + VIEW_DISTANCE_PER_PROCESSOR * processors);
    }

    static Optional<Finding> proxyCompression(final ServerSettings server) {
        if (!server.behindProxy()) {
            return Optional.empty();
        }
        if (server.compressionThreshold() == NO_COMPRESSION) {
            return Optional.of(Finding.ok("Behind a proxy with network compression off"));
        }
        final String what = "The server sits behind a proxy and compresses its packets (network-compression-threshold="
            + server.compressionThreshold() + ")";
        if (server.proxyLocation() == ProxyLocation.OTHER_HOST) {
            return Optional.of(Finding.info(
                what + ": the proxy runs on another machine, where compression saves bandwidth on the link at the price of CPU time on both ends",
                "Set network-compression-threshold=-1 in server.properties only when the link between proxy and server is fast, for example in one data center",
                Basis.GENERAL_ADVICE));
        }
        return Optional.of(Finding.warn(
            what + (server.proxyLocation() == ProxyLocation.SAME_HOST ? ": the proxy runs on this machine" : ": the proxy may run on this machine")
                + " and compresses for the player already, so this is wasted work on the link between proxy and server",
            "Set network-compression-threshold=-1 in server.properties and restart",
            Basis.GENERAL_ADVICE));
    }

    static Optional<Finding> recommendedOptions(final ServerSettings server) {
        if (server.recommendedOptionsOff().isEmpty()) {
            return Optional.of(Finding.ok("Kindling's recommended options (async entity tracker, async pathfinding, async player data saving, "
                + "sleeping block entities) are on"));
        }
        return Optional.of(Finding.info(
            "leaf-global.yml has " + String.join(", ", server.recommendedOptionsOff()) + " off: Kindling switches them on by default, "
                + "but the defaults only apply to a fresh file, so a file from before keeps its old values",
            "Set them to true in config/leaf-global.yml and restart, unless they are off on purpose; Kindling measured them faster with vanilla behaviour kept",
            Basis.KINDLING_MEASUREMENT));
    }
}
