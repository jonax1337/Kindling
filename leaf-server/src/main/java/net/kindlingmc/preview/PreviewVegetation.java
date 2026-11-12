package net.kindlingmc.preview;

import com.mojang.logging.LogUtils;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.dreeam.leaf.config.modules.misc.SecureSeed;
import org.slf4j.Logger;
import su.plo.matter.Globals;
import su.plo.matter.WorldgenCryptoRandom;

/**
 * Vanilla's own surface features on a preview chunk: the {@link #STEPS} of ChunkGenerator.addVanillaDecorations that
 * shape the surface (the End's small islands, lakes, rocks, icebergs, ice spikes, desert wells, the Nether's basalt
 * pillars and deltas, then the vegetation), with the same features in the same order, the same decoration and feature
 * seeds (including Paper's configured feature seeds and Leaf's secure seed) and the same biome check, through a {@link PreviewRegion} over the chunk. Underground features of these steps
 * (lava lakes, geodes) run too; they are rare and hidden in the stone below the preview's band. Inside the chunk the
 * features land where the real world has them, as far as the chunk alone decides that:
 * <ul>
 * <li>The blocks neighbouring chunks' features place in this chunk are missing (tree crowns and patches of grass near
 * the edges), and what this chunk's features place in its neighbours is not shown.
 * <li>The neighbours are {@link Surroundings} standing in for the real ones. Wherever a feature's random draws depend on
 * what it finds there, or on what neighbours placed in this chunk before it, the rest of that feature in the chunk
 * lands elsewhere than in the real world. In the real world that, too, depends on the order chunks were generated in.
 * <li>Structures (villages and the like), ores, springs, carvers and post-processing do not run, so what they change on
 * the surface, and the vegetation that depends on it, differs.
 * <li>A feature that fails, for instance because it needs the server level, is left out of the rest of that chunk and
 * reported once; its blocks placed so far stay, and it runs (and fails) again on the next chunk. Known cases:
 * pale_garden_vegetation, whose PaleMossDecorator asks the server level for its chunk source, and the Nether's
 * small_basalt_columns and large_basalt_columns, whose height predicate (WorldGenerationContext.of) asks it for the
 * generator; that needs a change to vanilla and is not made here.
 * </ul>
 *
 * <p>Thread safety: every field is immutable after construction and {@link #decorate} keeps its state in locals, so
 * several threads may decorate different chunks at once. The features, their placement and the generator's feature
 * lists are the objects the server's own chunk workers share the same way.
 */
