package net.kindlingmc.bench;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * One benchmark run, phase after phase: the fake players join and wait, fly outward (chunk generation and sending), land
 * among spawned mobs and stand still (entity ticking), then the heap is measured, the result written and the server
 * stopped. Every random choice comes from a fixed seed, so two runs ask the server for the same work.
 */
final class BenchRun {

    private static final int TICKS_PER_SECOND = 20;
    private static final double FLIGHT_HEIGHT = 160;
    private static final int NIGHT = 18000;
    private static final int MOB_SPREAD = 24;
    private static final long MOB_SEED = 26_03L;
    private static final List<EntityType> MOB_MIX = List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG,
            EntityType.CHICKEN, EntityType.VILLAGER, EntityType.ZOMBIE, EntityType.SKELETON);
    private static final int LEAD_SAMPLE_TICKS = 5;

    private final Plugin plugin;
    private final Scenario scenario;
    private final TickRecorder ticks;
    private final FakePlayers fakePlayers;
    private final RunResult result = new RunResult();
    private final List<Player> players = new ArrayList<>();
    private World world;
    private ChunkLead lead;
    private int flightTicks;

    BenchRun(Plugin plugin, Scenario scenario, TickRecorder ticks, FakePlayers fakePlayers) {
        this.plugin = plugin;
        this.scenario = scenario;
        this.ticks = ticks;
        this.fakePlayers = fakePlayers;
    }

    void start() {
        world = Bukkit.getWorlds().getFirst();
        world.setTime(NIGHT);
        world.setStorm(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule advance_time false");
        result.put("scenario", scenario);
        joinPlayers();
        later(scenario.warmupSeconds(), this::travel);
    }

    private void joinPlayers() {
        for (int i = 0; i < scenario.players(); i++) {
            players.add(fakePlayers.joinFlying("bench" + i, startOf(i)));
        }
    }

    private Location startOf(int index) {
        double angle = angleOf(index);
        return new Location(world, Math.cos(angle) * scenario.circleRadius(), FLIGHT_HEIGHT,
                Math.sin(angle) * scenario.circleRadius());
    }

    private double angleOf(int index) {
        return 2 * Math.PI * index / scenario.players();
    }

    private void travel() {
        lead = new ChunkLead(players.getFirst().getSendViewDistance());
        Bukkit.getPluginManager().registerEvents(lead, plugin);
        ticks.start();
        BukkitTask flight = Bukkit.getScheduler().runTaskTimer(plugin, this::flyOneTick, 1, 1);
        later(scenario.travelSeconds(), () -> {
            flight.cancel();
            HandlerList.unregisterAll(lead);
            result.put("chunk_lead", lead.summary(scenario.travelSeconds()));
            result.put("travel", ticks.stop());
            result.put("flown_blocks_avg", averageFlownBlocks());
            land();
        });
    }

    private void flyOneTick() {
        boolean sampleLead = ++flightTicks % LEAD_SAMPLE_TICKS == 0;
        for (int i = 0; i < players.size(); i++) {
            double angle = angleOf(i);
            Location next = players.get(i).getLocation()
                    .add(Math.cos(angle) * scenario.blocksPerTick(), 0, Math.sin(angle) * scenario.blocksPerTick());
            next.setY(FLIGHT_HEIGHT);
            fakePlayers.move(players.get(i), next);
            if (sampleLead) {
                lead.sample(players.get(i), angle);
            }
        }
    }

    /** How far the players got; far below the planned distance means the server rejected or held back the flight. */
    private long averageFlownBlocks() {
        double total = 0;
        for (int i = 0; i < players.size(); i++) {
            total += players.get(i).getLocation().distance(startOf(i));
        }
        return Math.round(total / players.size());
    }

    private void land() {
        Random random = new Random(MOB_SEED);
        for (Player player : players) {
            Location ground = groundAt(player.getLocation());
            player.teleport(ground);
            spawnMobsAround(ground, random);
        }
        later(scenario.settleSeconds(), this::steady);
    }

    private void spawnMobsAround(Location centre, Random random) {
        for (int i = 0; i < scenario.mobsPerPlayer(); i++) {
            Location at = groundAt(centre.clone().add(random.nextInt(-MOB_SPREAD, MOB_SPREAD + 1), 0,
                    random.nextInt(-MOB_SPREAD, MOB_SPREAD + 1)));
            Entity mob = world.spawnEntity(at, MOB_MIX.get(random.nextInt(MOB_MIX.size())));
            if (mob instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
            }
        }
    }

    private Location groundAt(Location location) {
        return location.clone().set(location.getBlockX() + 0.5,
                world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1, location.getBlockZ() + 0.5);
    }

    private void steady() {
        ticks.start();
        later(scenario.steadySeconds(), () -> {
            result.put("steady", ticks.stop());
            result.put("chunks_loaded", ServerState.loadedChunks());
            result.put("entities", ServerState.entities());
            result.putAll(ServerState.memoryUnderLoad());
            idle();
        });
    }

    /** Everyone leaves, as at night on a small server; what the JVM gives back by itself in that time is measured. */
    private void idle() {
        players.forEach(Player::kick);
        later(scenario.idleSeconds(), () -> {
            result.put("idle", idleFigures());
            result.writeAndStop(plugin);
        });
    }

    private static Map<String, Object> idleFigures() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("chunks_loaded", ServerState.loadedChunks());
        figures.put("entities", ServerState.entities());
        figures.put("heap_used_mb", memory.getHeapMemoryUsage().getUsed() / ServerState.BYTES_PER_MB);
        figures.put("heap_committed_mb", memory.getHeapMemoryUsage().getCommitted() / ServerState.BYTES_PER_MB);
        return figures;
    }

    private void later(int seconds, Runnable step) {
        Bukkit.getScheduler().runTaskLater(plugin, step, (long) seconds * TICKS_PER_SECOND);
    }
}
