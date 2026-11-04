package net.kindlingmc.preview;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.kindlingmc.chunk.LookAheadDirection;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.dreeam.leaf.config.modules.network.PreviewChunks;
import org.slf4j.Logger;

/**
 * One player's previews in one world. The ring beyond the send distance gets previews computed in the background, and
 * with {@code fill-gaps} while the player is moving so do chunks within it that the server has neither sent nor loaded
 * yet; real chunks the server stops sending stay on the client as their own preview. The chunk loader keeps owning
 * every chunk it has sent: a chunk it sends replaces the preview on the client and leaves this ring's bookkeeping. While previews
 * are off (the default, or after a config reload) nothing is computed and nothing stays on the client.
 *
 * <p>Tick thread only, except {@link #compute(long)}, which runs on a preview worker and talks back only through the
 * {@link #ready} queue and the {@link #closed} flag.
 */
public final class PlayerPreviews {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SELECT_INTERVAL_TICKS = 10;
    private static final int UNKNOWN_VIEW_DISTANCE = -1;
    /**
     * With {@code fill-gaps}, chunks this close to the player never get a preview: the player could walk into a stand-in
     * tree there, which the server does not have, and the loader sends these chunks first anyway.
     */
    private static final int REAL_ONLY_RADIUS = 2;
    /** The most previews a player standing still has pending; two selections of these keep the unhurried worker busy. */
    private static final int UNHURRIED_PENDING = 32;

    /** A preview computed off the tick thread; empty when computing it failed. */
    private record Ready(long chunk, Optional<PreviewPacket> packet) {
    }

    private final ServerLevel level;
    private final ServerPlayer player;
    private final LongSet sentByLoader;
    private final RingChunks ring = new RingChunks();
    /** Chunks this ring put or left on the client: previews it sent and real chunks it kept. */
    private final LongOpenHashSet onClient = new LongOpenHashSet();
    /** Chunks being computed. */
    private final LongOpenHashSet claimed = new LongOpenHashSet();
    /** Chunks whose preview failed; not tried again while in range. */
    private final LongOpenHashSet failed = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<Ready> ready = new ConcurrentLinkedQueue<>();
    private final FastTravel travel = new FastTravel();
    private volatile boolean closed;
    private volatile boolean warned;
    /** Resolved on first use with previews on, so that a server with previews off never builds one. */
    private PreviewWorld world;
    private int radius;
    private int sendDistance;
    private boolean moving;
    private int ticksUntilSelect;
    private int centerX;
    private int centerZ;

    /**
     * @param sentByLoader the live set of chunks the chunk loader has sent to this player; only read here
     */
    public PlayerPreviews(final ServerLevel level, final ServerPlayer player, final LongSet sentByLoader) {
        this.level = level;
        this.player = player;
        this.sentByLoader = sentByLoader;
    }

    /** The chunk radius to tell the client: the preview radius while previews are on, else the server's own. */
    public int clientRadius(final int sendViewDistance) {
        return this.isActive() ? Math.max(sendViewDistance, PreviewChunks.radius) : sendViewDistance;
    }

    /**
     * The load distance the chunk loader uses: the configured one, but at most {@code travel-view-distance} + 1 while
     * the player is on a fast journey and its previews reach beyond that distance, so that previews stand in for the
     * far chunks instead of generating them. The loader keeps the tick and send distances below it, as always.
     */
    public int loadDistance(final int configuredLoadDistance) {
        final int travelViewDistance = PreviewChunks.travelViewDistance;
        if (travelViewDistance == 0 || travelViewDistance >= this.radius || !this.travel.isFast() || this.closed || !this.isActive()) {
            return configuredLoadDistance;
        }
        return Math.min(configuredLoadDistance, travelViewDistance + 1);
    }

    /** One tick: notes the player's speed, sends computed previews, forgets those out of range, and starts the next ones. */
    public void tick(final int centerX, final int centerZ, final int sendDistance, final int clientViewDistance,
                     final LookAheadDirection order) {
        if (this.closed) {
            return;
        }
        if (!this.isActive()) {
            this.forgetAll();
            return;
        }
        this.noteSpeed();
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.sendDistance = sendDistance;
        this.moving = order.isMoving(MinecraftServer.currentTick);
        this.radius = clientViewDistance == UNKNOWN_VIEW_DISTANCE ? PreviewChunks.radius : Math.min(PreviewChunks.radius, clientViewDistance);
        this.sendReady();
        if (--this.ticksUntilSelect > 0) {
            return;
        }
        this.ticksUntilSelect = SELECT_INTERVAL_TICKS;
        this.forgetOutOfRange();
        this.startNext(order);
    }

