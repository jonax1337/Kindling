package net.kindlingmc.stress;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

/**
 * One stress test, from the first order to the deleted world. While it runs, every tick moves each load toward its
 * target and once a second (of wall time, however slow the ticks) the monitor takes the figures. It stops when asked,
 * after {@value #MAX_MINUTES} minutes or when the server is overloaded; then the fake players leave, the mobs go, the
 * real players go back to where they came from, and once the world is empty it is unloaded and deleted.
 */
final class StressSession {

    private static final int MAX_MINUTES = 15;
    private static final long MAX_NANOS = Duration.ofMinutes(MAX_MINUTES).toNanos();
    private static final long SAMPLE_NANOS = Duration.ofSeconds(1).toNanos();
    /**
     * The tick time is the mean of the last 5 seconds, which at first still holds the tick that created the world; the
     * report and the overload check count only what comes after.
     */
    private static final long SETTLE_NANOS = Duration.ofSeconds(5).toNanos();
    /** How often a stopped test tries again to send players back and unload its world, and when it gives up. */
    private static final int RETRY_TICKS = 20;
    private static final long GIVE_UP_TICKS = 30 * 20L;
    private static final long RUNNING = -1;
    private static final long FINISHED = Long.MIN_VALUE;

    private final World world;
    private final KeptChunks kept;
    private final FakePlayerLoad players;
    private final MobLoad mobs;
    private final BaseLoad bases;
    private final LoadMonitor monitor;
    private final CompletableFuture<Void> deleted = new CompletableFuture<>();
    private final long startNanos = System.nanoTime();
    private long lastSampleNanos = this.startNanos;
    private long endNanos;
    private long ticks;
    private long closingTicks = RUNNING;

    StressSession(final World world) {
        this.world = world;
        this.kept = new KeptChunks(world);
        this.players = new FakePlayerLoad(((CraftWorld) world).getHandle());
        this.mobs = new MobLoad(world, this.kept);
        this.bases = new BaseLoad(world, this.kept);
        this.monitor = new LoadMonitor(world);
    }

    void apply(final StressOrder order) {
        switch (order) {
            case StressOrder.Players wanted -> this.players.aimAt(wanted.count(), wanted.motion());
            case StressOrder.Mobs wanted -> this.mobs.aimAt(wanted.count());
            case StressOrder.Bases wanted -> this.bases.aimAt(wanted.count());
            default -> throw new IllegalArgumentException("Not a load: " + order);
        }
    }

    /** Takes the player into the test world, to the centre, and remembers where to bring it back to. */
    void bringIn(final Player player) {
        if (!player.getWorld().equals(this.world)) {
            ReturnTrip.takeInto(player, new Location(this.world, 0.5, this.world.getHighestBlockYAt(0, 0) + 1, 0.5));
        }
    }

    void tick() {
        if (this.isFinished()) {
            return;
        }
        if (this.isClosing()) {
            this.closeTick();
            return;
        }
        this.ticks++;
        this.players.tick();
        this.mobs.tick();
        this.bases.tick(this.ticks);
        final long now = System.nanoTime();
        if (now - this.lastSampleNanos >= SAMPLE_NANOS) {
            final long sinceLastSample = now - this.lastSampleNanos;
            this.lastSampleNanos = now;
            this.monitor.sample(this.load());
            if (now - this.startNanos >= SETTLE_NANOS) {
                this.monitor.record(Duration.ofNanos(sinceLastSample));
            }
            this.stopIfDue(now);
        }
    }

    private void stopIfDue(final long now) {
        if (this.monitor.isOverloaded()) {
            this.stop(String.format("the server stayed above %.0f mspt for %d seconds", OverloadWatch.LIMIT_MSPT, OverloadWatch.LIMIT.toSeconds()), List.of());
        } else if (now - this.startNanos >= MAX_NANOS) {
            this.stop(MAX_MINUTES + " minutes are up", List.of());
        }
    }

