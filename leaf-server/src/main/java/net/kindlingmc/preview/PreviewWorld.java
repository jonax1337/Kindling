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
    /** Vanilla's vegetation, or where the world's generator does not allow it, the approximate trees. */
    private final Decoration decoration;
    private final long seed;
    private final int seaLevel;
    private final RecentPackets recent = new RecentPackets();

    private PreviewWorld(final Optional<PreviewGenerator> generator, final Decoration decoration, final long seed, final int seaLevel) {
        this.generator = generator;
        this.decoration = decoration;
        this.seed = seed;
        this.seaLevel = seaLevel;
    }

    static PreviewWorld of(final ServerLevel level) {
        return WORLDS.computeIfAbsent(level, PreviewWorld::create);
    }

    private static PreviewWorld create(final ServerLevel level) {
        final Optional<PreviewGenerator> generator = PreviewGenerator.forLevel(level);
        final Decoration decoration = generator.isEmpty() ? Decoration.NONE : decoration(level);
        return new PreviewWorld(generator, decoration, level.getSeed(), level.getSeaLevel());
    }

    /** Never keeps the level: this world is the value of a weak map keyed by it. */
    private static Decoration decoration(final ServerLevel level) {
        final Optional<PreviewVegetation> vegetation = PreviewVegetation.forLevel(level);
        if (vegetation.isPresent()) {
            return vegetation.get()::decorate;
        }
        final RegistryAccess registries = level.registryAccess();
        final long seed = level.getSeed();
        // Built on first use, on a preview worker: deriving every biome's trees takes a moment.
        final Supplier<PreviewTrees> trees = Suppliers.memoize(() -> PreviewTrees.of(registries, seed));
        return preview -> trees.get().decorate(preview.chunk());
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
        final PreviewGenerator.Generated generated = this.generator.orElseThrow().generateWithBiomes(ChunkPos.unpack(chunkKey));
        if (PreviewChunks.trees) {
            this.decoration.decorate(generated);
        }
        final ProtoChunk chunk = generated.chunk();
        PreviewSnow.freeze(chunk, this.seed, this.seaLevel);
        final PreviewPacket built = PreviewPacket.of(chunk);
        this.recent.put(chunkKey, built);
        return built;
    }

    /** Trees and plants on a generated preview, on the thread that generated it. */
    @FunctionalInterface
    private interface Decoration {
        Decoration NONE = preview -> {
        };

        void decorate(PreviewGenerator.Generated preview);
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