    /**
     * Called when the player's chunk changed, before the client learns its new center: the client ignores forget
     * packets for chunks outside its range around the new center, so previews left behind are forgotten now.
     */
    public void recenter(final int centerX, final int centerZ) {
        if (this.closed || !this.isActive()) {
            return;
        }
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.forgetOutOfRange();
    }

    /**
     * Called when the chunk loader stops sending a real chunk. Returns whether the client keeps it as its preview, in
     * which case the caller must not send the client the forget packet.
     */
    public boolean keepOnClient(final int chunkX, final int chunkZ) {
        final long chunk = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        if (this.closed || !this.isActive() || !PreviewChunks.keepRealChunks || !this.isNearPlayer(chunk)) {
            this.onClient.remove(chunk);
            return false;
        }
        this.onClient.add(chunk);
        PreviewStats.recordKeptOnClient();
        return true;
    }

    /** The player leaves this world or the server: the client drops this ring's chunks; nothing more is computed. */
    public void close() {
        this.closed = true;
        this.forgetAll();
    }

    private boolean isActive() {
        return PreviewChunks.enabled && this.world().available();
    }

    /** Only with {@code travel-view-distance} on; turned on later, the first sample starts over. */
    private void noteSpeed() {
        if (PreviewChunks.travelViewDistance > 0) {
            this.travel.sample(this.player.getX(), this.player.getZ(), MinecraftServer.currentTick, PreviewChunks.travelSpeed);
        }
    }

    private PreviewWorld world() {
        if (this.world == null) {
            this.world = PreviewWorld.of(this.level);
        }
        return this.world;
    }

    private boolean isNearPlayer(final long chunk) {
        final ChunkPos at = this.player.chunkPosition();
        return RingChunks.isWithin(chunk, at.x(), at.z(), this.radius);
    }

    private void sendReady() {
        int sent = 0;
        while (sent < PreviewChunks.sendPerTick) {
            final Ready next = this.ready.poll();
            if (next == null) {
                return;
            }
            this.claimed.remove(next.chunk());
            if (next.packet().isEmpty()) {
                this.failed.add(next.chunk());
                PreviewStats.recordFailed();
            } else if (this.isStillWanted(next.chunk())) {
                this.player.connection.send(next.packet().get().packet());
                PreviewStats.recordSent(next.packet().get().rawBytes());
                this.onClient.add(next.chunk());
                sent++;
            } else {
                PreviewStats.recordDroppedOnArrival();
            }
        }
    }

    /** Checked again on arrival: the player may have come closer, or the server loaded the chunk, while it was computed. */
    private boolean isStillWanted(final long chunk) {
        return !this.sentByLoader.contains(chunk) && !this.onClient.contains(chunk)
            && RingChunks.isWithin(chunk, this.centerX, this.centerZ, this.radius)
            && !RingChunks.isWithin(chunk, this.centerX, this.centerZ, this.innerRadius())
            && !this.isAboutToBeSent(chunk);
    }

    /** Hands chunks the loader sent back to it, and tells the client to drop previews that left the radius. */
    private void forgetOutOfRange() {
        for (final LongIterator chunks = this.onClient.iterator(); chunks.hasNext(); ) {
            final long chunk = chunks.nextLong();
            if (this.sentByLoader.contains(chunk)) {
                chunks.remove();
            } else if (!RingChunks.isWithin(chunk, this.centerX, this.centerZ, this.radius)) {
                this.forgetOnClient(chunk);
                chunks.remove();
            }
        }
        for (final LongIterator chunks = this.failed.iterator(); chunks.hasNext(); ) {
            if (!RingChunks.isWithin(chunks.nextLong(), this.centerX, this.centerZ, this.radius)) {
                chunks.remove();
            }
        }
    }

