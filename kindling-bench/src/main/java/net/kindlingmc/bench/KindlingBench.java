package net.kindlingmc.bench;

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
        Scenario scenario = Scenario.read(getDataFolder().toPath());
        FakePlayers fakePlayers = new ServerFakePlayers(action -> Bukkit.getScheduler().runTask(this, action),
                scenario.clientViewDistance());
        Bukkit.getScheduler().runTask(this, () -> new BenchRun(this, scenario, ticks, fakePlayers).start());
    }
}
