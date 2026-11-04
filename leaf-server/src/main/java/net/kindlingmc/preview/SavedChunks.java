package net.kindlingmc.preview;

import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import com.mojang.logging.LogUtils;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.slf4j.Logger;

/**
 * The saved chunks of one world as previews: reads the chunk from the world's region files and builds
 * {@link SavedPreview} from it, or says in a {@link SavedOutcome} that the seed preview must be used instead: no saved
 * chunk, another data version, too early a status, a different world height, an unreadable chunk, a world that is gone,
 * or Paper's Anti-Xray enabled in the world (a preview sends the blocks from a little below each column's surface
 * upward, which can include ores that Anti-Xray hides; packet-based anti-xray plugins are not detected).
 *
 * <p>This code writes nothing. Reading goes through the chunk system's own read path, which, as for a real load, may
 * repair a region header, quarantine an unreadable chunk, or (with the linear region format) open the region file for
 * writing.
 *
 * <p>Threads: {@link #preview} blocks the calling thread until the region file has been read, so call it from a
 * preview worker, never from the tick thread. The read is queued at {@link Priority#IDLE} in the chunk system's I/O
 * queue, the lowest, behind every chunk load and save of that world, and a real load of the same chunk raises it.
 * The wait is short and bounded: when a read times out (busy real I/O, a world being unloaded, the server stopping),
 * the world's previews come from the seed for a few seconds instead. The world is only held weakly.
 */
final class SavedChunks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * A read waits at most this long: a preview worker held up by busy real I/O (or a stopped I/O queue) is better
     * spent on a seed preview, which takes a few milliseconds.
     */
    private static final long READ_TIMEOUT_MILLIS = 250L;
    /** After a read timed out, the world's saved chunks are not read for this long: its I/O is busy with real work. */
    private static final long BUSY_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final int CURRENT_DATA_VERSION = SharedConstants.getCurrentVersion().dataVersion().version();

    private final WeakReference<ServerLevel> level;
    private final String worldName;
    private final Terrain terrain;
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile long busyUntilNanos = System.nanoTime();

    SavedChunks(ServerLevel level, Terrain terrain) {
        this.level = new WeakReference<>(level);
        this.worldName = level.dimension().identifier().toString();
        this.terrain = terrain;
    }

    /** The preview of the chunk as saved, or why to use the seed preview and whether that one may be cached. */
    SavedOutcome preview(ChunkPos pos) {
        ServerLevel world = this.level.get();
        if (world == null || world.chunkPacketBlockController != ChunkPacketBlockController.NO_OPERATION_INSTANCE) {
            return SavedOutcome.NONE_LASTING;
        }
        if (this.isIoBusy()) {
            return SavedOutcome.NONE_FOR_NOW;
        }
        try {
            return read(world, pos).map(tag -> build(world, pos, tag)).orElse(SavedOutcome.NONE_LASTING);
        } catch (TimeoutException e) {
            PreviewStats.recordSavedReadTimeout();
            return SavedOutcome.NONE_FOR_NOW;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SavedOutcome.NONE_FOR_NOW;
        } catch (IOException | RuntimeException e) {
            reportFailure(pos, e);
            return SavedOutcome.NONE_LASTING;
        }
    }

    private SavedOutcome build(ServerLevel world, ChunkPos pos, CompoundTag tag) {
        Optional<SavedChunkCheck.Rejection> rejection = SavedChunkCheck.ofTag(tag, CURRENT_DATA_VERSION);
        if (rejection.isPresent()) {
            return notPreviewed(pos, rejection.get());
        }
        SerializableChunkData data = SerializableChunkData.parse(world, world.palettedContainerFactory(), tag);
        if (data == null) {
            return SavedOutcome.NONE_LASTING;
        }
        rejection = SavedChunkCheck.ofParsed(pos, data.chunkPos(), data.sectionData(), world.getMinSectionY(), world.getMaxSectionY());
        if (rejection.isPresent()) {
            return notPreviewed(pos, rejection.get());
        }
        return SavedOutcome.of(SavedPreview.of(data, this.terrain));
    }

    /** The saved data of the chunk, or empty when there is none; times out when the read did not finish in time. */
    private Optional<CompoundTag> read(ServerLevel world, ChunkPos pos) throws IOException, InterruptedException, TimeoutException {
        CompletableFuture<CompoundTag> loaded = new CompletableFuture<>();
        Cancellable read = MoonriseRegionFileIO.loadDataAsync(world, pos.x(), pos.z(), MoonriseRegionFileIO.RegionFileType.CHUNK_DATA,
            (tag, failure) -> {
                if (failure == null) {
                    loaded.complete(tag);
                } else {
                    loaded.completeExceptionally(failure);
                }
            }, true, Priority.IDLE);
        try {
            return Optional.ofNullable(loaded.get(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } catch (ExecutionException e) {
            throw new IOException("cannot read the saved chunk " + pos, e.getCause());
        } catch (TimeoutException e) {
            read.cancel();
            this.busyUntilNanos = System.nanoTime() + BUSY_BACKOFF_NANOS;
            LOGGER.debug("Reading the saved chunk {} of {} timed out; seed previews for a while", pos, this.worldName);
            throw e;
        } catch (InterruptedException e) {
            read.cancel();
            throw e;
        }
    }

    private boolean isIoBusy() {
        return System.nanoTime() - this.busyUntilNanos < 0;
    }

    private SavedOutcome notPreviewed(ChunkPos pos, SavedChunkCheck.Rejection rejection) {
        LOGGER.debug("Not previewing the saved chunk {} of {}: {}", pos, this.worldName, rejection);
        return SavedOutcome.rejected(rejection);
    }

    private void reportFailure(ChunkPos pos, Exception failure) {
        if (this.warned.compareAndSet(false, true)) {
            LOGGER.warn("Cannot preview the saved chunk {} of {}; using the seed preview. Later failures in this world are logged at debug level.",
                pos, this.worldName, failure);
        } else {
            LOGGER.debug("Cannot preview the saved chunk {} of {}; using the seed preview", pos, this.worldName, failure);
        }
    }
}
