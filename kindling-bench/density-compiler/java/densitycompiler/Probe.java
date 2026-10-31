package densitycompiler;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Point queries on the context a density call has just used. In the server the aquifer keeps using that context
 * after finalDensity, and Paper's cached volumes answer point queries that lie on their grid. A variant that does
 * not fill Paper's cache cells (or fills them differently) must still return the same bits there.
 * Results are laid out [function][point], so a verifier mismatch at block (x, y, 0) reads as function x, point y.
 */
final class Probe {
    static final int POINTS = 48;
    private static final String PRNG = "Xoroshiro128PlusPlus";
    private static final int QUART = 4;

    private Probe() {
    }

    static int length(Chunk chunk) {
        return chunk.world().probeFunctions().size() * POINTS;
    }

    /** The shape of the result array, for the verifier. */
    static DensityVolume layout(Chunk chunk) {
        return new DensityVolume(chunk.world().probeFunctions().size(), POINTS, 1, 0, 0, 0);
    }

    static void sample(Chunk chunk, SamplerContext context, float[] target) {
        DensityVolume volume = chunk.volume();
        List<DensityFunction> functions = chunk.world().probeFunctions();
        RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(volume.minBlockX() * 31L + volume.minBlockZ() + chunk.world().seed());
        int[] points = new int[POINTS * 3];
        for (int p = 0; p < POINTS; ++p) {
            int x = volume.blockX(random.nextInt(volume.sizeX()));
            int z = volume.blockZ(random.nextInt(volume.sizeZ()));
            if ((p & 1) == 0) {
                x -= Math.floorMod(x, QUART);
                z -= Math.floorMod(z, QUART);
            }
            points[p * 3] = x;
            points[p * 3 + 1] = volume.blockY(random.nextInt(volume.sizeY()));
            points[p * 3 + 2] = z;
        }
        for (int f = 0; f < functions.size(); ++f) {
            DensitySampler sampler = chunk.world().randomState().getSampler(functions.get(f));
            for (int p = 0; p < POINTS; ++p) {
                target[f * POINTS + p] = sampler.sampleValue(context, points[p * 3], points[p * 3 + 1], points[p * 3 + 2]);
            }
        }
    }
}
