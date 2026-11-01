package net.kindlingmc.preview;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OverlayFeature;
import net.minecraft.world.level.levelgen.feature.RandomBooleanSelectorFeature;
import net.minecraft.world.level.levelgen.feature.RandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.SequenceFeature;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.WeightedRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.AcaciaFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.DarkOakFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaJungleFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaPineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PoplarFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.RandomSpreadFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.SpruceFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.DarkOakTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RandomChancePlacement;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.minecraft.world.level.levelgen.placement.SurfaceWaterDepthFilter;
import org.slf4j.Logger;

/**
 * Approximate trees for a preview chunk, so that a forest seen from far away reads as a forest of the right kind and
 * density. Not the trees a full chunk would grow: positions, heights and shapes differ, and nothing outside the chunk
 * is looked at.
 *
 * <p>Every biome's trees are derived once from its vegetal-decoration features. Each placed feature contributes the
 * trees it would plant per chunk on average (count, rarity and chance modifiers, and the shares of the random selectors
 * around the tree), and each tree configuration becomes a {@link Style}: log and leaf blocks, trunk height, and a crown
 * shape taken from the foliage placer. Other features (fallen trees, bushes, huge mushrooms, bamboo) are ignored.
 *
 * <p>A chunk is decorated by trying {@link #CANDIDATES} random columns. A column is accepted with the probability that
 * makes the accepted count equal the biome's expected tree count at that column, and then plants one of the biome's
 * styles. A tree grows on a dirt-like block with air above (a tree with a water depth filter, as in swamps, may stand in shallow water),
 * and is moved inward so that its crown stays inside the chunk. Everything is seeded from the world seed and the chunk
 * position, so the same chunk always gets the same trees.
 *
 * <p>Thread safety: after construction every field is immutable and {@link #decorate} keeps its state in locals, so
 * different chunks may be decorated concurrently.
 */
final class PreviewTrees {
    /** Columns tried per chunk; a biome expecting more trees per chunk than this gets this many at most. */
    private static final int CANDIDATES = 64;
    /** Crown radius limit: a crown of this radius around a 2 x 2 trunk exactly fills a chunk. */
    private static final int MAX_CROWN_RADIUS = 7;
    private static final int MEAN_SAMPLES = 1024;
    private static final int MEAN_SAMPLE_RANGE = 4096;
    private static final int MAX_NESTING = 8;
    private static final int MEGA_PINE_RADIUS = 4;
    /** Stands for a placement chain without a water depth filter: such trees grow on dry land only. */
    private static final int NO_WATER_FILTER = -1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long STYLE_SEED = 0x7265657374L;
    private static final long CHUNK_SALT = 0x54726565L;

    private final long seed;
    private final Map<ResourceKey<Biome>, BiomeTrees> biomes;

    private PreviewTrees(long seed, Map<ResourceKey<Biome>, BiomeTrees> biomes) {
        this.seed = seed;
        this.biomes = biomes;
    }

    static PreviewTrees forLevel(ServerLevel level) {
        return of(level.registryAccess(), level.getSeed());
    }

    static PreviewTrees of(RegistryAccess registries, long seed) {
        TreeCatalog catalog = new TreeCatalog();
        Map<ResourceKey<Biome>, BiomeTrees> biomes = new HashMap<>();
        registries.lookupOrThrow(Registries.BIOME).listElements().forEach(biome -> {
            BiomeTrees trees = treesOfOrNone(catalog, biome);
            if (trees.expectedCount() > 0) {
                biomes.put(biome.key(), trees);
            }
        });
        return new PreviewTrees(seed, biomes);
    }

    /** A biome whose features cannot be read gets no trees; the others are not affected. */
    private static BiomeTrees treesOfOrNone(TreeCatalog catalog, Holder.Reference<Biome> biome) {
        try {
            return catalog.treesOf(biome.value());
        } catch (RuntimeException e) {
            LOGGER.warn("Preview trees: skipping the trees of biome {}", biome.key().identifier(), e);
            BiomeTrees none = new BiomeTrees();
            none.seal();
            return none;
        }
    }

