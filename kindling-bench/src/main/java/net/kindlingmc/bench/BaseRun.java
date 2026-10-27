package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A run in plots built for counting: every fake player hovers among its {@link Plot}s ({@link PlotGrid}), which the
 * scenario's {@link Layout} fills: survival bases with hoppers, furnaces, redstone and a workshop, the cases where
 * sleeping block entities can differ, or halls of villagers. Once world generation around the players has calmed
 * down, all contraptions are built in the same tick, run through the warm-up and are then measured: tick times, and
 * how far every contraption got. Nothing in a layout depends on chance, so two runs, and two server builds that keep
 * vanilla behaviour, must report the same counters; a difference means behaviour changed.
 */
final class BaseRun {

    private static final int TICKS_PER_SECOND = 20;
    private static final int NOON = 6000;
    private static final int QUIET_TICKS = 5 * TICKS_PER_SECOND;
    private static final int SETTLE_LIMIT_TICKS = 180 * TICKS_PER_SECOND;
    private static final List<String> STILL_WORLD_RULES =
            List.of("advance_time", "advance_weather", "spawn_mobs", "spawn_patrols", "spawn_wandering_traders");

    private final Plugin plugin;
    private final Scenario scenario;
    private final MeasuredPhase phase;
    private final FakePlayers fakePlayers;
    private final RunResult result = new RunResult();
    private final List<Plot> plots = new ArrayList<>();
    private final GenerationWatch generation = new GenerationWatch();
    private Layout layout;
    private World world;
    private int startTick;
    private int clearedPlots;
    private int clockTicks;

    BaseRun(Plugin plugin, Scenario scenario, MeasuredPhase phase, FakePlayers fakePlayers) {
        this.plugin = plugin;
        this.scenario = scenario;
        this.phase = phase;
        this.fakePlayers = fakePlayers;
    }

    void start() {
        world = Bukkit.getWorlds().getFirst();
        holdStill(world);
        result.put("scenario", scenario);
        result.put("options", ServerOptions.read(plugin.getServer().getWorldContainer().toPath()));
        Bukkit.getPluginManager().registerEvents(generation, plugin);
        PlotGrid grid = new PlotGrid(world, scenario.plotsPerPlayer());
        for (int i = 0; i < scenario.players(); i++) {
            plots.addAll(grid.plotsOf(i));
            fakePlayers.joinFlying("bench" + i, grid.standingSpot(i), GameMode.CREATIVE);
        }
        startTick = Bukkit.getCurrentTick();
        Bukkit.getScheduler().runTaskTimer(plugin, this::prepare, 1, 1);
    }

    /** Fixed noon, clear sky and no natural spawning: only the bases change the world. */
    private static void holdStill(World world) {
        world.setTime(NOON);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        STILL_WORLD_RULES.forEach(rule -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule " + rule + " false"));
    }

    /**
     * Clears one plot per tick, since each loads and may generate {@value Plot#CHUNKS} chunks; then builds the bases as
     * soon as no chunk has been generated for a while and every plot ticks, or after the limit. Generation still
     * running would put its load into the measurement.
     */
    private void prepare(BukkitTask task) {
        if (clearedPlots < plots.size()) {
            plots.get(clearedPlots++).clear();
            return;
        }
        boolean settled = generation.quietTicks() >= QUIET_TICKS && plots.stream().allMatch(Plot::isTicking);
        int waited = Bukkit.getCurrentTick() - startTick;
        if (settled || waited >= SETTLE_LIMIT_TICKS) {
            task.cancel();
            HandlerList.unregisterAll(generation);
            result.put("settled", settled);
            result.put("prepare_seconds", (double) waited / TICKS_PER_SECOND);
            build();
        }
    }

    private void build() {
        removeStrayEntities();
        layout = Layout.of(scenario, plots);
        layout.contraptions().forEach(Contraption::build);
        for (Listener listener : layout.listeners(Bukkit.getCurrentTick())) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickClocks, 1, 1);
        afterTicks((long) scenario.warmupSeconds() * TICKS_PER_SECOND, this::measure);
    }

    /**
     * The animals world generation placed and the items it dropped (mineshaft rails and torches over air pop off)
     * differ from run to run; only the bases are meant to load the server. Minecarts stay: discarding a chest minecart
     * spills its loot as items.
     */
    private void removeStrayEntities() {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Mob || entity instanceof Item) {
                entity.remove();
            }
        }
    }

    /**
     * Steps each clock every {@value Clocked#STEP_TICKS} ticks, clock after clock one tick apart, as clocks on a server
     * are not in step; all of them at once would make every tenth tick a spike.
     */
    private void tickClocks() {
        clockTicks++;
        List<Clocked> clocks = layout.clocks();
        for (int clock = 0; clock < clocks.size(); clock++) {
            if ((clockTicks + clock) % Clocked.STEP_TICKS == 0) {
                clocks.get(clock).clockStep();
            }
        }
    }

    /** Reading the counters costs a tick of its own, so the tick times are recorded from the next tick on. */
    private void measure() {
        Map<String, Long> before = layout.totals();
        long measuredTicks = (long) scenario.steadySeconds() * TICKS_PER_SECOND;
        afterTicks(1, phase::start);
        afterTicks(1 + measuredTicks, () -> finish(before));
    }

    private void finish(Map<String, Long> before) {
        result.put("bases", phase.stop());
        result.put("counters", layout.counters(before, layout.totals()));
        result.put("chunks_loaded", ServerState.loadedChunks());
        result.put("entities", ServerState.entities());
        result.put("entity_types", ServerState.entityTypes());
        result.putAll(ServerState.memoryUnderLoad());
        result.writeAndStop(plugin);
    }

    private void afterTicks(long delay, Runnable step) {
        Bukkit.getScheduler().runTaskLater(plugin, step, delay);
    }
}
