package previewchunks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stage 2 of the preview-chunks experiment: sends vanilla clients preview chunks (the harness's band generator) for the
 * ring between the server's send distance and a larger radius, so that someone at a real client can see how previews
 * render and how real chunks replace them. Overworld only. For a throwaway test server: it changes what clients see.
 */
public final class PreviewProbe extends JavaPlugin implements Listener {
    private static final int UPDATE_TICKS = 10;
    private static final int STATS_TICKS = 20 * 30;

    /** A preview generated off the main thread, waiting to be sent on it; empty when the generation failed. */
    private record Generated(UUID player, ChunkPos pos, Optional<PreviewPacket> packet, double millis) {
    }

    private static final String DEFAULT_TOUR_SPEED = "20";

    private final Map<UUID, PlayerPreviews> players = new HashMap<>();
    private final Map<UUID, Tour> tours = new HashMap<>();
    /** Read by the generation threads: paint new previews' surface red, to tell them from real chunks. */
    private volatile boolean marking;
    private final ConcurrentLinkedQueue<Generated> generated = new ConcurrentLinkedQueue<>();
    private final Totals totals = new Totals();
    private ExecutorService workers;
    private Optional<Preview> overworld = Optional.empty();
    private int radius;
    private int sendPerTick;
    private int maxPending;
    private boolean autoStart;
    private long ticks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.radius = getConfig().getInt("radius");
        this.sendPerTick = getConfig().getInt("send-per-tick");
        this.maxPending = getConfig().getInt("max-pending");
        this.autoStart = getConfig().getBoolean("auto-start");
        this.workers = Executors.newFixedThreadPool(getConfig().getInt("threads"), workerThreads());
        this.overworld = previewOf(((CraftWorld) getServer().getWorlds().getFirst()).getHandle());
        if (this.overworld.isEmpty()) {
            getLogger().warning("the overworld has no noise generator: no previews");
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
    }

    @Override
    public void onDisable() {
        this.workers.shutdownNow();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (this.autoStart) {
            this.players.put(event.getPlayer().getUniqueId(), new PlayerPreviews(this.radius));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.players.remove(event.getPlayer().getUniqueId());
        Tour tour = this.tours.remove(event.getPlayer().getUniqueId());
        if (tour != null) {
            tour.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !(sender instanceof Player player)) {
            return false;
        }
        switch (args[0]) {
            case "off" -> {
                this.players.remove(player.getUniqueId());
                PlayerPreviews.restoreRadius(player);
                sender.sendMessage("previews off");
            }
            case "stats" -> sender.sendMessage(this.totals.summary());
            case "tour" -> tour(player, args.length > 1 ? args[1] : DEFAULT_TOUR_SPEED);
            case "mark" -> {
                this.marking = !this.marking;
                sender.sendMessage(this.marking ? "new previews get a red surface" : "new previews look normal again");
            }
            case "on" -> startPreviews(player, this.radius);
            default -> startPreviews(player, Integer.parseInt(args[0]));
        }
        return true;
    }

    private void startPreviews(Player player, int radius) {
        this.players.put(player.getUniqueId(), new PlayerPreviews(radius));
        player.sendMessage("previews up to " + radius + " chunks; set your render distance at least that high");
    }

    /** "/preview tour [blocks per second]" flies the player straight on in spectator mode; "/preview tour stop" ends it. */
    private void tour(Player player, String speed) {
        Tour running = this.tours.remove(player.getUniqueId());
        if (running != null) {
            running.stop();
        }
        if (speed.equals("stop")) {
            player.sendMessage("tour stopped");
            return;
        }
        this.players.computeIfAbsent(player.getUniqueId(), id -> new PlayerPreviews(this.radius));
        this.tours.put(player.getUniqueId(), Tour.start(player, Double.parseDouble(speed)));
        player.sendMessage("flying straight on at " + speed + " blocks/s; look around with the mouse; /preview tour stop ends it");
    }

    private void tick() {
        this.ticks++;
        this.tours.values().forEach(Tour::step);
        if (this.ticks % UPDATE_TICKS == 0) {
            startGenerations();
        }
        sendGenerated();
        if (this.ticks % STATS_TICKS == 0) {
            this.totals.logIfChanged(getLogger());
        }
    }

    private void startGenerations() {
        if (this.overworld.isEmpty()) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerPreviews previews = this.players.get(player.getUniqueId());
            if (previews != null && isInOverworld(player)) {
                for (ChunkPos pos : previews.update(player, this.maxPending)) {
                    submit(this.overworld.get(), player.getUniqueId(), pos);
                }
            }
        }
    }

    private void submit(Preview preview, UUID player, ChunkPos pos) {
        this.workers.execute(() -> {
            long start = System.nanoTime();
            Optional<PreviewPacket> packet = Optional.empty();
            try {
                ProtoChunk chunk = preview.generateSkin(pos);
                if (this.marking) {
                    PreviewPacket.paintSurface(chunk, Blocks.CONCRETE.red().defaultBlockState());
                }
                packet = Optional.of(PreviewPacket.of(chunk));
            } catch (RuntimeException e) {
                getLogger().warning("preview of " + pos + " failed: " + e);
            }
            this.generated.add(new Generated(player, pos, packet, (System.nanoTime() - start) / 1e6));
        });
    }

    private void sendGenerated() {
        for (int sent = 0; sent < this.sendPerTick; sent++) {
            Generated next = this.generated.poll();
            if (next == null) {
                return;
            }
            deliver(next);
        }
    }

    /** Sends a preview unless the real chunk got there first, the player left, or the chunk is out of range now. */
    private void deliver(Generated preview) {
        Player player = getServer().getPlayer(preview.player());
        PlayerPreviews previews = this.players.get(preview.player());
        if (player == null || previews == null) {
            return;
        }
        long key = preview.pos().pack();
        if (preview.packet().isEmpty() || player.isChunkSent(key) || !isInOverworld(player) || !previews.isWanted(player, preview.pos())) {
            previews.dropped(key);
            this.totals.dropped();
            return;
        }
        ((CraftPlayer) player).getHandle().connection.send(preview.packet().get().packet());
        previews.delivered(key);
        this.totals.sent(preview.millis(), preview.packet().get().rawBytes());
    }

    private static boolean isInOverworld(Player player) {
        return ((CraftWorld) player.getWorld()).getHandle().dimension() == Level.OVERWORLD;
    }

    private static Optional<Preview> previewOf(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
            return Optional.empty();
        }
        World world = new World(level.getSeed(), noise, noise.generatorSettings().value(), level.getChunkSource().randomState(),
            PalettedContainerFactory.create(level.registryAccess()), level);
        return Optional.of(new Preview(new Terrain(world, Terrain.PossibleBiomes.NONE), Margins.DEFAULT));
    }

    private static ThreadFactory workerThreads() {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "preview-probe-" + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    /** What the probe did since the start: previews sent, their generation time and raw size, previews dropped. */
    private static final class Totals {
        private long sent;
        private long dropped;
        private double millis;
        private long rawBytes;
        private long lastLogged = -1;

        void sent(double generationMillis, int bytes) {
            this.sent++;
            this.millis += generationMillis;
            this.rawBytes += bytes;
        }

        void dropped() {
            this.dropped++;
        }

        String summary() {
            return String.format("previews sent %d, dropped %d, generation %.2f ms each, raw %d B each", this.sent, this.dropped,
                this.millis / Math.max(1, this.sent), this.rawBytes / Math.max(1, this.sent));
        }

        void logIfChanged(java.util.logging.Logger logger) {
            if (this.sent + this.dropped != this.lastLogged) {
                this.lastLogged = this.sent + this.dropped;
                logger.info(summary());
            }
        }
    }
}