    /** Adds the approximate trees to a finished preview chunk. */
    void decorate(ProtoChunk chunk) {
        RandomSource random = RandomSource.create(chunkSeed(chunk.getPos()));
        Planter planter = new Planter(chunk, random);
        for (int i = 0; i < CANDIDATES; i++) {
            int x = random.nextInt(16);
            int z = random.nextInt(16);
            BiomeTrees trees = this.biomes.get(planter.surfaceBiome(x, z));
            if (trees != null && random.nextDouble() * CANDIDATES < trees.expectedCount()) {
                planter.plant(trees.pick(random.nextDouble()));
            }
        }
    }

    /** Ground a tree may stand on: what vegetation, saplings included, survives on (grass, dirt, podzol, mud and the like). */
    static boolean isSoil(BlockState state) {
        return state.is(BlockTags.SUPPORTS_VEGETATION) || state.is(BlockTags.DIRT);
    }

    /** The expected number of trees per chunk in a biome, as the style table of {@link #describe} sums them. */
    double expectedCount(ResourceKey<Biome> biome) {
        BiomeTrees trees = this.biomes.get(biome);
        return trees == null ? 0 : trees.expectedCount();
    }

    /** The biome's styles with their expected trees per chunk, for logging and tests. */
    String describe(ResourceKey<Biome> biome) {
        BiomeTrees trees = this.biomes.get(biome);
        return trees == null ? "no trees" : trees.describe();
    }

    private long chunkSeed(ChunkPos pos) {
        return this.seed ^ pos.x() * 341873128712L ^ pos.z() * 132897987541L ^ CHUNK_SALT;
    }

    /** What a tree looks like and how a crown is stacked; see {@link Crown#of}. */
    private enum Shape {
        /** The vanilla blob: rows widen downward by one block every two rows. */
        BLOB(false) {
            @Override
            int radiusAt(int radius, int rows, int row) {
                return Math.max(radius - 1 + row / 2, 0);
            }
        },
        /** Spruce and pine: a point at the top widening to the full radius at the bottom row. */
        CONE(false) {
            @Override
            int radiusAt(int radius, int rows, int row) {
                return row * radius / Math.max(rows - 1, 1);
            }
        },
        /** Acacia: a flat umbrella of two rows. */
        FLAT(false) {
            @Override
            int radiusAt(int radius, int rows, int row) {
                return row == 0 ? radius : radius + 1;
            }
        },
        /** Cherry, dark oak, jungle, poplar and the like: an ellipsoid. */
        ROUND(true) {
            @Override
            int radiusAt(int radius, int rows, int row) {
                double fromMiddle = (row - (rows - 1) / 2.0) / (rows / 2.0);
                return (int) Math.round(radius * Math.sqrt(1 - fromMiddle * fromMiddle));
            }
        };

        private final boolean rounded;

        Shape(boolean rounded) {
            this.rounded = rounded;
        }

        /** The radius of the row-th row from the top, before the crown limit. */
        abstract int radiusAt(int radius, int rows, int row);

        /** Whether a column that far outside the trunk (in blocks along each axis) belongs to a row of that radius. */
        boolean covers(int radius, int outX, int outZ) {
            if (this.rounded) {
                return 2 * (outX * outX + outZ * outZ) <= 2 * radius * radius + radius;
            }
            return outX <= radius && outZ <= radius && !(outX == radius && outZ == radius && radius > 0);
        }
    }

    /**
     * The leaves of one tree: {@code rows} rows from {@code top} blocks above the foliage attachment point (the block
     * above the trunk) downward, each of the radius its shape gives.
     */
    private record Crown(Shape shape, int radius, int rows, int top) {
        Crown {
            radius = Mth.clamp(radius, 0, MAX_CROWN_RADIUS);
            rows = Math.max(rows, 1);
        }

