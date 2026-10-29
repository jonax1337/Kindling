package net.kindlingmc.stress;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code /kindling stress players <n> [stand|fly] | mobs <n> | bases <n> | stop | status}: puts the server under load
 * that one can watch in game. The first load order creates a fresh world for the test and takes the player there;
 * {@code stop} ends the test and deletes the world. All of it runs on the main thread, as one of the server's tickables.
 */
public final class StressTest {

    public static final String USAGE = "/kindling stress players <0-" + StressOrder.MAX_PLAYERS + "> [stand|fly] | mobs <0-"
        + StressOrder.MAX_MOBS + "> | bases <0-" + StressOrder.MAX_BASES + "> | stop | status";

    private static final List<String> ACTIONS = List.of("players", "mobs", "bases", "stop", "status");
    private static final String NOT_RUNNING = "No stress test is running. Start one with " + USAGE;

    private static @Nullable StressSession session;
    private static CompletableFuture<Void> cleanup = CompletableFuture.completedFuture(null);

    private StressTest() {
    }

    public static void install(final MinecraftServer server) {
        server.addTickable(StressTest::tick);
        ReturnTrip.install();
    }

    /** Carries out the arguments after {@code stress} and answers what the sender should read. */
    public static String order(final CommandSender sender, final String[] args) {
        final StressOrder order;
        try {
            order = StressOrder.parse(args);
        } catch (final IllegalArgumentException e) {
            return e.getMessage() + "\nUsage: " + USAGE;
        }
        return switch (order) {
            case StressOrder.Status ignored -> session == null || session.isClosing() ? NOT_RUNNING : session.status();
            case StressOrder.Stop ignored -> stop(sender);
            case StressOrder load -> load(sender, load);
        };
    }

    private static String stop(final CommandSender sender) {
        if (session == null || session.isClosing()) {
            return NOT_RUNNING;
        }
        try {
            session.stop("stopped by " + sender.getName(), List.of(sender));
        } catch (final RuntimeException e) {
            endAfterFailure(e);
            return "The test failed while stopping; the server log has the details.";
        }
        return "The test world is being deleted.";
    }

    private static String load(final CommandSender sender, final StressOrder order) {
        if (session == null) {
            final Optional<String> refusal = refusalToStart();
            if (refusal.isPresent()) {
                return refusal.get();
            }
            try {
                session = new StressSession(StressWorld.create());
            } catch (final RuntimeException e) {
                MinecraftServer.LOGGER.error("Could not create the stress test world", e);
                return "Could not create the test world: " + e.getMessage();
            }
        }
        if (session.isClosing()) {
            return "The last stress test is still shutting down; try again in a moment.";
        }
        session.apply(order);
        if (sender instanceof Player player) {
            session.bringIn(player);
        }
        return session.status();
    }

    @SuppressWarnings("removal") // Leaf still offers parallel world ticking; the check goes when the option does.
    private static Optional<String> refusalToStart() {
        if (org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking.enabled) {
            return Optional.of("The stress test does not run with parallel world ticking: its fake players are driven from the main thread.");
        }
        if (!cleanup.isDone()) {
            return Optional.of("The world of the last stress test is still being deleted; try again in a moment.");
        }
        final World leftLoaded = Bukkit.getWorld(StressWorld.KEY);
        if (leftLoaded != null) {
            return Optional.of(discardLeftLoaded(leftLoaded));
        }
        if (StressWorld.hasLegacyFolder()) {
            return Optional.of("A world folder named kindling_stress exists; the test would take it over and delete it. Move it away first.");
        }
        if (StressWorld.isLeftOver()) {
            cleanup = StressWorld.deleteInBackground().whenComplete((done, failure) -> logCleanup(failure));
            return Optional.of("Deleting the world of a test that ended with the server; try again in a moment.");
        }
        return Optional.empty();
    }

    /** A test that gave up or failed left its world loaded; once nobody is in it any more, it can go. */
    private static String discardLeftLoaded(final World world) {
        final CompletableFuture<Void> deleted = new CompletableFuture<>();
        if (world.getPlayers().isEmpty() && StressWorld.discard(world, deleted)) {
            cleanup = deleted.whenComplete((done, failure) -> logCleanup(failure));
            return "Removing the world of the last stress test; try again in a moment.";
        }
        return "The world of the last stress test is still loaded, with players in it or kept by a plugin.";
    }

    private static void tick() {
        if (session == null) {
            return;
        }
        try {
            session.tick();
        } catch (final RuntimeException e) {
            // An exception out of a tickable would crash the server; a failed test only ends.
            endAfterFailure(e);
        }
        if (session.isFinished()) {
            cleanup = session.deleted().whenComplete((done, failure) -> logCleanup(failure));
            session = null;
        }
    }

    /** Stops the failed test as far as it still can; if even that fails, removes what it can of the load. */
    private static void endAfterFailure(final RuntimeException cause) {
        MinecraftServer.LOGGER.error("The stress test failed and stops", cause);
        if (!session.isClosing()) {
            try {
                session.stop("it failed: " + cause.getMessage(), List.of());
                return;
            } catch (final RuntimeException e) {
                MinecraftServer.LOGGER.error("The failed stress test could not be stopped cleanly", e);
            }
        }
        try {
            session.abandon();
        } catch (final RuntimeException e) {
            MinecraftServer.LOGGER.error("The failed stress test could not remove all of its load", e);
        }
    }

    private static void logCleanup(final @Nullable Throwable failure) {
        if (failure != null) {
            MinecraftServer.LOGGER.warn("The stress test world was not deleted; it is deleted at the first test after a restart", failure);
        }
    }

    public static List<String> tabComplete(final String[] args) {
        if (args.length == 1) {
            return startingWith(ACTIONS, args[0]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("players")) {
            return startingWith(List.of(PlayerMotion.STANDING.keyword(), PlayerMotion.FLYING.keyword()), args[2]);
        }
        return List.of();
    }

    private static List<String> startingWith(final List<String> words, final String start) {
        final String prefix = start.toLowerCase(Locale.ROOT);
        return words.stream().filter(word -> word.startsWith(prefix)).toList();
    }
}
