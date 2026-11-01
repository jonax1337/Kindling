package net.kindlingmc.preview;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.dreeam.leaf.config.modules.network.PreviewChunks;

/**
 * The previews of one world: its generator (none for worlds without one, such as the Nether) and the most recently
 * built preview packets, so that several players and a player flying back reuse them. Safe to use from any thread.
 */
final class PreviewWorld {
    private static final Map<ServerLevel, PreviewWorld> WORLDS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Optional<PreviewGenerator> generator;
    /** Built on first use, on a preview worker: deriving every biome's trees takes a moment. */
    private final Supplier<PreviewTrees> trees;
    private final long seed;
    private final int seaLevel;
    private final RecentPackets recent = new RecentPackets();

    private PreviewWorld(final Optional<PreviewGenerator> generator, final Supplier<PreviewTrees> trees, final long seed, final int seaLevel) {
        this.generator = generator;
        this.trees = trees;
        this.seed = seed;
        this.seaLevel = seaLevel;
    }

    static PreviewWorld of(final ServerLevel level) {
        return WORLDS.computeIfAbsent(level, PreviewWorld::create);
    }

    private static PreviewWorld create(final ServerLevel level) {
        final RegistryAccess registries = level.registryAccess();
        final long seed = level.getSeed();
        return new PreviewWorld(PreviewGenerator.forLevel(level), Suppliers.memoize(() -> PreviewTrees.of(registries, seed)), seed, level.getSeaLevel());
    }

    boolean available() {
        return this.generator.isPresent();
    }

    /** The preview of a chunk, from the recent packets or computed now. Call off the tick thread only when computing. */
    PreviewPacket preview(final long chunkKey) {
        final Optional<PreviewPacket> known = this.recent.get(chunkKey);
        if (known.isPresent()) {
            return known.get();
        }
        final ProtoChunk chunk = this.generator.orElseThrow().generate(ChunkPos.unpack(chunkKey));
        if (PreviewChunks.trees) {
            this.trees.get().decorate(chunk);
        }
        PreviewSnow.freeze(chunk, this.seed, this.seaLevel);
        final PreviewPacket built = PreviewPacket.of(chunk);
        this.recent.put(chunkKey, built);
        return built;
    }

    /** The {@code cache-size} most recently used preview packets, by chunk key. */
    private static final class RecentPackets {
        private static final float LOAD_FACTOR = 0.75F;

        private final LinkedHashMap<Long, PreviewPacket> packets = new LinkedHashMap<>(16, LOAD_FACTOR, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Long, PreviewPacket> eldest) {
                return this.size() > PreviewChunks.cacheSize;
            }
        };

        synchronized Optional<PreviewPacket> get(final long chunkKey) {
            return Optional.ofNullable(this.packets.get(chunkKey));
        }

        synchronized void put(final long chunkKey, final PreviewPacket packet) {
            if (PreviewChunks.cacheSize > 0) {
                this.packets.put(chunkKey, packet);
            }
        }
    }
}