        /** The crown a foliage placer would grow from this sampled radius and foliage height. */
        static Crown of(FoliagePlacer placer, int radius, int height) {
            if (placer instanceof SpruceFoliagePlacer || placer instanceof PineFoliagePlacer) {
                return new Crown(Shape.CONE, Math.min(radius, height), height + 1, 0);
            }
            if (placer instanceof MegaPineFoliagePlacer) {
                return new Crown(Shape.CONE, MEGA_PINE_RADIUS, height + 1, 0);
            }
            if (placer instanceof AcaciaFoliagePlacer) {
                return new Crown(Shape.FLAT, radius, 2, 0);
            }
            if (placer instanceof FancyFoliagePlacer) {
                return new Crown(Shape.ROUND, radius + 2, height + 2, 1);
            }
            if (placer instanceof CherryFoliagePlacer) {
                return new Crown(Shape.ROUND, radius, height, height - 2);
            }
            if (placer instanceof DarkOakFoliagePlacer) {
                return new Crown(Shape.ROUND, radius + 3, 4, 1);
            }
            if (placer instanceof MegaJungleFoliagePlacer) {
                return new Crown(Shape.ROUND, radius + 2, height + 2, 1);
            }
            if (placer instanceof RandomSpreadFoliagePlacer) {
                return new Crown(Shape.ROUND, radius, 2 * height, height);
            }
            if (placer instanceof PoplarFoliagePlacer) {
                return new Crown(Shape.ROUND, radius - 1, height + 1, height - 1);
            }
            return new Crown(Shape.BLOB, radius, height + 1, 0);
        }

        int radiusAt(int row) {
            return Mth.clamp(this.shape.radiusAt(this.radius, this.rows, row), 0, MAX_CROWN_RADIUS);
        }

        int widestRadius() {
            int widest = 0;
            for (int row = 0; row < this.rows; row++) {
                widest = Math.max(widest, radiusAt(row));
            }
            return widest;
        }
    }

    /** One tree configuration reduced to what a preview draws. */
    private static final class Style {
        private final TreeFeature tree;
        private final BlockState log;
        private final BlockState leaves;
        private final int trunkWidth;
        /** How deep the water above its ground may be: 0 for dry land. */
        private final int waterDepth;

        Style(TreeFeature tree, int waterDepth) {
            RandomSource random = RandomSource.create(STYLE_SEED);
            this.tree = tree;
            this.log = fixedState(tree.trunkProvider().value(), random, Blocks.OAK_LOG.defaultBlockState());
            this.leaves = fixedState(tree.foliageProvider().value(), random, Blocks.OAK_LEAVES.defaultBlockState());
            this.trunkWidth = tree.trunkPlacer() instanceof GiantTrunkPlacer || tree.trunkPlacer() instanceof DarkOakTrunkPlacer ? 2 : 1;
            this.waterDepth = waterDepth;
        }

        int sampleHeight(RandomSource random) {
            return Math.max(this.tree.trunkPlacer().getTreeHeight(random), 1);
        }

        Crown sampleCrown(RandomSource random, int trunkHeight) {
            FoliagePlacer placer = this.tree.foliagePlacer();
            return Crown.of(placer, placer.foliageRadius(random, trunkHeight), placer.foliageHeight(random, trunkHeight, this.tree));
        }

        /** Providers that read the world (noise, rotation) are not worth resolving for an approximation. */
        private static BlockState fixedState(BlockStateProvider provider, RandomSource random, BlockState fallback) {
            if (provider instanceof SimpleStateProvider simple) {
                return simple.state();
            }
            if (provider instanceof WeightedStateProvider weighted) {
                return weighted.weightedList().getRandomOrThrow(random);
            }
            return fallback;
        }

        @Override
        public String toString() {
            return name(this.log) + "/" + name(this.leaves) + " " + this.tree.foliagePlacer().getClass().getSimpleName()
                + (this.trunkWidth == 2 ? " 2x2" : "") + (this.waterDepth > 0 ? " water<=" + this.waterDepth : "");
        }

        private static String name(BlockState state) {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        }
    }

    /** The styles a biome plants with the trees each is expected to add per chunk. */
    private static final class BiomeTrees {
        private final List<Style> styles = new ArrayList<>();
        private final List<Double> expectedCounts = new ArrayList<>();
        private double[] cumulative = new double[0];

        void add(Style style, double count) {
            for (int i = 0; i < this.styles.size(); i++) {
                if (this.styles.get(i) == style) {
                    this.expectedCounts.set(i, this.expectedCounts.get(i) + count);
                    return;
                }
            }
            this.styles.add(style);
            this.expectedCounts.add(count);
        }

        double expectedCount() {
            double sum = 0;
            for (double count : this.expectedCounts) {
                sum += count;
            }
            return sum;
        }

        /** Ends the building; the object is then only read, by any number of threads. */
        void seal() {
            this.cumulative = cumulativeCounts();
        }