    /** Tells the client to drop every chunk of this ring; previews still being computed are dropped when they arrive. */
    private void forgetAll() {
        if (this.onClient.isEmpty() && this.claimed.isEmpty() && this.failed.isEmpty() && this.ready.isEmpty()) {
            return;
        }
        for (final LongIterator chunks = this.onClient.iterator(); chunks.hasNext(); ) {
            final long chunk = chunks.nextLong();
            if (!this.sentByLoader.contains(chunk)) {
                this.forgetOnClient(chunk);
            }
        }
        this.onClient.clear();
        this.claimed.clear();
        this.failed.clear();
        this.ready.clear();
    }

    private void forgetOnClient(final long chunk) {
        this.player.connection.send(new ClientboundForgetLevelChunkPacket(ChunkPos.unpack(chunk)));
    }

    private void startNext(final LookAheadDirection order) {
        final int room = this.maxPending() - this.claimed.size();
        if (room <= 0) {
            return;
        }
        final LongArrayList next = this.ring.select(this.centerX, this.centerZ, this.innerRadius(), this.radius, order, this::isTaken, room);
        for (int i = 0; i < next.size(); i++) {
            final long chunk = next.getLong(i);
            this.claimed.add(chunk);
            this.submit(chunk);
        }
    }

    /**
     * Few while standing still, so the unhurried worker's queue stays short and fair between players, and a player who
     * starts moving finds nearly all its room free for the shared workers.
     */
    private int maxPending() {
        return this.moving ? PreviewChunks.maxPendingPerPlayer : Math.min(UNHURRIED_PENDING, PreviewChunks.maxPendingPerPlayer);
    }

    /**
     * A player standing still, which includes just after joining or a teleport, waits for the unhurried worker: its
     * ring fills more slowly, but all standing players together cost at most one core.
     */
    private void submit(final long chunk) {
        if (this.moving) {
            PreviewWorkers.execute(() -> this.compute(chunk));
        } else {
            PreviewWorkers.executeUnhurried(() -> this.compute(chunk));
        }
    }

    /**
     * Within it, previews are left to the loader: the send distance, or only the closest chunks when filling gaps while
     * the player moves. A player standing still or just joined gets its chunks from the loader moments later anyway.
     */
    private int innerRadius() {
        return PreviewChunks.fillGaps && this.moving ? Math.min(REAL_ONLY_RADIUS, this.sendDistance) : this.sendDistance;
    }

    private boolean isTaken(final long chunk) {
        return this.sentByLoader.contains(chunk) || this.onClient.contains(chunk) || this.claimed.contains(chunk) || this.failed.contains(chunk)
            || this.isAboutToBeSent(chunk);
    }

    /**
     * A chunk the loader sends (by its own distance test) that the server has loaded: the real one comes soon, a
     * preview is wasted.
     */
    private boolean isAboutToBeSent(final long chunk) {
        final int chunkX = CoordinateUtils.getChunkX(chunk);
        final int chunkZ = CoordinateUtils.getChunkZ(chunk);
        return ChunkTrackingView.isWithinDistance(this.centerX, this.centerZ, this.sendDistance, chunkX, chunkZ, true)
            && this.level.getChunkSource().getChunkAtIfLoadedImmediately(chunkX, chunkZ) != null;
    }

    /** On a preview worker. Always reports back, so that the chunk never stays claimed, even when an error escapes. */
    private void compute(final long chunk) {
        if (this.closed) {
            return;
        }
        Optional<PreviewPacket> packet = Optional.empty();
        try {
            packet = this.tryPreview(chunk);
        } finally {
            this.ready.add(new Ready(chunk, packet));
        }
    }

    private Optional<PreviewPacket> tryPreview(final long chunk) {
        try {
            return Optional.of(this.world.preview(chunk));
        } catch (final RuntimeException e) {
            this.reportFailure(chunk, e);
            return Optional.empty();
        }
    }

    /** The first failure of this player's previews with its stack trace, later ones only in the debug log. */
    private void reportFailure(final long chunk, final RuntimeException e) {
        if (this.warned) {
            LOGGER.debug("Could not compute the preview of chunk {}", ChunkPos.unpack(chunk), e);
            return;
        }
        this.warned = true;
        LOGGER.warn("Could not compute the preview of chunk {}; further failures for this player are logged at debug level", ChunkPos.unpack(chunk), e);
    }
}
