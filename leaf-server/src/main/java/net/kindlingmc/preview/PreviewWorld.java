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
 * The previews of one world: its generator (none for worlds without a noise generator, such as flat worlds), the saved chunks it
 * previews from where they exist, and the most recently built seed preview packets, so that several players and a
 * player flying back reuse them. Safe to use from any thread; it never holds the world strongly.
 */
final class PreviewWorld {
    private static final Map<ServerLevel, PreviewWorld> WORLDS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Optional<PreviewGenerator> generator;
    /** Present with the generator; holds the world weakly. */
    private final Optional<SavedChunks> saved;
    /** Vanilla's vegetation, or where the world's generator does not allow it, the approximate trees. */
    private final Decoration decoration;
    private final long seed;
    private final int seaLevel;
    private final RecentPackets recent = new RecentPackets();

    private PreviewWorld(final Optional<PreviewGenerator> generator, final Optional<SavedChunks> saved, final Decoration decoration,
        final long seed, final int seaLevel) {
        this.generator = generator;
        this.saved = saved;
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
        final Optional<SavedChunks> saved = generator.map(previews -> new SavedChunks(level, previews.terrain()));
        return new PreviewWorld(generator, saved, decoration, level.getSeed(), level.getSeaLevel());
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

    /** Whether the world has a generator and a sky, so its previews carry sky light. */
    boolean hasSkyLight() {
        return this.generator.map(previews -> previews.terrain().shape().skyLight()).orElse(false);
    }

    /**
     * The preview of a chunk: the recent seed packet, else the chunk as saved when it is saved, else computed from the
     * seed now. A seed packet is cached only when the saved chunks lastingly have nothing to show for the chunk, so a
     * chunk whose saved read was only postponed gets its saved preview on a later request. Call off the tick thread
     * only when computing.
     */
    PreviewPacket preview(final long chunkKey) {
        final long startNanos = System.nanoTime();
        final Optional<PreviewPacket> known = this.recent.get(chunkKey);
        if (known.isPresent()) {
            return counted(PreviewStats.Source.SEED_FROM_CACHE, startNanos, known.get());
        }
        final ChunkPos pos = ChunkPos.unpack(chunkKey);
        final SavedOutcome saved = savedOutcome(pos);
        if (saved.packet().isPresent()) {
            return counted(PreviewStats.Source.SAVED, startNanos, saved.packet().get());
        }
        final PreviewPacket seed = PreviewWorkers.yieldingToGeneration(() -> seedPreview(pos, chunkKey, saved.seedPreviewMayBeCached()));
        return counted(PreviewStats.Source.SEED, startNanos, seed);
    }

    /** Records that the preview came from {@code source} and took until now, and returns it. */
    private static PreviewPacket counted(final PreviewStats.Source source, final long startNanos, final PreviewPacket packet) {
        PreviewStats.recordComputed(source, System.nanoTime() - startNanos);
        return packet;
    }

    private SavedOutcome savedOutcome(final ChunkPos pos) {
        if (!PreviewChunks.savedChunks) {
            return SavedOutcome.NONE_LASTING;
        }
        return this.saved.map(chunks -> chunks.preview(pos)).orElse(SavedOutcome.NONE_LASTING);
    }

    private PreviewPacket seedPreview(final ChunkPos pos, final long chunkKey, final boolean cache) {
        final ProtoChunk chunk = seedChunk(pos);
        final PreviewPacket built = PreviewPacket.of(chunk, this.generator.orElseThrow().terrain().shape().estimatedLight(chunk.getSections()));
        if (cache) {
            this.recent.put(chunkKey, built);
        }
        return built;
    }

    /**
     * The finished seed preview of a chunk, as the server sends it before the packet is built: generated with its
     * biomes, decorated when {@code trees} is on, frozen where it is cold.
     */
    ProtoChunk seedChunk(final ChunkPos pos) {
        final PreviewGenerator.Generated generated = this.generator.orElseThrow().generateWithBiomes(pos);
        if (PreviewChunks.trees) {
            this.decoration.decorate(generated);
        }
        final ProtoChunk chunk = generated.chunk();
        PreviewSnow.freeze(chunk, this.seed, this.seaLevel);
        return chunk;
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