        /** The style at the given point (0 to 1) of the cumulative shares. */
        Style pick(double point) {
            double target = point * this.cumulative[this.cumulative.length - 1];
            int index = 0;
            while (index < this.cumulative.length - 1 && target >= this.cumulative[index]) {
                index++;
            }
            return this.styles.get(index);
        }

        String describe() {
            StringBuilder text = new StringBuilder(String.format("%.2f trees:", expectedCount()));
            for (int i = 0; i < this.styles.size(); i++) {
                text.append(String.format(" [%.2f %s]", this.expectedCounts.get(i), this.styles.get(i)));
            }
            return text.toString();
        }

        private double[] cumulativeCounts() {
            double[] sums = new double[this.expectedCounts.size()];
            double running = 0;
            for (int i = 0; i < sums.length; i++) {
                running += this.expectedCounts.get(i);
                sums[i] = running;
            }
            return sums;
        }
    }

    /**
     * Reads the vegetal-decoration features of biomes. Keeps the style of every tree configuration it has met (one per
     * allowed water depth) and the mean of every repeating placement, so that the numbers do not depend on the order in
     * which biomes are read.
     */
    private static final class TreeCatalog {
        private final Map<TreeFeature, Map<Integer, Optional<Style>>> styles = new IdentityHashMap<>();
        private final Map<RepeatingPlacement, Double> means = new IdentityHashMap<>();

        BiomeTrees treesOf(Biome biome) {
            BiomeTrees trees = new BiomeTrees();
            List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
            int step = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
            if (step < steps.size()) {
                collectAll(steps.get(step), 1, NO_WATER_FILTER, trees, 0);
            }
            trees.seal();
            return trees;
        }

        private void collectAll(Iterable<Holder<PlacedFeature>> placedFeatures, double share, int waterDepth, BiomeTrees trees, int nesting) {
            for (Holder<PlacedFeature> placed : placedFeatures) {
                collect(placed.value(), share, waterDepth, trees, nesting);
            }
        }

        /**
         * Adds the trees a placed feature plants per chunk, if it is a tree or a selector of trees. The water depth is
         * the strictest depth filter of the placed features around the tree, or NO_WATER_FILTER.
         */
        private void collect(PlacedFeature placed, double share, int inheritedWaterDepth, BiomeTrees trees, int nesting) {
            if (nesting > MAX_NESTING) {
                return;
            }
            double count = share * placementMultiplier(placed.placement());
            int waterDepth = strictest(inheritedWaterDepth, waterDepthFilter(placed.placement()));
            int deeper = nesting + 1;
            Feature feature = placed.feature().value();
            if (feature instanceof TreeFeature tree) {
                styleOf(tree, waterDepth).ifPresent(style -> trees.add(style, count));
            } else if (feature instanceof RandomSelectorFeature selector) {
                double remaining = count;
                for (WeightedPlacedFeature candidate : selector.features()) {
                    collect(candidate.feature().value(), remaining * candidate.chance(), waterDepth, trees, deeper);
                    remaining *= 1 - candidate.chance();
                }
                collect(selector.defaultFeature().value(), remaining, waterDepth, trees, deeper);
            } else if (feature instanceof RandomBooleanSelectorFeature selector) {
                collect(selector.featureTrue().value(), count / 2, waterDepth, trees, deeper);
                collect(selector.featureFalse().value(), count / 2, waterDepth, trees, deeper);
            } else if (feature instanceof SimpleRandomSelectorFeature selector) {
                collectAll(selector.features(), count / selector.features().size(), waterDepth, trees, deeper);
            } else if (feature instanceof WeightedRandomSelectorFeature selector) {
                collectWeighted(selector, count, waterDepth, trees, deeper);
            } else if (feature instanceof SequenceFeature sequence) {
                collectAll(sequence.features(), count, waterDepth, trees, deeper);
            } else if (feature instanceof OverlayFeature overlay) {
                collectAll(overlay.features(), count, waterDepth, trees, deeper);
            }
        }

        private void collectWeighted(WeightedRandomSelectorFeature selector, double count, int waterDepth, BiomeTrees trees, int nesting) {
            int totalWeight = 0;
            for (Weighted<Holder<PlacedFeature>> entry : selector.features().unwrap()) {
                totalWeight += entry.weight();
            }
            for (Weighted<Holder<PlacedFeature>> entry : selector.features().unwrap()) {
                collect(entry.value().value(), count * entry.weight() / totalWeight, waterDepth, trees, nesting);
            }
        }

