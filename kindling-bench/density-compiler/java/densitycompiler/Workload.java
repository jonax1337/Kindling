package densitycompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * The chunk list: per world seed random chunks within +-20,000 blocks, a square at the origin and a few far chunks
 * (which exercise coordinate wrapping); every N-th one gets a synthetic structure for the beardifier.
 */
final class Workload {
    private static final String PRNG = "Xoroshiro128PlusPlus";
    private static final int SPREAD_CHUNKS = 20_000 / 16;
    private static final int FAR_CHUNK = 29_999_000 / 16;
    private static final int SPREAD_BLOCKS = 20_000;
    private static final int FAR_BLOCK = 29_999_000;
    private static final int BOX_MAX_SIDE = 24;
    private static final int BEARD_REACH = 24;
    private static final int PIECE_MIN_Y = 60;
    private static final int PIECE_MAX_Y = 80;
    private static final int PIECE_INSET = 2;
    private static final int PIECE_SIDE = 12;
    private static final int BEARD_BOX_MIN_HEIGHT = 30;
    private static final int BEARD_BOX_HEIGHT_RANGE = 60;
    private static final int CELL_XZ = 4;
    private static final int CELL_Y = 8;
    private static final int CELL_BOX_KINDS = 8;
    private static final int STEPPED_SIDE = 6;
    private static final int STEPPED_HEIGHT = 40;

    private final List<Chunk> chunks;

    private Workload(List<Chunk> chunks) {
        this.chunks = chunks;
    }

    static Workload build(List<World> worlds, Options options) {
        List<Chunk> chunks = new ArrayList<>();
        for (World world : worlds) {
            RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(options.workloadSeed ^ world.seed());
            List<int[]> positions = new ArrayList<>();
            for (int i = 0; i < options.chunks; ++i) {
                positions.add(new int[] {randomCoordinate(random, SPREAD_CHUNKS), randomCoordinate(random, SPREAD_CHUNKS)});
            }
            addSquare(positions, options.square);
            addFar(positions, options.far);
            for (int i = 0; i < positions.size(); ++i) {
                boolean withStructure = options.beardEvery > 0 && i % options.beardEvery == 0;
                chunks.add(chunkAt(world, positions.get(i)[0], positions.get(i)[1], withStructure));
            }
        }
        return new Workload(chunks);
    }

    /**
     * What only the verifier checks, per world seed: chunks at random positions over the whole +-30M range, the
     * 1 x height x 1 columns that iterateNoiseColumn asks for (unaligned, negative, far; no beardifier) and random
     * unaligned boxes. Bench times the plain chunk list only.
     */
    static List<Chunk> verifyOnly(List<World> worlds, Options options) {
        List<Chunk> chunks = new ArrayList<>();
        RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(~options.workloadSeed);
        for (int i = 0; i < options.fuzz; ++i) {
            World world = worlds.get(i % worlds.size());
            chunks.add(chunkAt(world, randomCoordinate(random, FAR_CHUNK), randomCoordinate(random, FAR_CHUNK), withStructure(options, i)));
        }
        for (int i = 0; i < options.columns; ++i) {
            World world = worlds.get(i % worlds.size());
            if (withStructure(options, i)) {
                int chunkX = randomCoordinate(random, SPREAD_CHUNKS);
                int chunkZ = randomCoordinate(random, SPREAD_CHUNKS);
                chunks.add(Chunk.column(world, insidePiece(random, chunkX), insidePiece(random, chunkZ), syntheticStructure(chunkX, chunkZ)));
            } else {
                int bound = (i & 1) == 0 ? SPREAD_BLOCKS : FAR_BLOCK;
                chunks.add(Chunk.column(world, randomCoordinate(random, bound), randomCoordinate(random, bound)));
            }
        }
        for (int i = 0; i < options.boxes; ++i) {
            World world = worlds.get(i % worlds.size());
            chunks.add(withStructure(options, i) ? structureBox(world, random) : Chunk.box(world, randomBox(world, random)));
        }
        return chunks;
    }

    private static boolean withStructure(Options options, int index) {
        return options.beardEvery > 0 && index % options.beardEvery == 0;
    }

    /** A block inside the middle piece of syntheticStructure, so the beardifier has something to add. */
    private static int insidePiece(RandomGenerator random, int chunk) {
        return chunk * 16 + PIECE_INSET + random.nextInt(PIECE_SIDE);
    }

