package net.kindlingmc.bench;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs the benchmark scenario once the server has started; see run_bench.py for how a run is set up. */
public final class KindlingBench extends JavaPlugin implements Listener {

    private final TickRecorder ticks = new TickRecorder();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(ticks, this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    /** Scheduled tasks already run while the worlds load; the server ticks its connections only after this event. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        readScenario().ifPresent(this::run);
    }

    private void run(Scenario scenario) {
        FakePlayers fakePlayers = new ServerFakePlayers(
                (delayTicks, action) -> Bukkit.getScheduler().runTaskLater(this, action, delayTicks),
                scenario.clientViewDistance(), scenario.chunkAcks());
        MeasuredPhase phase = new MeasuredPhase(ticks, new PhaseMeter(SchedulerStats.ofCurrentThread()));
        Runnable run = switch (scenario.type()) {
            case MIXED -> () -> new BenchRun(this, scenario, phase, fakePlayers).start();
            case BASES, SLEEPING, VILLAGERS -> () -> new BaseRun(this, scenario, phase, fakePlayers).start();
        };
        Bukkit.getScheduler().runTask(this, run);
    }

    /** The scenario, or none after stopping the server, so a mistyped scenario ends the run at once instead of hanging. */
    private Optional<Scenario> readScenario() {
        try {
            return Optional.of(Scenario.read(getDataFolder().toPath()));
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid scenario, stopping without a result: " + e.getMessage());
            Bukkit.shutdown();
            return Optional.empty();
        }
    }
}