final class PreviewVegetation {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * The steps whose features shape the surface, in vanilla's order: the End's small islands (vanilla's overworld and
     * Nether have no raw generation), lakes, rocks and icebergs, ice spikes and desert wells, then the vegetation,
     * which so avoids the lakes and rocks as in the real world. Each feature has a seed of its own, so the steps left
     * out (underground structures, ores, springs, and the Nether's glowstone and fire) do not move what these place.
     */
    private static final List<GenerationStep.Decoration> STEPS = List.of(GenerationStep.Decoration.RAW_GENERATION, GenerationStep.Decoration.LAKES,
        GenerationStep.Decoration.LOCAL_MODIFICATIONS, GenerationStep.Decoration.SURFACE_STRUCTURES, GenerationStep.Decoration.VEGETAL_DECORATION);
    private static final long NO_CONFIGURED_SEED = -1L;

    private final ChunkGenerator generator;
    private final PreviewRegion.World world;
    private final List<StepFeatures> steps;
    /** What Globals.setupGlobals sets per thread for Secure Seed: the level's index among the server's levels. */
    private final int secureSeedDimension;
    private final Set<PlacedFeature> failedFeatures = ConcurrentHashMap.newKeySet();

    private PreviewVegetation(ChunkGenerator generator, PreviewRegion.World world, List<StepFeatures> steps, int secureSeedDimension) {
        this.generator = generator;
        this.world = world;
        this.steps = steps;
        this.secureSeedDimension = secureSeedDimension;
    }

    /** Empty, with a warning, when the level's generator does not give out its feature lists. */
    static Optional<PreviewVegetation> forLevel(ServerLevel level) {
        PreviewRegion.World world = new PreviewRegion.World(level.getSeed(), level.getSeaLevel(), level.registryAccess(),
            level.dimensionType(), level.enabledFeatures(), level.getChunkSource().randomState(), level.palettedContainerFactory());
        try {
            return Optional.of(of(level.getChunkSource().getGenerator(), world, level.paperConfig().featureSeeds.features,
                secureSeedDimension(level)));
        } catch (RuntimeException e) {
            LOGGER.warn("Preview chunks: vanilla vegetation is not available for {}, using approximate trees", level.dimension().identifier(), e);
            return Optional.empty();
        }
    }

    /** @param featureSeeds Paper's configured feature seeds; a feature without one is absent or maps to -1 */
    static PreviewVegetation of(ChunkGenerator generator, PreviewRegion.World world, Reference2LongMap<Holder<Feature>> featureSeeds) {
        return of(generator, world, featureSeeds, 0);
    }

    private static PreviewVegetation of(ChunkGenerator generator, PreviewRegion.World world, Reference2LongMap<Holder<Feature>> featureSeeds,
        int secureSeedDimension) {
        List<FeatureSorter.StepFeatureData> featuresPerStep = featuresPerStep(generator);
        List<StepFeatures> steps = new ArrayList<>();
        for (GenerationStep.Decoration step : STEPS) {
            // A step without features in this world (raw generation outside the End) is left out of the decoration statistics, too.
            if (step.ordinal() < featuresPerStep.size() && !featuresPerStep.get(step.ordinal()).features().isEmpty()) {
                steps.add(StepFeatures.of(step.ordinal(), featuresPerStep.get(step.ordinal()), generator, featureSeeds));
            }
        }
        return new PreviewVegetation(generator, world, List.copyOf(steps), secureSeedDimension);
    }

    /**
     * The dimension index Globals.setupGlobals computes; a level not yet among the server's levels (still being
     * constructed) gets the next free index, as there. The world seed it also copies is the same for every level and
     * already set by the server thread, so it is not written from here.
     */
    private static int secureSeedDimension(ServerLevel level) {
        if (!SecureSeed.enabled) {
            return 0;
        }
        Set<ResourceKey<Level>> levelKeys = level.getServer().levelKeys();
        int index = Iterables.indexOf(levelKeys, key -> key == level.dimension());
        return index == -1 ? levelKeys.size() : index;
    }

    /** Places the vegetation on a generated preview; call on the thread that generated it. */
    void decorate(PreviewGenerator.Generated preview) {
        ProtoChunk chunk = preview.chunk();
        if (SecureSeed.enabled) {
            Globals.dimension.set(this.secureSeedDimension);
        }
        prepareForFeatures(chunk);
        PreviewRegion region = new PreviewRegion(this.world, chunk, preview.biomes());
        BlockPos origin = new BlockPos(chunk.getPos().getMinBlockX(), chunk.getMinY(), chunk.getPos().getMinBlockZ());
        WorldgenRandom random = decorationRandom(origin);
        long decorationSeed = random.setDecorationSeed(this.world.seed(), origin.getX(), origin.getZ());
        Set<Holder<Biome>> biomes = preview.biomes().possibleBiomes();
        for (StepFeatures step : this.steps) {
            long startNanos = System.nanoTime();
            BitSet features = step.featuresOf(biomes);
            for (int index = features.nextSetBit(0); index >= 0; index = features.nextSetBit(index + 1)) {
                random.setFeatureSeed(step.populationSeed(index, decorationSeed, random, origin), index, step.index());
                placeOrSkip(region, step.features().get(index), random, origin);
            }
            PreviewStats.recordDecorationStep(step.index(), System.nanoTime() - startNanos);
        }
    }

    /** The features that have failed on a preview of this world so far, each reported once in the log. */
    Set<PlacedFeature> failedFeatures() {
        return Set.copyOf(this.failedFeatures);
    }

    /** As the TERRAIN status leaves a chunk: the final heightmaps primed, and kept up to date by every block placed. */
    private static void prepareForFeatures(ProtoChunk chunk) {
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
        chunk.setPersistedStatus(ChunkStatus.TERRAIN);
    }

    /** The random of addVanillaDecorations; setDecorationSeed reseeds it, so its first seed does not matter. */
    private static WorldgenRandom decorationRandom(BlockPos origin) {
        if (SecureSeed.enabled) {
            return new WorldgenCryptoRandom(origin.getX(), origin.getZ(), Globals.Salt.UNDEFINED, 0);
        }
        return new WorldgenRandom(new XoroshiroRandomSource(0L));
    }

    /** A fresh placer per feature, so that a feature failing halfway leaves no positions behind for the next one. */
    private void placeOrSkip(PreviewRegion region, PlacedFeature feature, WorldgenRandom random, BlockPos origin) {
        try {
            new FeaturePlacer(region, this.generator).placeWithBiomeCheck(feature, random, origin);
        } catch (RuntimeException e) {
            reportOnce(feature, e);
        }
    }

    private void reportOnce(PlacedFeature feature, RuntimeException e) {
        if (this.failedFeatures.add(feature)) {
            String name = this.world.registries().lookupOrThrow(Registries.PLACED_FEATURE).getResourceKey(feature)
                .map(key -> key.identifier().toString()).orElseGet(feature::toString);
            LOGGER.warn("Preview chunks: feature {} failed in a preview; its remaining placements in such chunks are left out. "
                + "Further failures of it are not logged", name, e);
        }
    }

    /** Looked up per call (once per world), so that any failure reaches the caller's fallback instead of failing class initialisation. */
    @SuppressWarnings("unchecked")
    private static List<FeatureSorter.StepFeatureData> featuresPerStep(ChunkGenerator generator) {
        try {
            MethodHandle getter = MethodHandles.privateLookupIn(ChunkGenerator.class, MethodHandles.lookup())
                .findGetter(ChunkGenerator.class, "featuresPerStep", Supplier.class);
            return ((Supplier<List<FeatureSorter.StepFeatureData>>) getter.invoke(generator)).get();
        } catch (Throwable t) {
            throw new IllegalStateException("cannot read ChunkGenerator.featuresPerStep", t);
        }
    }

    /**
     * One generation step's features in the generator's order, whose index is the one vanilla seeds each feature with,
     * and which of them each biome has.
     */
    private record StepFeatures(int index, List<PlacedFeature> features, long[] configuredSeeds, Map<Holder<Biome>, BitSet> featuresOfBiome) {
        static StepFeatures of(int index, FeatureSorter.StepFeatureData data, ChunkGenerator generator,
            Reference2LongMap<Holder<Feature>> featureSeeds) {
            Map<Holder<Biome>, BitSet> featuresOfBiome = new IdentityHashMap<>();
            for (Holder<Biome> biome : generator.getBiomeSource().possibleBiomes()) {
                List<HolderSet<PlacedFeature>> steps = generator.generationSettingsGetter.apply(biome).features();
                if (index < steps.size()) {
                    BitSet features = new BitSet();
                    for (Holder<PlacedFeature> feature : steps.get(index)) {
                        features.set(data.indexMapping().applyAsInt(feature.value()));
                    }
                    featuresOfBiome.put(biome, features);
                }
            }
            long[] configuredSeeds = new long[data.features().size()];
            for (int i = 0; i < configuredSeeds.length; i++) {
                configuredSeeds[i] = featureSeeds.getOrDefault(data.features().get(i).feature(), NO_CONFIGURED_SEED);
            }
            return new StepFeatures(index, data.features(), configuredSeeds, featuresOfBiome);
        }

        /** The features any of the biomes has, by index: what addVanillaDecorations sorts and walks. */
        BitSet featuresOf(Set<Holder<Biome>> biomes) {
            BitSet features = new BitSet();
            for (Holder<Biome> biome : biomes) {
                BitSet ofBiome = this.featuresOfBiome.get(biome);
                if (ofBiome != null) {
                    features.or(ofBiome);
                }
            }
            return features;
        }

        /** The decoration seed, or for a feature with a configured seed the decoration seed derived from it, as Paper does. */
        long populationSeed(int feature, long decorationSeed, WorldgenRandom random, BlockPos origin) {
            long configured = this.configuredSeeds[feature];
            return configured == NO_CONFIGURED_SEED ? decorationSeed : random.setDecorationSeed(configured, origin.getX(), origin.getZ());
        }
    }
}
