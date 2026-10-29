package net.kindlingmc.stress;

import java.time.Duration;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Watches the server while a stress test runs: once a second it reads the tick time and TPS, shows them on a boss bar
 * to every real player in the test world, keeps the figures for the report and notices an overload.
 */
final class LoadMonitor {

    static final int SAMPLE_TICKS = 20;

    private static final double TICK_BUDGET_MSPT = 50;
    private static final double BUSY_MSPT = 35;

    private final World world;
    private final TickStats stats = new TickStats();
    private final OverloadWatch overload = new OverloadWatch();
    private final BossBar bar = BossBar.bossBar(Component.text("Stress test starting"), 0, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private double mspt;
    private double tps;
    private boolean overloaded;

    LoadMonitor(final World world) {
        this.world = world;
    }

    /** Takes this second's figures and shows them with the load that caused them. */
    void sample(final String load) {
        this.mspt = Bukkit.getAverageTickTime();
        this.tps = Bukkit.getTPSIncluding5SecondAverage()[0];
        this.bar.name(Component.text(String.format("%s · %.1f mspt · %.1f TPS", load, this.mspt, this.tps)));
        this.bar.progress((float) Math.min(1, this.mspt / TICK_BUDGET_MSPT));
        this.bar.color(this.colour());
        this.showToViewers();
    }

    /** Counts the last sample, which stands for the {@code elapsed} time since the one before, for the report and the overload check. */
    void record(final Duration elapsed) {
        this.stats.add(this.mspt, this.tps);
        this.overloaded = this.overload.isOverloaded(this.mspt, elapsed);
    }

    private BossBar.Color colour() {
        if (this.mspt < BUSY_MSPT) {
            return BossBar.Color.GREEN;
        }
        return this.mspt < TICK_BUDGET_MSPT ? BossBar.Color.YELLOW : BossBar.Color.RED;
    }

    private void showToViewers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(this.world) && !FakeConnection.drives(player)) {
                player.showBossBar(this.bar);
            } else {
                player.hideBossBar(this.bar);
            }
        }
    }

    void hide() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(this.bar);
        }
    }

    boolean isOverloaded() {
        return this.overloaded;
    }

    String now() {
        return String.format("Now %.1f mspt, %.1f TPS.", this.mspt, this.tps);
    }

    String summary() {
        if (this.stats.isEmpty()) {
            return "No tick times were taken.";
        }
        return String.format("Tick time: %.1f mspt on average, %.1f at the peak. Lowest TPS: %.1f.",
            this.stats.averageMspt(), this.stats.peakMspt(), this.stats.lowestTps());
    }
}
