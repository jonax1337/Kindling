package net.kindlingmc.bench;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The warm-up of a mixed run: at least {@code warmup-seconds}, then until no chunk has been generated for
 * {@code warmup-quiet-seconds}, but not longer than {@code warmup-max-seconds}. A fixed time would measure a flight that
 * starts while the world around the players is still being generated on one machine and finished on another.
 */
final class Warmup {

    private static final int TICKS_PER_SECOND = 20;
    private static final double MILLIS_PER_SECOND = 1000.0;

    private final Plugin plugin;
    private final Scenario scenario;
    private final Runnable whenDone;
    private final GenerationWatch generation = new GenerationWatch();
    private final long startMillis = System.currentTimeMillis();
    private final int startTick = Bukkit.getCurrentTick();
    private int lengthTicks;
    private long lengthMillis;
    private boolean settled;

    /** @param whenDone runs once the warm-up is over */
    Warmup(Plugin plugin, Scenario scenario, Runnable whenDone) {
        this.plugin = plugin;
        this.scenario = scenario;
        this.whenDone = whenDone;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(generation, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 1, 1);
    }

    private void check(BukkitTask task) {
        int elapsedTicks = Bukkit.getCurrentTick() - startTick;
        if (elapsedTicks < scenario.warmupSeconds() * TICKS_PER_SECOND) {
            return;
        }
        boolean quiet = generation.quietTicks() >= scenario.warmupQuietSeconds() * TICKS_PER_SECOND;
        if (quiet || elapsedTicks >= scenario.warmupMaxSeconds() * TICKS_PER_SECOND) {
            task.cancel();
            HandlerList.unregisterAll(generation);
            lengthTicks = elapsedTicks;
            lengthMillis = System.currentTimeMillis() - startMillis;
            settled = quiet;
            whenDone.run();
        }
    }

    /** How long the warm-up took, in server time and wall time, and whether generation had calmed down by its end. */
    Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("warmup_seconds", (double) lengthTicks / TICKS_PER_SECOND);
        summary.put("warmup_wall_seconds", Math.round(lengthMillis / MILLIS_PER_SECOND * 10) / 10.0);
        summary.put("warmup_settled", settled);
        return summary;
    }
}
