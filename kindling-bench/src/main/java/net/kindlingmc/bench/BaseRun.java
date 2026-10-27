package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A run in survival bases: every fake player hovers among its {@link Plot}s, each with a storage room of hoppers, a row
 * of furnaces, a redstone board and a workshop ({@link PlotGrid}). Once world generation around the players has calmed
 * down, all contraptions are built in the same tick, run through the warm-up and are then measured: tick times, and
 * how far every contraption got. Nothing in a base depends on chance, so two runs, and two server builds that keep
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
    private final TickRecorder ticks;
    private final FakePlayers fakePlayers;
    private final RunResult result = new RunResult();
    private final List<Plot> plots = new ArrayList<>();
    private final List<Contraption> contraptions = new ArrayList<>();
    private final List<Clocked> clocks = new ArrayList<>();
    private final GenerationWatch generation = new GenerationWatch();
    private final PistonCount pistons = new PistonCount();
    private World world;
    private int startTick;
    private int clearedPlots;
    private int clockTicks;

    BaseRun(Plugin plugin, Scenario scenario, TickRecorder ticks, FakePlayers fakePlayers) {
        this.plugin = plugin;
        this.scenario = scenario;
        this.ticks = ticks;
        this.fakePlayers = fakePlayers;
    }

    void start() {
        world = Bukkit.getWorlds().getFirst();
        holdStill(world);
        result.put("scenario", scenario);
        Bukkit.getPluginManager().registerEvents(generation, plugin);
        PlotGrid grid = new PlotGrid(world, scenario.plotsPerPlayer());
        for (int i = 0; i < scenario.players(); i++) {
            plots.addAll(grid.plotsOf(i));
            fakePlayers.joinFlying("bench" + i, grid.standingSpot(i));
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
        List<Clocked> workshops = new ArrayList<>();
        for (Plot plot : plots) {
            RedstoneBoard board = new RedstoneBoard(plot.site(2));
            Workshop workshop = new Workshop(plot.site(3));
            contraptions.addAll(List.of(new HopperHall(plot.site(0)), new FurnaceRow(plot.site(1)), board, workshop));
            clocks.add(board);
            workshops.add(workshop);
        }
        // All boards before all workshops: taking turns, each kind would step on every other tick only.
        clocks.addAll(workshops);
        contraptions.forEach(Contraption::build);
        Bukkit.getPluginManager().registerEvents(pistons, plugin);
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
        for (int clock = 0; clock < clocks.size(); clock++) {
            if ((clockTicks + clock) % Clocked.STEP_TICKS == 0) {
                clocks.get(clock).clockStep();
            }
        }
    }

    /** Reading the counters costs a tick of its own, so the tick times are recorded from the next tick on. */
    private void measure() {
        Map<String, Long> before = totals();
        long measuredTicks = (long) scenario.steadySeconds() * TICKS_PER_SECOND;
        afterTicks(1, ticks::start);
        afterTicks(1 + measuredTicks, () -> finish(before));
    }

    private void finish(Map<String, Long> before) {
        result.put("bases", ticks.stop());
        result.put("counters", growth(before, totals()));
        result.put("chunks_loaded", ServerState.loadedChunks());
        result.put("entities", ServerState.entities());
        result.put("entity_types", ServerState.entityTypes());
        result.putAll(ServerState.memoryUnderLoad());
        result.writeAndStop(plugin);
    }

    private Map<String, Long> totals() {
        Map<String, Long> totals = Contraption.totalsOf(contraptions);
        totals.put("piston_extensions", pistons.extensions());
        return totals;
    }

    private static Map<String, Long> growth(Map<String, Long> before, Map<String, Long> after) {
        Map<String, Long> growth = new TreeMap<>();
        after.forEach((name, value) -> growth.put(name, value - before.getOrDefault(name, 0L)));
        return growth;
    }

    private void afterTicks(long delay, Runnable step) {
        Bukkit.getScheduler().runTaskLater(plugin, step, delay);
    }
}
