package net.kindlingmc.bench;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.kindlingmc.preview.FidelityCounts;
import net.kindlingmc.preview.PreviewFidelity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.dreeam.leaf.config.modules.network.PreviewChunks;

/**
 * How faithful the seed previews of a Kindling server are: draws chunks at random from a square around the origin
 * (the same ones in every run), loads each to full status, compares it with its preview ({@link PreviewFidelity}) and
 * writes the totals as {@code fidelity}. Only the overworld, no players.
 *
 * <p>A chunk is compared only once its light can be trusted: a plugin ticket keeps the chunk and its neighbours loaded
 * to full status, as they are around a player, so light has flowed in over the chunk's edges; the comparison then waits
 * {@value #LIGHT_SETTLE_TICKS} ticks after the load and for an idle light engine. A chunk that is still not settled
 * after {@value #GIVE_UP_TICKS} ticks is compared anyway and counted in {@code light_unsettled_chunks}. Loading is
 * synchronous but spread over ticks, {@value #LOADS_PER_TICK} chunks each, so the server stays responsive.
 */
final class FidelityRun {

    private static final long PICK_SEED = 26_03L;
    private static final int LOADS_PER_TICK = 2;
    private static final int LIGHT_SETTLE_TICKS = 40;
    private static final int GIVE_UP_TICKS = 600;
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int BORDER_MARGIN_CHUNKS = 4;

    private final Plugin plugin;
    private final Scenario scenario;
    private final RunResult result = new RunResult();
    private final FidelityCounts totals = new FidelityCounts();
    private final Deque<Site> notLoaded;
    private final Deque<LoadedSite> loaded = new ArrayDeque<>();
    private World world;
    private ServerLevel level;
    private int lightUnsettledChunks;

    FidelityRun(Plugin plugin, Scenario scenario) {
        this.plugin = plugin;
        this.scenario = scenario;
        this.notLoaded = pickSites(scenario.fidelityChunks(), scenario.fidelityRadius());
    }

    void start() {
        world = Bukkit.getWorlds().getFirst();
        level = ((CraftWorld) world).getHandle();
        result.put("scenario", scenario);
        if (!squareFitsInsideBorder()) {
            stopWithoutResult("the world border is closer than fidelity-radius + " + BORDER_MARGIN_CHUNKS + " chunks");
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::step, 1, 1);
    }

    /** The same chunks for the same count and radius, and the first chunks of a larger count are those of a smaller one. */
    private static Deque<Site> pickSites(int count, int radius) {
        Random random = new Random(PICK_SEED);
        long squareSide = 2L * radius + 1;
        int wanted = (int) Math.min(count, squareSide * squareSide);
        Set<Site> picked = new LinkedHashSet<>();
        while (picked.size() < wanted) {
            picked.add(new Site(random.nextInt(-radius, radius + 1), random.nextInt(-radius, radius + 1)));
        }
        return new ArrayDeque<>(picked);
    }

    private boolean squareFitsInsideBorder() {
        double borderRadiusInChunks = world.getWorldBorder().getSize() / 2 / BLOCKS_PER_CHUNK;
        return scenario.fidelityRadius() + BORDER_MARGIN_CHUNKS <= borderRadiusInChunks;
    }

    private void step(BukkitTask task) {
        loadSome();
        if (!compareSettledChunks(task)) {
            return;
        }
        if (notLoaded.isEmpty() && loaded.isEmpty()) {
            task.cancel();
            finish();
        }
    }

    private void loadSome() {
        for (int i = 0; i < LOADS_PER_TICK && !notLoaded.isEmpty(); i++) {
            Site site = notLoaded.poll();
            world.addPluginChunkTicket(site.x(), site.z(), plugin);
            world.getChunkAt(site.x(), site.z());
            loaded.add(new LoadedSite(site, Bukkit.getCurrentTick()));
        }
    }

    /** Compares the loaded chunks in loading order while the oldest is settled; false after a failure, which stops the run. */
    private boolean compareSettledChunks(BukkitTask task) {
        while (!loaded.isEmpty() && isSettledOrOverdue(loaded.peek())) {
            LoadedSite next = loaded.poll();
            if (!compare(next.site())) {
                task.cancel();
                return false;
            }
        }
        return true;
    }

    private boolean isSettledOrOverdue(LoadedSite candidate) {
        int waited = Bukkit.getCurrentTick() - candidate.loadedAtTick();
        if (waited >= GIVE_UP_TICKS) {
            return true;
        }
        return waited >= LIGHT_SETTLE_TICKS && lightIsSettled(candidate.site());
    }

    /** Light is settled when the chunk and its neighbours are at full status and the light engine has nothing queued. */
    private boolean lightIsSettled(Site site) {
        return neighbourhoodIsFull(site) && !level.getLightEngine().hasLightWork();
    }

    private boolean neighbourhoodIsFull(Site site) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(site.x() + dx, site.z() + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean compare(Site site) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(site.x(), site.z());
        if (chunk == null) {
            stopWithoutResult("chunk " + site + " is not loaded after " + GIVE_UP_TICKS + " ticks");
            return false;
        }
        if (!lightIsSettled(site) || !chunk.isLightCorrect()) {
            lightUnsettledChunks++;
        }
        Optional<FidelityCounts> counts = compareOrStop(site, chunk);
        counts.ifPresent(totals::add);
        world.removePluginChunkTicket(site.x(), site.z(), plugin);
        return counts.isPresent();
    }

    /** A failed comparison stops the run: a run that silently skipped chunks would report too few of them as a result. */
    private Optional<FidelityCounts> compareOrStop(Site site, LevelChunk chunk) {
        try {
            Optional<FidelityCounts> counts = PreviewFidelity.compare(level, chunk);
            if (counts.isEmpty()) {
                stopWithoutResult("the world has no preview generator");
            }
            return counts;
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Comparing chunk " + site + " failed", e);
            stopWithoutResult("comparing chunk " + site + " failed");
            return Optional.empty();
        }
    }

    private void finish() {
        Map<String, Object> fidelity = new LinkedHashMap<>(totals.toFigures());
        fidelity.put("light_unsettled_chunks", lightUnsettledChunks);
        fidelity.put("sample_seed", PICK_SEED);
        result.put("preview_trees", PreviewChunks.trees);
        result.put("fidelity", fidelity);
        result.writeAndStop(plugin);
    }

    /** A run that cannot measure ends at once, without the result file, so that run_bench.py reports it instead of waiting. */
    private void stopWithoutResult(String reason) {
        plugin.getLogger().severe("Fidelity run stopped without a result: " + reason);
        Bukkit.shutdown();
    }

    private record Site(int x, int z) {
        @Override
        public String toString() {
            return "(" + x + ", " + z + ")";
        }
    }

    private record LoadedSite(Site site, int loadedAtTick) {
    }
}
