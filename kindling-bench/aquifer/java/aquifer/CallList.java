package aquifer;

import java.util.random.RandomGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.jspecify.annotations.Nullable;

/**
 * A fixed sequence of computeSubstance calls: block position and the density passed (a float widened to double,
 * as the server does). Verify and bench replay the same list, so both exercise the same calls.
 */
final class CallList {
    private static final double SYNTHETIC_DENSITY_RANGE = 2.5;
    private static final int FUZZ_KINDS = 8;
    private static final int CARVE_COLUMNS = 24;
    private static final int CARVE_MIN_SPAN = 4;
    private static final int CARVE_SPAN_RANGE = 80;
    private static final int BAND_TOP = 400;
    private static final int BAND_BOTTOM = -80;
    private static final double[] FLOOR_DENSITIES = {-2.5, -0.4, 0.0};

    /** How densities of a sampled list are chosen. */
    enum Densities { AS_IS, FUZZED }

    final int[] x;
    final int[] y;
    final int[] z;
    final double[] density;
    final int size;

    private CallList(int size) {
        this.size = size;
        this.x = new int[size];
        this.y = new int[size];
        this.z = new int[size];
        this.density = new double[size];
    }

    /** doFill order: z outer, x middle, y descending, real densities (NBCG:449-459). */
    static CallList fill(DensityVolume volume, float[] densities) {
        CallList list = new CallList(volume.size());
        int at = 0;
        for (int z = 0; z < volume.sizeZ(); ++z) {
            for (int x = 0; x < volume.sizeX(); ++x) {
                for (int y = volume.sizeY() - 1; y >= 0; --y) {
                    list.set(at++, volume.blockX(x), volume.blockY(y), volume.blockZ(z), densities[volume.indexUnchecked(x, y, z)]);
                }
            }
        }
        return list;
    }

    /** The carving mask: density 0.0 on random columns, each a y span visited downwards (NBCG:302-317). */
    static CallList carve(DensityVolume volume, RandomGenerator random) {
        int[] columnX = new int[CARVE_COLUMNS];
        int[] columnZ = new int[CARVE_COLUMNS];
        int[] top = new int[CARVE_COLUMNS];
        int[] bottom = new int[CARVE_COLUMNS];
        int total = 0;
        for (int i = 0; i < CARVE_COLUMNS; ++i) {
            columnX[i] = volume.blockX(random.nextInt(volume.sizeX()));
            columnZ[i] = volume.blockZ(random.nextInt(volume.sizeZ()));
            top[i] = volume.blockY(random.nextInt(volume.sizeY()));
            bottom[i] = Math.max(volume.minBlockY(), top[i] - CARVE_MIN_SPAN - random.nextInt(CARVE_SPAN_RANGE));
            total += top[i] - bottom[i] + 1;
        }
        CallList list = new CallList(total);
        int at = 0;
        for (int i = 0; i < CARVE_COLUMNS; ++i) {
            for (int blockY = top[i]; blockY >= bottom[i]; --blockY) {
                list.set(at++, columnX[i], blockY, columnZ[i], 0.0);
            }
        }
        return list;
    }

    /**
     * Random positions in random order. Half of them lie in the band where aquifers matter, half anywhere in the
     * volume. Real densities come from densities (null: synthetic values in [-2.5, 0], 0.0 included).
     * FUZZED replaces some by the values a comparison could trip over: 0.0, -0.0, NaN and the smallest doubles.
     */
    static CallList sampled(DensityVolume volume, float @Nullable [] densities, int count, Densities mode, RandomGenerator random) {
        CallList list = new CallList(count);
        for (int i = 0; i < count; ++i) {
            int x = random.nextInt(volume.sizeX());
            int z = random.nextInt(volume.sizeZ());
            int y = random.nextBoolean() ? random.nextInt(volume.sizeY()) : bandIndex(volume, random);
            double value = densities == null ? syntheticDensity(random) : densities[volume.indexUnchecked(x, y, z)];
            list.set(i, volume.blockX(x), volume.blockY(y), volume.blockZ(z), mode == Densities.FUZZED ? fuzz(value, random) : value);
        }
        return list;
    }

    /**
     * Every column of the volume's lowest rows, each with a strong, a medium and a zero density: the blocks whose twelve
     * candidate cells include the bottom grid row (the only row where a deep volume's cell locations wrap).
     */
    static CallList floor(DensityVolume volume, int rows) {
        CallList list = new CallList(rows * volume.sizeX() * volume.sizeZ() * FLOOR_DENSITIES.length);
        int at = 0;
        for (int y = 0; y < rows; ++y) {
            for (int z = 0; z < volume.sizeZ(); ++z) {
                for (int x = 0; x < volume.sizeX(); ++x) {
                    for (double density : FLOOR_DENSITIES) {
                        list.set(at++, volume.blockX(x), volume.blockY(y), volume.blockZ(z), density);
                    }
                }
            }
        }
        return list;
    }

    private static int bandIndex(DensityVolume volume, RandomGenerator random) {
        int low = Math.max(volume.minBlockY(), BAND_BOTTOM);
        int high = Math.min(volume.maxBlockY(), BAND_TOP);
        int blockY = high < low ? volume.minBlockY() : random.nextInt(low, high + 1);
        return blockY - volume.minBlockY();
    }

    private static double syntheticDensity(RandomGenerator random) {
        return random.nextInt(FUZZ_KINDS) == 0 ? 0.0 : -random.nextDouble() * SYNTHETIC_DENSITY_RANGE;
    }

    private static double fuzz(double real, RandomGenerator random) {
        return switch (random.nextInt(FUZZ_KINDS)) {
            case 0 -> 0.0;
            case 1 -> -0.0;
            case 2 -> Double.NaN;
            case 3 -> Double.MIN_VALUE;
            case 4 -> -Double.MIN_VALUE;
            case 5 -> -random.nextDouble() * SYNTHETIC_DENSITY_RANGE;
            default -> real;
        };
    }

    private void set(int index, int blockX, int blockY, int blockZ, double value) {
        this.x[index] = blockX;
        this.y[index] = blockY;
        this.z[index] = blockZ;
        this.density[index] = value;
    }
}