        /** The style of a tree configuration for a water depth; empty, and logged once, when the tree cannot be read. */
        private Optional<Style> styleOf(TreeFeature tree, int waterDepth) {
            int allowedDepth = Math.max(waterDepth, 0);
            return this.styles.computeIfAbsent(tree, key -> new HashMap<>()).computeIfAbsent(allowedDepth, depth -> {
                try {
                    return Optional.of(new Style(tree, depth));
                } catch (RuntimeException e) {
                    LOGGER.warn("Preview trees: skipping a tree with trunk placer {} and foliage placer {}",
                        tree.trunkPlacer().getClass().getSimpleName(), tree.foliagePlacer().getClass().getSimpleName(), e);
                    return Optional.empty();
                }
            });
        }

        private static int waterDepthFilter(List<PlacementModifier> modifiers) {
            int depth = NO_WATER_FILTER;
            for (PlacementModifier modifier : modifiers) {
                if (modifier instanceof SurfaceWaterDepthFilter filter) {
                    depth = strictest(depth, filter.maxWaterDepth());
                }
            }
            return depth;
        }

        private static int strictest(int first, int second) {
            if (first == NO_WATER_FILTER) {
                return second;
            }
            return second == NO_WATER_FILTER ? first : Math.min(first, second);
        }

        /** How many times a placement chain is expected to run its feature per chunk: counts times rarity and chance. */
        private double placementMultiplier(List<PlacementModifier> modifiers) {
            double multiplier = 1;
            for (PlacementModifier modifier : modifiers) {
                if (modifier instanceof RepeatingPlacement repeating) {
                    multiplier *= this.means.computeIfAbsent(repeating, TreeCatalog::meanCount);
                } else if (modifier instanceof RarityFilter rarity) {
                    multiplier /= rarity.chance();
                } else if (modifier instanceof RandomChancePlacement chance) {
                    multiplier *= chance.chance();
                }
            }
            return multiplier;
        }

        /**
         * Averages the count over positions spread across the world, as noise-based counts depend on the position. Every
         * placement is sampled with its own fresh random, and negative counts are zero, as for vanilla.
         */
        private static double meanCount(RepeatingPlacement placement) {
            RandomSource random = RandomSource.create(STYLE_SEED);
            long sum = 0;
            for (int i = 0; i < MEAN_SAMPLES; i++) {
                BlockPos origin = new BlockPos(
                    random.nextInt(2 * MEAN_SAMPLE_RANGE) - MEAN_SAMPLE_RANGE, 64, random.nextInt(2 * MEAN_SAMPLE_RANGE) - MEAN_SAMPLE_RANGE);
                sum += Math.max(placement.count(random, origin), 0);
            }
            return (double) sum / MEAN_SAMPLES;
        }
    }

    /** The trees of one chunk: reads the chunk's terrain and writes blocks straight into its sections. */
    private static final class Planter {
        private final ProtoChunk chunk;
        private final RandomSource random;
        private final int minBlockX;
        private final int minBlockZ;

        Planter(ProtoChunk chunk, RandomSource random) {
            this.chunk = chunk;
            this.random = random;
            this.minBlockX = chunk.getPos().getMinBlockX();
            this.minBlockZ = chunk.getPos().getMinBlockZ();
        }

        /** The key of the biome at the surface of a column; null for a biome that is not in a registry. */
        ResourceKey<Biome> surfaceBiome(int x, int z) {
            int quartY = QuartPos.fromBlock(groundAt(x, z));
            Holder<Biome> biome = this.chunk.getNoiseBiome(QuartPos.fromBlock(this.minBlockX + x), quartY, QuartPos.fromBlock(this.minBlockZ + z));
            return biome instanceof Holder.Reference<Biome> reference ? reference.key() : null;
        }