    /**
     * Ends the test and sends its report to every real player in the test world, to {@code alsoTell} and to the server
     * log, unless the console is among them anyway.
     */
    void stop(final String reason, final Collection<? extends CommandSender> alsoTell) {
        this.endNanos = System.nanoTime();
        final String report = this.report(reason);
        final Set<CommandSender> readers = new LinkedHashSet<>(this.realPlayers());
        readers.addAll(alsoTell);
        for (final CommandSender reader : readers) {
            reader.sendPlainMessage(report);
        }
        if (readers.stream().noneMatch(ConsoleCommandSender.class::isInstance)) {
            MinecraftServer.LOGGER.info(report);
        }
        this.removeLoad();
        this.sendPlayersBack();
        this.closingTicks = 0;
    }

    /** Each part of the load goes even if removing an earlier part failed. */
    private void removeLoad() {
        try {
            this.players.removeAll();
        } finally {
            try {
                this.mobs.removeAll();
            } finally {
                this.kept.releaseAll();
                this.monitor.hide();
            }
        }
    }

    /**
     * Fake players are removed at the next network tick, so the world is unloaded only once it is really empty. A player
     * that cannot be sent back (dead, riding, held by a plugin) or a plugin that keeps the world loaded makes the test
     * give up after a while, leaving the world loaded until the server restarts.
     */
    private void closeTick() {
        this.closingTicks++;
        if (this.closingTicks > GIVE_UP_TICKS) {
            this.giveUp();
        } else if (this.closingTicks % RETRY_TICKS == 1) {
            this.tryToClose();
        }
    }

    private void tryToClose() {
        if (!this.realPlayers().isEmpty()) {
            this.sendPlayersBack();
        } else if (this.world.getPlayers().isEmpty() && StressWorld.discard(this.world, this.deleted)) {
            this.closingTicks = FINISHED;
        }
    }

    private void giveUp() {
        MinecraftServer.LOGGER.warn("The stress test world {} could not be unloaded; it is deleted at the first test after a restart", StressWorld.folder());
        this.deleted.completeExceptionally(new IllegalStateException("The stress test world could not be unloaded"));
        this.closingTicks = FINISHED;
    }

    /**
     * Leaves after a failure: removes what it can of the load and sends the players back; the world stays loaded until
     * the next test removes it.
     */
    void abandon() {
        try {
            this.removeLoad();
            this.sendPlayersBack();
        } finally {
            this.deleted.completeExceptionally(new IllegalStateException("The stress test failed"));
            this.closingTicks = FINISHED;
        }
    }

    private void sendPlayersBack() {
        for (final Player player : this.realPlayers()) {
            ReturnTrip.sendBack(player);
        }
    }

    private Set<Player> realPlayers() {
        final Set<Player> real = new LinkedHashSet<>();
        for (final Player player : this.world.getPlayers()) {
            if (!FakeConnection.drives(player)) {
                real.add(player);
            }
        }
        return real;
    }

    boolean isClosing() {
        return this.closingTicks != RUNNING;
    }

    /** Whether the world is gone or given up; {@link #deleted()} then tells whether its folder is deleted too. */
    boolean isFinished() {
        return this.closingTicks == FINISHED;
    }

    CompletableFuture<Void> deleted() {
        return this.deleted;
    }

    String status() {
        return String.format("Stress test, %s of %d:00: %s. %s Stop it with /kindling stress stop.",
            clock(System.nanoTime() - this.startNanos), MAX_MINUTES, this.load(), this.monitor.now());
    }

    private String report(final String reason) {
        return String.format("Stress test over (%s) after %s, with %s at the end. %s",
            reason, clock(this.endNanos - this.startNanos), this.load(), this.monitor.summary());
    }

    private String load() {
        return amount(this.players.count(), this.players.target(), "players " + this.players.motion().name().toLowerCase(Locale.ROOT))
            + ", " + amount(this.mobs.count(), this.mobs.target(), "mobs")
            + ", " + amount(this.bases.count(), this.bases.target(), "bases");
    }

    private static String amount(final int count, final int target, final String what) {
        return (count == target ? String.valueOf(count) : count + "/" + target) + " " + what;
    }

    private static String clock(final long nanos) {
        final long seconds = Duration.ofNanos(nanos).toSeconds();
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
