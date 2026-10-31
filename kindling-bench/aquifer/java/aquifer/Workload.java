package aquifer;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** The cases of a run: per world a few chunks, columns and shuffled replays, plus the edge cases verify adds. */
final class Workload {
    private static final String PRNG = "Xoroshiro128PlusPlus";
    private static final int SPREAD_CHUNKS = 10_000 / 16;
    private static final int SPREAD_BLOCKS = 20_000;
    private static final int FAR_BLOCKS = 29_999_000;
    private static final int FAR_CHUNKS = FAR_BLOCKS / 16;
    private static final int FAR_COLUMN_EVERY = 4;
    private static final int DEEP_CASES = 2;
    private static final int FLOODED_CASES = 2;
    private static final long SYNTHETIC_SALT = 0x5E17AC1DL;

    private final List<ChunkCase> cases;

    private Workload(List<ChunkCase> cases) {
        this.cases = cases;
    }

    static Workload build(List<World> worlds, Options options) {
        List<ChunkCase> cases = new ArrayList<>();
        for (int w = 0; w < worlds.size(); ++w) {
            World world = worlds.get(w);
            RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(options.workloadSeed ^ world.seed() ^ world.preset().hashCode());
            addChunks(cases, world, options.chunks, random);
            addColumns(cases, world, options.columns, random);
            addShuffles(cases, world, options.shuffles, random);
            if (options.edge) {
                addEdges(cases, world, w, random);
            }
        }
        return new Workload(cases);
    }

    List<ChunkCase> cases() {
        return this.cases;
    }

    List<ChunkCase> of(ChunkCase.Group group) {
        return this.cases.stream().filter(c -> c.group() == group).toList();
    }

    /** Chunk (0, 0), then random chunks within +-10,000 blocks. */
    private static void addChunks(List<ChunkCase> cases, World world, int count, RandomGenerator random) {
        for (int i = 0; i < count; ++i) {
            int chunkX = i == 0 ? 0 : coordinate(random, SPREAD_CHUNKS);
            int chunkZ = i == 0 ? 0 : coordinate(random, SPREAD_CHUNKS);
            cases.add(new ChunkCase(ChunkCase.Group.FILL, world, chunkVolume(world, chunkX, chunkZ), random.nextLong(), false));
        }
    }

    /** 1 x height x 1 columns at any block; every fourth one far out. */
    private static void addColumns(List<ChunkCase> cases, World world, int count, RandomGenerator random) {
        for (int i = 0; i < count; ++i) {
            int bound = i % FAR_COLUMN_EVERY == FAR_COLUMN_EVERY - 1 ? FAR_BLOCKS : SPREAD_BLOCKS;
            DensityVolume volume = new DensityVolume(1, world.noiseSettings().height(), 1, coordinate(random, bound),
                world.noiseSettings().minY(), coordinate(random, bound));
            cases.add(new ChunkCase(ChunkCase.Group.COLUMN, world, volume, random.nextLong(), false));
        }
    }

    private static void addShuffles(List<ChunkCase> cases, World world, int count, RandomGenerator random) {
        for (int i = 0; i < count; ++i) {
            DensityVolume volume = chunkVolume(world, coordinate(random, SPREAD_CHUNKS), coordinate(random, SPREAD_CHUNKS));
            cases.add(new ChunkCase(ChunkCase.Group.SHUFFLE, world, volume, random.nextLong(), false));
        }
    }

    /** A far chunk (the corner rotates with the world index), a Legacy-random chunk, and the synthetic worlds' cases. */
    private static void addEdges(List<ChunkCase> cases, World world, int worldIndex, RandomGenerator random) {
        int farX = (worldIndex & 1) == 0 ? FAR_CHUNKS : -FAR_CHUNKS;
        int farZ = (worldIndex & 2) == 0 ? FAR_CHUNKS : -FAR_CHUNKS;
        cases.add(new ChunkCase(ChunkCase.Group.EDGE, world, chunkVolume(world, farX, farZ), random.nextLong(), false));
        World legacy = world.withLegacyRandom();
        cases.add(new ChunkCase(ChunkCase.Group.EDGE, legacy, chunkVolume(legacy, coordinate(random, SPREAD_CHUNKS), coordinate(random, SPREAD_CHUNKS)),
            random.nextLong(), false));
        addSynthetic(cases, world, random);
    }

    /**
     * What the presets never reach, in worlds built from the preset by changing its sea level and amplifying the floodedness
     * noise: the deep-sea world (a 4064-high volume whose bottom cells lie in the BlockPos Y wrap) and the sea-level -10 world
     * (fluid levels exactly on the lava test's edge, water against lava in volume).
     */
    static List<ChunkCase> synthetic(World world, long workloadSeed) {
        List<ChunkCase> cases = new ArrayList<>();
        addSynthetic(cases, world, RandomGeneratorFactory.of(PRNG).create(workloadSeed ^ world.seed() ^ SYNTHETIC_SALT));
        return cases;
    }

    private static void addSynthetic(List<ChunkCase> cases, World world, RandomGenerator random) {
        World deep = world.withDeepSea();
        for (int i = 0; i < DEEP_CASES; ++i) {
            cases.add(new ChunkCase(ChunkCase.Group.DEEP, deep, chunkVolume(deep, coordinate(random, SPREAD_CHUNKS), coordinate(random, SPREAD_CHUNKS)),
                random.nextLong(), true));
        }
        World sea = world.withSeaLevelMinus10();
        for (int i = 0; i < FLOODED_CASES; ++i) {
            cases.add(new ChunkCase(ChunkCase.Group.FLOODED, sea, chunkVolume(sea, coordinate(random, SPREAD_CHUNKS), coordinate(random, SPREAD_CHUNKS)),
                random.nextLong(), false));
        }
    }

    private static DensityVolume chunkVolume(World world, int chunkX, int chunkZ) {
        return new DensityVolume(16, world.noiseSettings().height(), 16, chunkX * 16, world.noiseSettings().minY(), chunkZ * 16);
    }

    private static int coordinate(RandomGenerator random, int bound) {
        return random.nextInt(-bound, bound + 1);
    }
}