        /**
         * Grows one tree at a random column whose crown fits in the chunk, if the ground allows it. The column is drawn
         * uniformly from those that fit, so that trees neither pile up at the chunk edges nor all crowd the middle.
         */
        void plant(Style style) {
            int trunkHeight = style.sampleHeight(this.random);
            Crown crown = style.sampleCrown(this.random, trunkHeight);
            int reach = crown.widestRadius();
            int width = style.trunkWidth;
            int span = 16 - 2 * reach - (width - 1);
            if (span <= 0) {
                return;
            }
            int baseX = reach + this.random.nextInt(span);
            int baseZ = reach + this.random.nextInt(span);
            int groundY = lowestGround(baseX, baseZ, width);
            int attachY = groundY + 1 + trunkHeight;
            if (groundY < this.chunk.getMinY() || attachY + crown.top() > this.chunk.getMaxY()
                || !canGrow(style, baseX, baseZ)) {
                return;
            }
            placeTrunk(style, baseX, baseZ, groundY, trunkHeight);
            placeCrown(style, crown, baseX, baseZ, attachY);
        }

        /** Every column of the trunk stands on soil with free space above it (water too, as deep as the style allows). */
        private boolean canGrow(Style style, int baseX, int baseZ) {
            for (int dx = 0; dx < style.trunkWidth; dx++) {
                for (int dz = 0; dz < style.trunkWidth; dz++) {
                    int groundY = groundAt(baseX + dx, baseZ + dz);
                    if (!isSoil(blockAt(baseX + dx, groundY, baseZ + dz)) || !hasRoomAbove(style, baseX + dx, baseZ + dz, groundY)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean hasRoomAbove(Style style, int x, int z, int groundY) {
            int depth = this.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - groundY;
            if (depth > style.waterDepth) {
                return false;
            }
            BlockState above = blockAt(x, groundY + 1, z);
            return above.isAir() || depth > 0 && above.is(Blocks.WATER);
        }

        /** Each trunk column rises from its own ground to the top set by the lowest ground. */
        private void placeTrunk(Style style, int baseX, int baseZ, int lowestGroundY, int trunkHeight) {
            for (int dx = 0; dx < style.trunkWidth; dx++) {
                for (int dz = 0; dz < style.trunkWidth; dz++) {
                    for (int y = groundAt(baseX + dx, baseZ + dz) + 1; y <= lowestGroundY + trunkHeight; y++) {
                        place(baseX + dx, y, baseZ + dz, style.log, style.waterDepth > 0);
                    }
                }
            }
        }

        private void placeCrown(Style style, Crown crown, int baseX, int baseZ, int attachY) {
            int width = style.trunkWidth;
            for (int row = 0; row < crown.rows(); row++) {
                int y = attachY + crown.top() - row;
                int radius = crown.radiusAt(row);
                for (int dx = -radius; dx <= radius + width - 1; dx++) {
                    for (int dz = -radius; dz <= radius + width - 1; dz++) {
                        if (crown.shape().covers(radius, outside(dx, width), outside(dz, width))) {
                            place(baseX + dx, y, baseZ + dz, style.leaves, false);
                        }
                    }
                }
            }
        }

        /** How far an offset from the trunk's first column lies outside the trunk's columns. */
        private static int outside(int offset, int trunkWidth) {
            return offset < 0 ? -offset : Math.max(offset - (trunkWidth - 1), 0);
        }

        /** The y of the highest terrain block of a column: ChunkAccess.getHeight already answers the block, not the free block above it. */
        private int groundAt(int x, int z) {
            return this.chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        }

        private int lowestGround(int baseX, int baseZ, int width) {
            int lowest = Integer.MAX_VALUE;
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    lowest = Math.min(lowest, groundAt(baseX + dx, baseZ + dz));
                }
            }
            return lowest;
        }

        /** The block at a column and height of the chunk; air above and below the world. */
        private BlockState blockAt(int x, int y, int z) {
            if (isOutsideWorld(y)) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.chunk.getSection(this.chunk.getSectionIndex(y)).getBlockState(x, y & 15, z);
        }

        private boolean isOutsideWorld(int y) {
            return y < this.chunk.getMinY() || y > this.chunk.getMaxY();
        }

        /** Writes only into air (and water for logs of a tree that grows in water), so terrain and earlier trees stay. */
        private void place(int x, int y, int z, BlockState state, boolean intoWater) {
            if (isOutsideWorld(y)) {
                return;
            }
            LevelChunkSection section = this.chunk.getSection(this.chunk.getSectionIndex(y));
            BlockState old = section.getBlockState(x, y & 15, z);
            if (old.isAir() || intoWater && old.is(Blocks.WATER)) {
                section.setBlockState(x, y & 15, z, state, false);
            }
        }
    }
}
