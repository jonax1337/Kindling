package densitycompiler;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Informational only. Paper's sampleValue and sampleVolume are not bit-identical (interpolation, mul/div, min/max
 * and noise differ in rounding), so the per-point path cannot be the pass criterion. This measures how far apart
 * they are on K sampled points per chunk, as DensitySampler.sampleVolumeNaive would see it.
 */
final class NaiveOracle {
    private final BoundVariant reference;
    private final int chunksPerWorld;
    private final int pointsPerChunk;
    private final long seed;

    NaiveOracle(List<World> worlds, Options options) {
        this.reference = new BoundVariant(Variants.reference(), worlds);
        this.chunksPerWorld = options.naiveChunks;
        this.pointsPerChunk = options.naivePoints;
        this.seed = options.workloadSeed;
    }

    Result check(List<Chunk> chunks) {
        Result result = new Result();
        RandomGenerator random = RandomGeneratorFactory.of("Xoroshiro128PlusPlus").create(this.seed);
        for (Chunk chunk : this.selected(chunks)) {
            this.checkChunk(chunk, random, result);
        }
        return result;
    }

    /** The first few chunks of each world in the list. */
    private List<Chunk> selected(List<Chunk> chunks) {
        List<Chunk> selected = new ArrayList<>();
        for (World world : chunks.stream().map(Chunk::world).distinct().toList()) {
            chunks.stream().filter(chunk -> chunk.world() == world).limit(this.chunksPerWorld).forEach(selected::add);
        }
        return selected;
    }

    private void checkChunk(Chunk chunk, RandomGenerator random, Result result) {
        DensityVolume volume = chunk.volume();
        float[] expected = new float[volume.size()];
        this.reference.runInto(chunk, expected, null);
        DensitySampler sampler = this.reference.sampler(chunk.world());
        ThreadLocalDensityBufferArena arena = ThreadLocalDensityBufferArena.acquire();
        try {
            SamplerContext context = ChunkCall.context(chunk.beardifier(), arena, false);
            for (int i = 0; i < this.pointsPerChunk; ++i) {
                int x = random.nextInt(volume.sizeX());
                int y = random.nextInt(volume.sizeY());
                int z = random.nextInt(volume.sizeZ());
                float point = sampler.sampleValue(context, volume.blockX(x), volume.blockY(y), volume.blockZ(z));
                result.add(expected[volume.indexUnchecked(x, y, z)], point);
            }
        } finally {
            ThreadLocalDensityBufferArena.release(arena);
        }
    }

    /** Distance between the per-point and the per-volume value over all sampled points. */
    static final class Result {
        private long points;
        private long differing;
        private long nanDiffers;
        private long maxUlps;

        void add(float volumeValue, float pointValue) {
            ++this.points;
            int volumeBits = Float.floatToRawIntBits(volumeValue);
            int pointBits = Float.floatToRawIntBits(pointValue);
            if (volumeBits == pointBits) {
                return;
            }
            ++this.differing;
            if (Float.isNaN(volumeValue) || Float.isNaN(pointValue)) {
                ++this.nanDiffers;
            } else {
                this.maxUlps = Math.max(this.maxUlps, Math.abs(orderedKey(volumeBits) - orderedKey(pointBits)));
            }
        }

        /** Maps float bits to a line on which neighbouring floats are one apart and -0.0 equals +0.0. */
        private static long orderedKey(int bits) {
            long magnitude = bits & 0x7fffffffL;
            return bits < 0 ? -magnitude : magnitude;
        }

        long points() {
            return this.points;
        }

        long differing() {
            return this.differing;
        }

        long nanDiffers() {
            return this.nanDiffers;
        }

        long maxUlps() {
            return this.maxUlps;
        }

        String summary() {
            return String.format("NAIVE (informational, not pass/fail): %d points, %d differ from the volume value (%d involve NaN), max %d ulps",
                this.points, this.differing, this.nanDiffers, this.maxUlps);
        }
    }
}
