package net.kindlingmc.worldgen.density;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferArena;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.SliceFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.Normal;
import org.dreeam.leaf.config.modules.opt.CompiledDensity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The pruning evaluator against Paper's own sampler, bit for bit: the output of finalDensity for chunks, columns and
 * unaligned boxes of the game's routers, synthetic trees whose selectors see thresholds, NaN, signed zeros and
 * infinities, several threads on one compiled sampler, and the cache cells the aquifer, the surface and the material
 * rules read afterwards. Every buffer starts poisoned, so a value that is never written cannot pass.
 */
@Normal
class DensityPruningTest {
    private static final float POISON = Float.intBitsToFloat(0x7f7fdead);
    private static final int OVERWORLD_HEIGHT = 384;
    private static final Tuning[] TUNINGS = {Tuning.DEFAULT, new Tuning(0, 0), new Tuning(100_000, 8)};

    // ---- the game's routers --------------------------------------------------------------------------------------

    @Test
    void overworldChunksMatchPaper() {
        for (long seed : new long[] {0L, 12345L, -4172144997902289642L}) {
            World world = World.of(NoiseGeneratorSettings.OVERWORLD, seed);
            for (int[] chunk : new int[][] {{0, 0}, {5, -3}, {-17, 40}, {1000, -2000}, {1874999, -1874999}, {-31, -31}}) {
                Beardifier beardifier = (chunk[0] & 1) == 0 ? Beardifier.EMPTY : structureAt(chunk[0] * 16 + 8, 40, chunk[1] * 16 + 8);
                DensityVolume volume = new DensityVolume(16, OVERWORLD_HEIGHT, 16, chunk[0] * 16, -64, chunk[1] * 16);
                world.assertSameAsPaper(volume, beardifier, "seed " + seed + " chunk " + chunk[0] + " " + chunk[1]);
            }
        }
    }

