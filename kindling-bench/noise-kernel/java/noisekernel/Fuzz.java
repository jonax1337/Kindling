package noisekernel;

import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** Random calls on the real leaves: odd volume sizes, extreme coordinates and scales, zero y scale. */
final class Fuzz {
    private static final int[] SIZES_Y = {1, 2, 3, 5, 7, 8, 9, 15, 16, 17, 31, 33, 49, 64};

    private Fuzz() {
    }

    static KernelCall[] calls(int count, long seed, List<Leaf> leaves) {
        SplittableRandom random = new SplittableRandom(seed);
        KernelCall[] calls = new KernelCall[count];
        for (int i = 0; i < count; ++i) {
            Leaf leaf = leaves.get(random.nextInt(leaves.size()));
            calls[i] = new KernelCall(leaf, volume(random), logUniform(random, -4.0, 3.5), yScale(random), (float)uniform(random, -3.0, 3.0));
        }
        return calls;
    }

    private static DensityVolume volume(SplittableRandom random) {
        int sizeY = random.nextBoolean() ? SIZES_Y[random.nextInt(SIZES_Y.length)] : 1 + random.nextInt(70);
        return new DensityVolume(1 + random.nextInt(9), sizeY, 1 + random.nextInt(9),
            origin(random), origin(random), origin(random),
            1 + random.nextInt(8), 1 + random.nextInt(8), 1 + random.nextInt(8));
    }

    private static int origin(SplittableRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> random.nextInt(-2_000, 2_001);
            case 1 -> random.nextInt(-1_000_000, 1_000_001);
            case 2 -> random.nextInt(-40_000_000, 40_000_001);
            default -> random.nextInt(Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
        };
    }

    private static double yScale(SplittableRandom random) {
        return random.nextInt(10) == 0 ? 0.0 : logUniform(random, -4.0, 3.0);
    }

    private static double logUniform(SplittableRandom random, double minExponent, double maxExponent) {
        return Math.pow(10.0, uniform(random, minExponent, maxExponent));
    }

    private static double uniform(SplittableRandom random, double min, double max) {
        return min + (max - min) * random.nextDouble();
    }
}