    /** An unaligned box over a synthetic structure, tall enough to cross the piece. */
    private static Chunk structureBox(World world, RandomGenerator random) {
        int chunkX = randomCoordinate(random, SPREAD_CHUNKS);
        int chunkZ = randomCoordinate(random, SPREAD_CHUNKS);
        DensityVolume volume = new DensityVolume(1 + random.nextInt(BOX_MAX_SIDE), BEARD_BOX_MIN_HEIGHT + random.nextInt(BEARD_BOX_HEIGHT_RANGE),
            1 + random.nextInt(BOX_MAX_SIDE), insidePiece(random, chunkX) - random.nextInt(BOX_MAX_SIDE / 2),
            PIECE_MIN_Y - BEARD_REACH + random.nextInt(BEARD_BOX_HEIGHT_RANGE), insidePiece(random, chunkZ) - random.nextInt(BOX_MAX_SIDE / 2));
        return Chunk.box(world, volume, syntheticStructure(chunkX, chunkZ));
    }

    List<Chunk> chunks() {
        return this.chunks;
    }

    int structureCount() {
        return (int)this.chunks.stream().filter(Chunk::hasStructure).count();
    }

    /**
     * Sizes 1..24 across, any height from 1 up, any y offset, any block position. One box in eight steps by a cell
     * (4 / 8 / 4 blocks), one in eight is a single point on the cell lattice (the volume an interpolated function
     * passes through unchanged).
     */
    private static DensityVolume randomBox(World world, RandomGenerator random) {
        int kind = random.nextInt(CELL_BOX_KINDS);
        if (kind == 0) {
            return new DensityVolume(1 + random.nextInt(STEPPED_SIDE), 1 + random.nextInt(STEPPED_HEIGHT), 1 + random.nextInt(STEPPED_SIDE),
                randomCoordinate(random, SPREAD_BLOCKS), world.noiseSettings().minY() + random.nextInt(STEPPED_HEIGHT),
                randomCoordinate(random, SPREAD_BLOCKS), CELL_XZ, CELL_Y, CELL_XZ);
        } else if (kind == 1) {
            return new DensityVolume(1, 1, 1, randomCoordinate(random, SPREAD_BLOCKS / CELL_XZ) * CELL_XZ,
                world.noiseSettings().minY() + random.nextInt(world.noiseSettings().height() / CELL_Y) * CELL_Y,
                randomCoordinate(random, SPREAD_BLOCKS / CELL_XZ) * CELL_XZ);
        }
        int height = world.noiseSettings().height();
        int sizeY = 1 + random.nextInt(height);
        return new DensityVolume(1 + random.nextInt(BOX_MAX_SIDE), sizeY, 1 + random.nextInt(BOX_MAX_SIDE),
            randomCoordinate(random, SPREAD_BLOCKS), world.noiseSettings().minY() + random.nextInt(height - sizeY + 1), randomCoordinate(random, SPREAD_BLOCKS));
    }

    private static int randomCoordinate(RandomGenerator random, int bound) {
        return random.nextInt(-bound, bound + 1);
    }

    private static void addSquare(List<int[]> positions, int side) {
        for (int x = 0; x < side; ++x) {
            for (int z = 0; z < side; ++z) {
                positions.add(new int[] {x - side / 2, z - side / 2});
            }
        }
    }

    private static void addFar(List<int[]> positions, int count) {
        for (int i = 0; i < count; ++i) {
            positions.add(new int[] {(i & 1) == 0 ? FAR_CHUNK : -FAR_CHUNK, (i & 2) == 0 ? FAR_CHUNK : -FAR_CHUNK});
        }
    }

    private static Chunk chunkAt(World world, int chunkX, int chunkZ, boolean withStructure) {
        return Chunk.chunk(world, chunkX, chunkZ, withStructure ? syntheticStructure(chunkX, chunkZ) : Beardifier.EMPTY);
    }

    /** One rigid piece in the middle of the chunk, built through Beardifier's public test constructor. */
    private static Beardifier syntheticStructure(int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        BoundingBox box = new BoundingBox(minX + 2, PIECE_MIN_Y, minZ + 2, minX + 13, PIECE_MAX_Y, minZ + 13);
        return new Beardifier(List.of(new Beardifier.Rigid(box, TerrainAdjustment.BEARD_THIN, 0)), List.of(), box.inflatedBy(BEARD_REACH));
    }
}