    @Test
    void columnsAndUnalignedBoxesMatchPaper() {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 7L);
        SplittableRandom random = new SplittableRandom(3);
        for (int i = 0; i < 12; ++i) {
            int x = random.nextInt(-40_000, 40_000);
            int z = random.nextInt(-40_000, 40_000);
            world.assertSameAsPaper(new DensityVolume(1, OVERWORLD_HEIGHT, 1, x, -64, z), null, "column " + x + " " + z);
        }
        for (int i = 0; i < 12; ++i) {
            int sizeX = random.nextInt(1, 20);
            int sizeY = random.nextInt(1, 120);
            int sizeZ = random.nextInt(1, 20);
            DensityVolume box = new DensityVolume(sizeX, sizeY, sizeZ, random.nextInt(-5000, 5000), random.nextInt(-64, 320 - sizeY), random.nextInt(-5000, 5000));
            world.assertSameAsPaper(box, null, "box " + box);
        }
        world.assertSameAsPaper(new DensityVolume(5, 9, 5, -32, -64, 48, 4, 8, 4), null, "stepped box");
        world.assertSameAsPaper(new DensityVolume(1, 1, 1, 64, 0, -128), null, "single cell-aligned point");
    }

    @Test
    void otherDimensionsAndPresetsMatchPaper() {
        List<ResourceKey<NoiseGeneratorSettings>> presets = List.of(NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END,
            NoiseGeneratorSettings.AMPLIFIED, NoiseGeneratorSettings.LARGE_BIOMES, NoiseGeneratorSettings.CAVES, NoiseGeneratorSettings.FLOATING_ISLANDS);
        for (ResourceKey<NoiseGeneratorSettings> preset : presets) {
            World world = World.of(preset, 99L);
            NoiseGeneratorSettings settings = world.settings;
            for (int[] chunk : new int[][] {{0, 0}, {-9, 14}, {300, 77}}) {
                DensityVolume volume = new DensityVolume(16, settings.noiseSettings().height(), 16, chunk[0] * 16, settings.noiseSettings().minY(), chunk[1] * 16);
                world.assertSameAsPaper(volume, Beardifier.EMPTY, preset.identifier() + " chunk " + chunk[0] + " " + chunk[1]);
            }
        }
    }

    @Test
    void laterReadsOfTheContextMatchPaper() {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 12345L);
        CompiledFinalDensity compiled = assertInstanceOf(CompiledFinalDensity.class, world.compiled);
        assertTrue(compiled.caches.length > 0, "the overworld keeps cache cells below its interpolated functions");
        assertEquals(5, compiled.interpolated, "interpolated functions of the overworld's finalDensity");
        assertEquals(compiled.interpolated, compiled.corners, "every one of them is compiled");
        NoiseRouter router = world.settings.noiseRouter();
        List<DensityFunction> readers = List.of(router.continents(), router.erosion(), router.depth(), router.ridges(), router.chunkSurfaceLevel(),
            router.temperature(), router.vegetation());
        long completionsBefore = PendingCacheCell.completions();
        int pendingCells = 0;
        for (int[] chunk : new int[][] {{2, 3}, {-40, 7}, {123, -456}}) {
            DensityVolume volume = new DensityVolume(16, OVERWORLD_HEIGHT, 16, chunk[0] * 16, -64, chunk[1] * 16);
            SamplerContext paper = context(Beardifier.EMPTY);
            SamplerContext mine = context(Beardifier.EMPTY);
            // what the aquifer does first: other volumes on the same cells
            DensityVolume quarts = new DensityVolume(5, 1, 5, chunk[0] * 16, 0, chunk[1] * 16, 4, 1, 4);
            for (CachingDensitySampler cache : compiled.caches) {
                sameVolume(cache, paper, mine, quarts, "before, cache " + cache.id());
            }
            for (DensityFunction reader : readers) {
                sameVolume(world.randomState.getSampler(reader), paper, mine, quarts, "before, router function");
            }

            float[] expected = sample(compiled.paper, paper, volume);
            float[] actual = sample(compiled, mine, volume);
            assertBits(volume, expected, actual, "finalDensity of chunk " + chunk[0] + " " + chunk[1]);
            for (CachingDensitySampler cache : compiled.caches) {
                assertEquals(paper.kindling$cellVolume(cache.id()), mine.kindling$cellVolume(cache.id()), "cell of cache " + cache.id());
                PendingCacheCell pending = mine.kindling$pending(cache.id());
                pendingCells += pending != null && !pending.isComplete() ? 1 : 0;
            }

            // what the aquifer, the surface and the material rules do later: point and volume reads
            SplittableRandom random = new SplittableRandom(chunk[0] * 31L + chunk[1]);
            for (CachingDensitySampler cache : compiled.caches) {
                DensityVolume cell = paper.kindling$cellVolume(cache.id());
                if (cell != null) {
                    for (int i = 0; i < cell.size(); i += 7) {
                        int y = i % cell.sizeY();
                        int x = i / cell.sizeY() % cell.sizeX();
                        int z = i / cell.sizeY() / cell.sizeX();
                        samePoint(cache, paper, mine, cell.blockX(x), cell.blockY(y), cell.blockZ(z), "cache " + cache.id());
                    }
                    sameVolume(cache, paper, mine, cell, "cache " + cache.id() + " volume");
                }
                for (int i = 0; i < 16; ++i) {
                    samePoint(cache, paper, mine, chunk[0] * 16 + random.nextInt(16), random.nextInt(-64, 320), chunk[1] * 16 + random.nextInt(16), "cache " + cache.id());
                }
            }
            for (DensityFunction reader : readers) {
                DensitySampler sampler = world.randomState.getSampler(reader);
                for (int i = 0; i < 32; ++i) {
                    int x = chunk[0] * 16 + random.nextInt(4) * 4;
                    int z = chunk[1] * 16 + random.nextInt(4) * 4;
                    samePoint(sampler, paper, mine, x, random.nextInt(-64, 320), z, "router function");
                }
                sameVolume(sampler, paper, mine, quarts, "after, router function");
            }
            assertBits(volume, expected, sample(compiled, mine, volume), "finalDensity again on the used context");
        }
        assertTrue(pendingCells > 0, "pruning left some cells partly computed, so the completion path ran");
        assertTrue(PendingCacheCell.completions() > completionsBefore, "a later read completed a partly computed cell");
    }

    @Test
    void volumeReadOfAPendingCellCompletesItBeforeAnyPointRead() {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 12345L);
        CompiledFinalDensity compiled = assertInstanceOf(CompiledFinalDensity.class, world.compiled);
        int pendingCells = 0;
        for (int[] chunk : new int[][] {{2, 3}, {-40, 7}, {123, -456}}) {
            DensityVolume volume = new DensityVolume(16, OVERWORLD_HEIGHT, 16, chunk[0] * 16, -64, chunk[1] * 16);
            SamplerContext paper = context(Beardifier.EMPTY);
            SamplerContext mine = context(Beardifier.EMPTY);
            assertBits(volume, sample(compiled.paper, paper, volume), sample(compiled, mine, volume), "finalDensity of chunk " + chunk[0] + " " + chunk[1]);
            for (CachingDensitySampler cache : compiled.caches) {
                DensityVolume cell = mine.kindling$cellVolume(cache.id());
                PendingCacheCell pending = mine.kindling$pending(cache.id());
                if (cell == null || pending == null || pending.isComplete()) {
                    continue;
                }
                ++pendingCells;
                // no point read before: the volume read is the first thing that meets the partly computed cell
                sameVolume(cache, paper, mine, cell, "volume read of pending cache " + cache.id());
                assertNull(mine.kindling$pending(cache.id()), "the volume read completed the cell of cache " + cache.id());
            }
        }
        assertTrue(pendingCells > 0, "pruning left some cells partly computed, so the volume read had something to complete");
    }

    @Test
    void cachesInsideSkippedLeavesAreReplayedAndCompared() throws ReflectiveOperationException {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 0L);
        Noise noise = world.randomState.getOrCreateNoise(Noises.CAVE_CHEESE);
        // the selector is always in range, so the out-of-range branch with the wrapped cache is pruned
        DensitySampler hidden = new Wrapper(new CachingDensitySampler(9100, new NoiseFunction.Sampler(noise, 0.5, 0.25)));
        DensitySampler root = interpolated(rangeChoice(new ConstantFunction.Sampler(0.5F), new NoiseFunction.Sampler(noise, 1.5, 0.75), hidden));
        CompiledFinalDensity compiled = assertInstanceOf(CompiledFinalDensity.class, DensityPruning.compile(root, Tuning.DEFAULT));
        assertTrue(Arrays.stream(compiled.caches).anyMatch(cache -> cache.id() == 9100), "the cache inside the leaf is compared");
        for (DensityVolume volume : syntheticVolumes(new SplittableRandom(5))) {
            SamplerContext paper = context(null);
            SamplerContext mine = context(null);
            assertBits(volume, sample(root, paper, volume), sample(compiled, mine, volume), "volume " + volume);
            assertSameCells(compiled, paper, mine, "volume " + volume);
            assertTrue(mine.kindling$cellVolume(9100) != null, "the pruned leaf was replayed and filled its cache");
        }
    }

    @Test
    void cacheHitLeavesNestedCacheAsPaperDoes() throws ReflectiveOperationException {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 0L);
        Noise noise = world.randomState.getOrCreateNoise(Noises.CAVE_CHEESE);
        DensitySampler inner = new CachingDensitySampler(9201, new NoiseFunction.Sampler(noise, 0.5, 0.25));
        DensitySampler outer = new CachingDensitySampler(9200, new BinaryFunction.AddSampler(inner, new NoiseFunction.Sampler(noise, 1.5, 0.75)));
        DensitySampler root = interpolated(outer);
        CompiledFinalDensity compiled = assertInstanceOf(CompiledFinalDensity.class, DensityPruning.compile(root, Tuning.DEFAULT));
        DensityVolume volume = new DensityVolume(16, 48, 16, 64, -64, -32);
        DensityVolume quarts = new DensityVolume(5, 1, 5, 64, 0, -32, 4, 1, 4);
        SamplerContext paper = context(null);
        SamplerContext mine = context(null);
        sample(root, paper, volume);
        sample(root, mine, volume);
        // the outer cache now holds the corner volume, the inner one is evicted to another volume
        sample(inner, paper, quarts);
        sample(inner, mine, quarts);
        assertEquals(quarts, paper.kindling$cellVolume(9201), "Paper leaves the inner cache alone on a hit of the outer one");
        assertBits(volume, sample(root, paper, volume), sample(compiled, mine, volume), "second call on the same context");
        assertSameCells(compiled, paper, mine, "second call on the same context");
    }

    @Test
    void severalThreadsShareOneCompiledSampler() throws Exception {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 42L);
        List<DensityVolume> volumes = new ArrayList<>();
        for (int i = 0; i < 6; ++i) {
            volumes.add(new DensityVolume(16, OVERWORLD_HEIGHT, 16, (i * 37 - 90) * 16, -64, (i * 53 - 70) * 16));
        }
        List<float[]> expected = new ArrayList<>();
        for (DensityVolume volume : volumes) {
            expected.add(sample(world.paper, context(Beardifier.EMPTY), volume));
        }
        ExecutorService threads = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int thread = 0; thread < 4; ++thread) {
                int offset = thread;
                runs.add(threads.submit(() -> {
                    for (int round = 0; round < 3; ++round) {
                        for (int i = 0; i < volumes.size(); ++i) {
                            int index = (i + offset + round) % volumes.size();
                            DensityVolume volume = volumes.get(index);
                            assertBits(volume, expected.get(index), sample(world.compiled, context(Beardifier.EMPTY), volume), "thread " + offset);
                        }
                    }
                }));
            }
            for (Future<?> run : runs) {
                run.get();
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void verifyModeComparesAndKeepsPaper() {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 5L);
        CheckedDensitySampler checked = new CheckedDensitySampler(assertInstanceOf(CompiledFinalDensity.class, world.compiled));
        long[] before = CheckedDensitySampler.counts();
        DensityVolume volume = new DensityVolume(16, OVERWORLD_HEIGHT, 16, 160, -64, -320);
        assertBits(volume, sample(world.paper, context(Beardifier.EMPTY), volume), sample(checked, context(Beardifier.EMPTY), volume), "verify mode output");
        long[] after = CheckedDensitySampler.counts();
        assertEquals(before[0] + 1, after[0]);
        assertEquals(before[1], after[1], "no difference found");
    }

    @Test
    void settingOffHandsOutPaperSampler() {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 1L);
        boolean enabled = CompiledDensity.enabled;
        CompiledDensity.enabled = false;
        try {
            assertSame(world.paper, DensityPruning.finalDensity(world.paper));
        } finally {
            CompiledDensity.enabled = enabled;
        }
        assertSame(world.paper, world.randomState.getSampler(world.settings.noiseRouter().finalDensity()), "kept from the first request");
    }

    @Test
    void settingIsOnByDefault() {
        assertTrue(CompiledDensity.enabled, "on by default");
        Registry<NoiseGeneratorSettings> presets = RegistryHelper.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings settings = presets.getValueOrThrow(NoiseGeneratorSettings.OVERWORLD);
        RandomState randomState = RandomState.create(RegistryHelper.registryAccess().lookupOrThrow(Registries.NOISE), 2L, settings);
        assertInstanceOf(CompiledFinalDensity.class, randomState.getSampler(settings.noiseRouter().finalDensity()));
    }

    // ---- synthetic trees -----------------------------------------------------------------------------------------

    @Test
    void syntheticSelectorTreesMatchPaper() throws ReflectiveOperationException {
        World world = World.of(NoiseGeneratorSettings.OVERWORLD, 0L);
        Noise noise = world.randomState.getOrCreateNoise(Noises.CAVE_CHEESE);
        SplittableRandom random = new SplittableRandom(11);
        int compiledTrees = 0;
        for (int tree = 0; tree < 120; ++tree) {
            Synthetic synthetic = new Synthetic(random, noise, tree * 10);
            DensitySampler root = synthetic.root();
            for (Tuning tuning : TUNINGS) {
                DensitySampler compiled = DensityPruning.compile(root, tuning);
                compiledTrees += compiled instanceof CompiledFinalDensity ? 1 : 0;
                for (DensityVolume volume : syntheticVolumes(random)) {
                    SamplerContext paper = context(null);
                    SamplerContext mine = context(null);
                    String what = "tree " + tree + " tuning " + tuning + " volume " + volume;
                    assertBits(volume, sample(root, paper, volume), sample(compiled, mine, volume), what);
                    if (compiled instanceof CompiledFinalDensity density) {
                        assertSameCells(density, paper, mine, what);
                    }
                    assertBits(volume, sample(root, paper, volume), sample(compiled, mine, volume), what + ", repeated");
                }
            }
        }
        assertTrue(compiledTrees > 200, "most synthetic trees compile, was " + compiledTrees);
    }

    private static List<DensityVolume> syntheticVolumes(SplittableRandom random) {
        int x = random.nextInt(-200, 200) * 4;
        int z = random.nextInt(-200, 200) * 4;
        return List.of(
            new DensityVolume(16, 48, 16, x, -64, z),
            new DensityVolume(random.nextInt(1, 11), random.nextInt(1, 30), random.nextInt(1, 11), x + random.nextInt(-9, 9), random.nextInt(-64, 0), z + random.nextInt(-9, 9)),
            new DensityVolume(1, 48, 1, x + 3, -64, z - 5),
            new DensityVolume(3, 4, 3, x, -64, z, 4, 8, 4),
            new DensityVolume(1, 1, 1, x, -64, z)
        );
    }

    /** One random tree of selectors over leaves whose values sit on the thresholds, around an interpolated function. */
    private static final class Synthetic {
        private static final float MIN = 0.25F;
        private static final float MAX = 0.75F;
        private static final float[] SELECTOR_VALUES = {MIN, MAX, Math.nextDown(MIN), Math.nextUp(MIN), Math.nextDown(MAX), 0.5F, 0.0F, -0.0F,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1.0F, -1.0F};
        private static final float[] ALPHA_VALUES = {0.0F, -0.0F, 1.0F, 0.5F, Float.NaN, 2.0F, -0.5F, 1.0F, 0.0F};

        private final SplittableRandom random;
        private final Noise noise;
        private final List<DensitySampler> caches = new ArrayList<>();
        private final List<DensitySampler> slicedCaches = new ArrayList<>();
        private int salt;

        Synthetic(SplittableRandom random, Noise noise, int firstCacheId) throws ReflectiveOperationException {
            this.random = random;
            this.noise = noise;
            // sliced caches first: a cache must always sit below the same slices, or the tree is not compiled
            for (int i = 0; i < 2; ++i) {
                this.slicedCaches.add(new SliceFunction.YSampler(new CachingDensitySampler(firstCacheId + 2 + i, this.tree(1)), 0));
            }
            for (int i = 0; i < 2; ++i) {
                this.caches.add(new CachingDensitySampler(firstCacheId + i, this.tree(2)));
            }
        }

        DensitySampler root() throws ReflectiveOperationException {
            DensitySampler interpolated = interpolated(this.tree(4));
            return this.random.nextBoolean() ? interpolated : new BinaryFunction.AddSampler(interpolated, interpolated(this.tree(3)));
        }

        private DensitySampler tree(int depth) throws ReflectiveOperationException {
            if (depth == 0 || this.random.nextInt(5) == 0) {
                return this.leaf();
            }
            return switch (this.random.nextInt(9)) {
                case 0 -> rangeChoice(this.selector(depth), this.tree(depth - 1), this.tree(depth - 1));
                case 1 -> new LerpFunction.Sampler(this.alpha(depth), this.tree(depth - 1), this.tree(depth - 1));
                case 2 -> intervalSelect(this.selector(depth), new float[] {MIN, MAX}, this.tree(depth - 1), this.tree(depth - 1), this.tree(depth - 1));
                case 3 -> singleThreshold(this.selector(depth), MAX, this.tree(depth - 1), this.tree(depth - 1));
                case 4 -> new BinaryFunction.MinSampler(this.tree(depth - 1), this.tree(depth - 1), Float.NEGATIVE_INFINITY);
                case 5 -> new BinaryFunction.MulSampler(this.tree(depth - 1), this.tree(depth - 1));
                case 6 -> new LerpFunction.ConstFirstSampler(this.alpha(depth), 0.1171875F, this.tree(depth - 1));
                case 7 -> this.caches.isEmpty() ? this.leaf() : this.caches.get(this.random.nextInt(this.caches.size()));
                default -> this.slicedCaches.isEmpty() ? this.leaf() : this.slicedCaches.get(this.random.nextInt(this.slicedCaches.size()));
            };
        }

        private DensitySampler selector(int depth) throws ReflectiveOperationException {
            return this.random.nextInt(3) == 0 ? this.tree(depth - 1) : new ValueLeaf(++this.salt, SELECTOR_VALUES);
        }

        private DensitySampler alpha(int depth) throws ReflectiveOperationException {
            return this.random.nextInt(4) == 0 ? this.tree(depth - 1) : new ValueLeaf(++this.salt, ALPHA_VALUES);
        }

        private DensitySampler leaf() {
            return switch (this.random.nextInt(4)) {
                case 0 -> new ValueLeaf(++this.salt, SELECTOR_VALUES);
                case 1 -> new ConstantFunction.Sampler(this.random.nextBoolean() ? MIN : -0.0F);
                default -> new NoiseFunction.Sampler(this.noise, 0.5 + this.random.nextInt(4), 0.25 + this.random.nextInt(3));
            };
        }
    }

    /** A sampler type the evaluator does not know, so it becomes an opaque leaf; it reaches the caches inside. */
    private record Wrapper(DensitySampler inner) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            this.inner.sampleVolume(context, outputBuffer, volume);
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return this.inner.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    /** A leaf whose value is picked from values by a hash of the block, so it hits every threshold somewhere. */
    private record ValueLeaf(int salt, float[] values) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) {
                for (int x = 0; x < volume.sizeX(); x++) {
                    for (int y = 0; y < volume.sizeY(); y++) {
                        outputBuffer.set(index++, this.sampleValue(context, volume.blockX(x), volume.blockY(y), volume.blockZ(z)));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            long hash = blockX * 0x9E3779B97F4A7C15L ^ blockY * 0xC2B2AE3D27D4EB4FL ^ blockZ * 0x165667B19E3779F9L ^ this.salt * 0x27D4EB2F165667C5L;
            hash ^= hash >>> 29;
            hash *= 0xBF58476D1CE4E5B9L;
            hash ^= hash >>> 32;
            return this.values[(int)Math.floorMod(hash, (long)this.values.length)];
        }
    }

    private static DensitySampler interpolated(DensitySampler input) throws ReflectiveOperationException {
        return construct("InterpolatedFunction$Sampler", input, 4, 8, 0.25F, 0.125F);
    }

    private static DensitySampler rangeChoice(DensitySampler input, DensitySampler whenInRange, DensitySampler whenOutOfRange) throws ReflectiveOperationException {
        return construct("RangeChoiceFunction$Sampler", input, Synthetic.MIN, Synthetic.MAX, whenInRange, whenOutOfRange);
    }

    private static DensitySampler intervalSelect(DensitySampler input, float[] thresholds, DensitySampler... samplers) throws ReflectiveOperationException {
        return construct("IntervalSelectFunction$Sampler", input, thresholds, samplers);
    }

    private static DensitySampler singleThreshold(DensitySampler input, float threshold, DensitySampler below, DensitySampler above) throws ReflectiveOperationException {
        return construct("IntervalSelectFunction$SingleThresholdSampler", input, threshold, below, above);
    }

    private static DensitySampler construct(String name, Object... arguments) throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.minecraft.world.level.levelgen.densityfunction.op." + name);
        Constructor<?> constructor = type.getDeclaredConstructor(Arrays.stream(type.getRecordComponents()).map(c -> c.getType()).toArray(Class<?>[]::new));
        constructor.setAccessible(true);
        return (DensitySampler)constructor.newInstance(arguments);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** One world: its RandomState, Paper's finalDensity sampler and the compiled one. */
    private record World(NoiseGeneratorSettings settings, RandomState randomState, DensitySampler paper, DensitySampler compiled) {
        static World of(ResourceKey<NoiseGeneratorSettings> preset, long seed) {
            Registry<NoiseGeneratorSettings> presets = RegistryHelper.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
            NoiseGeneratorSettings settings = presets.getValueOrThrow(preset);
            RandomState randomState = RandomState.create(RegistryHelper.registryAccess().lookupOrThrow(Registries.NOISE), seed, settings);
            // the setting is on by default; with it off at the first request, this RandomState keeps Paper's own sampler
            boolean enabled = CompiledDensity.enabled;
            CompiledDensity.enabled = false;
            DensitySampler paper;
            try {
                paper = randomState.getSampler(settings.noiseRouter().finalDensity());
            } finally {
                CompiledDensity.enabled = enabled;
            }
            return new World(settings, randomState, paper, DensityPruning.compile(paper, Tuning.DEFAULT));
        }

        void assertSameAsPaper(DensityVolume volume, @Nullable Beardifier beardifier, String what) {
            SamplerContext paperContext = context(beardifier);
            SamplerContext compiledContext = context(beardifier);
            float[] expected = sample(this.paper, paperContext, volume);
            assertBits(volume, expected, sample(this.compiled, compiledContext, volume), what);
            if (this.compiled instanceof CompiledFinalDensity density) {
                assertSameCells(density, paperContext, compiledContext, what);
            }
            assertBits(volume, expected, sample(this.compiled, compiledContext, volume), what + ", repeated on the same context");
        }
    }

    private static Beardifier structureAt(int x, int y, int z) {
        BoundingBox box = new BoundingBox(x - 6, y - 4, z - 6, x + 6, y + 6, z + 6);
        return new Beardifier(List.of(new Beardifier.Rigid(box, TerrainAdjustment.BEARD_THIN, 0)), List.of(), box.inflatedBy(24));
    }

    private static SamplerContext context(@Nullable Beardifier beardifier) {
        return SamplerContext.builder()
            .setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, beardifier).build())
            .useBufferArena(PoisonedArena.INSTANCE)
            .enableCaches()
            .build();
    }

    /** Hands out new buffers filled with a finite poison value, so an unwritten element shows up in every compare. */
    private enum PoisonedArena implements DensityBufferArena {
        INSTANCE;

        @Override
        public ScopedDensityBuffer acquire(int size) {
            ScopedDensityBuffer buffer = new ScopedDensityBuffer(this, size, size);
            Arrays.fill(buffer.values, POISON);
            return buffer;
        }

        @Override
        public void release(ScopedDensityBuffer buffer) {
        }
    }

    private static float[] sample(DensitySampler sampler, SamplerContext context, DensityVolume volume) {
        try (ScopedDensityBuffer buffer = context.acquireBuffer(volume)) {
            sampler.sampleVolume(context, buffer, volume);
            return Arrays.copyOf(buffer.values, volume.size());
        }
    }

    private static void assertSameCells(CompiledFinalDensity compiled, SamplerContext paper, SamplerContext mine, String what) {
        for (CachingDensitySampler cache : compiled.caches) {
            DensityVolume cell = paper.kindling$cellVolume(cache.id());
            assertEquals(cell, mine.kindling$cellVolume(cache.id()), what + ": cell of cache " + cache.id());
            if (cell != null) {
                assertBits(cell, Arrays.copyOf(paper.kindling$completedCell(cache.id()).values, cell.size()),
                    Arrays.copyOf(mine.kindling$completedCell(cache.id()).values, cell.size()), what + ": contents of cache " + cache.id());
            }
        }
    }

    private static void samePoint(DensitySampler sampler, SamplerContext paper, SamplerContext mine, int x, int y, int z, String what) {
        float expected = sampler.sampleValue(paper, x, y, z);
        float actual = sampler.sampleValue(mine, x, y, z);
        if (!sameValue(expected, actual)) {
            fail(what + ": point " + x + " " + y + " " + z + " reads " + actual + ", Paper " + expected);
        }
    }

    private static void sameVolume(DensitySampler sampler, SamplerContext paper, SamplerContext mine, DensityVolume volume, String what) {
        assertBits(volume, sample(sampler, paper, volume), sample(sampler, mine, volume), what);
    }

    /**
     * Bit for bit, except that two NaNs count as equal whatever their payload: Java leaves the payload of a NaN result
     * unspecified, so Paper's own code may give either sign depending on how the JIT orders an operation. No
     * comparison or conversion the game makes can tell them apart. The game's routers produce no NaN at all.
     */
    private static void assertBits(DensityVolume volume, float[] expected, float[] actual, String what) {
        for (int i = 0; i < volume.size(); ++i) {
            if (!sameValue(expected[i], actual[i])) {
                int y = i % volume.sizeY();
                int x = i / volume.sizeY() % volume.sizeX();
                int z = i / volume.sizeY() / volume.sizeX();
                fail(what + ": index " + i + " (block " + volume.blockX(x) + " " + volume.blockY(y) + " " + volume.blockZ(z) + ") is " + actual[i]
                    + " (0x" + Integer.toHexString(Float.floatToRawIntBits(actual[i])) + "), Paper " + expected[i]
                    + " (0x" + Integer.toHexString(Float.floatToRawIntBits(expected[i])) + ")");
            }
        }
    }

    private static boolean sameValue(float expected, float actual) {
        return Float.floatToRawIntBits(expected) == Float.floatToRawIntBits(actual) || Float.isNaN(expected) && Float.isNaN(actual);
    }
}
