package aquifer;

import aquifer.candidate.BaseAquifer;
import aquifer.candidate.C2meAquifer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import org.apache.commons.lang3.mutable.MutableDouble;

/**
 * Checks the pure pieces of the candidates against Mojang's own private methods, called by reflection, on grids and
 * random inputs. The whole-aquifer replay compares results, but a one-ulp rounding difference or a boundary such as
 * {@code <= -10} only shows when a call lands exactly on it; here every such input is enumerated. Pieces: the similarity
 * of two squared distances (A:361), the pressure between two fluid statuses including the shared barrier noise (A:364-426)
 * and the fluid type of a status (A:556-571). Doubles are compared bit for bit.
 */
final class PartChecks {
    private static final String MOJANG_AQUIFER = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer";
    private static final String PRNG = "Xoroshiro128PlusPlus";
    private static final int SIMILARITY_DIFFERENCE_LIMIT = 1 << 21;
    private static final int SIMILARITY_RANDOM_PAIRS = 2_000_000;
    private static final int DISTANCE_SQR_BOUND = 1 << 27;
    private static final int[] PRESSURE_LEVELS = {DimensionType.WAY_BELOW_MIN_Y, -54, -3, -2, -1, 0, 1, 2, 3, 63};
    private static final int PRESSURE_Y_REACH = 12;
    private static final int[] FLUID_TYPE_LEVELS = {-14, -13, -12, -11, -10, -9, -8, -7, -6, DimensionType.WAY_BELOW_MIN_Y, 0, 63};
    private static final int FLUID_TYPE_POSITIONS = 3_000;
    private static final int FLUID_TYPE_COORDINATE_BOUND = 100_000;
    private static final int FLUID_TYPE_Y_BOUND = 200;
    private static final int LAVA_EDGE_LEVEL = -10;
    private static final int PROOF_LEVEL_BOUND = 140;
    private static final int PROOF_Y_BOUND = 150;
    private static final double LAVA_NOISE_THRESHOLD = 0.3;
    private static final int FLOOR_ROWS = 4;
    /** A location this high in a volume that starts at -2032 was computed below -2048 and wrapped. */
    private static final int WRAPPED_Y_FROM_BELOW = 2000;

    private static final MethodHandle MOJANG_SIMILARITY;
    private static final MethodHandle MOJANG_PRESSURE;
    private static final MethodHandle MOJANG_FLUID_TYPE;

    static {
        try {
            Class<?> mojang = Class.forName(MOJANG_AQUIFER);
            MOJANG_SIMILARITY = unreflect(mojang.getDeclaredMethod("similarity", int.class, int.class));
            MOJANG_PRESSURE = unreflect(mojang.getDeclaredMethod("calculatePressure", int.class, int.class, int.class, MutableDouble.class,
                Aquifer.FluidStatus.class, Aquifer.FluidStatus.class));
            MOJANG_FLUID_TYPE = unreflect(mojang.getDeclaredMethod("computeFluidType", int.class, int.class, int.class, Aquifer.FluidStatus.class,
                int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** The similarity function under test: BaseAquifer's, or a deliberately broken one. */
    interface Similarity {
        double of(int distanceSqr1, int distanceSqr2);
    }

    /** What a check enumerated, how many results differed, the first difference, and how often the lava branch was reached. */
    record Result(long checks, long differences, String first, long lavaAtEdge, long wrappedCells) {
        boolean passed() {
            return this.checks > 0 && this.differences == 0 && this.lavaAtEdge > 0 && this.wrappedCells > 0;
        }

        String summary() {
            return String.format("%,d checks, %d differences, lava test reached at the level edge %,d times, %d wrapped cell locations compared%s", this.checks, this.differences,
                this.lavaAtEdge, this.wrappedCells, this.first.isEmpty() ? "" : "; first: " + this.first);
        }
    }

    private PartChecks() {
    }

    /** Similarity, pressure and fluid type of a C2ME-family candidate, against the real class. Null when the variant is not one. */
    static Result run(World world, Variant candidate, Similarity similarity) {
        Accumulator accumulator = new Accumulator();
        checkSimilarity(similarity, accumulator);
        DensityVolume volume = new DensityVolume(16, world.noiseSettings().height(), 16, 0, world.noiseSettings().minY(), 0);
        try (AquiferRun run = AquiferRun.open(world, false)) {
            Aquifer reference = run.construct(Variants.reference(), volume);
            Aquifer built = run.construct(candidate, volume);
            if (!(built instanceof C2meAquifer parts)) {
                return null;
            }
            checkPressure(reference, parts, accumulator);
            checkFluidType(reference, parts, accumulator);
        }
        checkDeepLocations(world.withDeepSea(), candidate, accumulator);
        return accumulator.result();
    }

    /**
     * The cell locations after the lowest blocks of a deep volume were queried: every cell Mojang's cache holds must hold the
     * same x, y, z in the candidate. This sees the BlockPos Y wrap directly; its effect on a result is rare, because a wrapped
     * cell lies at least 17 blocks from the lowest block and seldom ranks among the four nearest.
     */
    private static void checkDeepLocations(World deep, Variant candidate, Accumulator accumulator) {
        DensityVolume volume = new DensityVolume(16, deep.noiseSettings().height(), 16, 0, deep.noiseSettings().minY(), 0);
        CallList calls = CallList.floor(volume, FLOOR_ROWS);
        try (AquiferRun run = AquiferRun.open(deep, false)) {
            Aquifer reference = run.construct(Variants.reference(), volume);
            Aquifer built = run.construct(candidate, volume);
            if (!(built instanceof C2meAquifer parts)) {
                return;
            }
            Replay.run(reference, calls);
            Replay.run(built, calls);
            long[] cache = mojangLocations(reference);
            for (int index = 0; index < cache.length; ++index) {
                if (cache[index] != Long.MAX_VALUE) {
                    accumulator.compareLocation(index, cache[index], parts);
                }
            }
        }
    }

    private static long[] mojangLocations(Aquifer reference) {
        try {
            Field field = Class.forName(MOJANG_AQUIFER).getDeclaredField("aquiferLocationCache");
            field.setAccessible(true);
            return (long[])field.get(reference);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Mojang's aquiferLocationCache", e);
        }
    }

    /**
     * Why two single-token mutants cannot be caught, checked as far as it can be: the lava noise is a float, and no float
     * widens to 0.3, so {@code > 0.3} and {@code >= 0.3} agree; and the pressure gradient is never exactly +-2.0, so
     * {@code < -2.0} and {@code <= -2.0} agree (the centre point it divides is always a half-integer).
     */
    static List<String> equivalentMutantProofFailures() {
        List<String> failures = new ArrayList<>();
        try {
            Method sampleValue = DensitySampler.Bound.class.getMethod("sampleValue", int.class, int.class, int.class);
            if (sampleValue.getReturnType() != float.class) {
                failures.add("the lava noise is not sampled as a float");
            }
        } catch (ReflectiveOperationException e) {
            failures.add("cannot read DensitySampler.Bound.sampleValue: " + e);
        }
        if ((double)(float)LAVA_NOISE_THRESHOLD == LAVA_NOISE_THRESHOLD) {
            failures.add("0.3 is a float value, so '> 0.3' differs from '>= 0.3'");
        }
        long atBound = 0;
        for (int level1 = -PROOF_LEVEL_BOUND; level1 <= PROOF_LEVEL_BOUND; ++level1) {
            for (int level2 = -PROOF_LEVEL_BOUND; level2 < level1; ++level2) {
                for (int blockY = -PROOF_Y_BOUND; blockY <= PROOF_Y_BOUND; ++blockY) {
                    double gradient = BaseAquifer.pressureGradient(blockY, level1, level2, level1 - level2);
                    if (Math.abs(gradient) == 2.0) {
                        atBound++;
                    }
                }
            }
        }
        if (atBound > 0) {
            failures.add("the pressure gradient is exactly +-2.0 " + atBound + " times, so '< -2.0' differs from '<= -2.0'");
        }
        return failures;
    }

    private static void checkSimilarity(Similarity candidate, Accumulator accumulator) {
        for (int difference = -SIMILARITY_DIFFERENCE_LIMIT; difference <= SIMILARITY_DIFFERENCE_LIMIT; ++difference) {
            int distanceSqr1 = Math.max(0, -difference);
            compareSimilarity(candidate, distanceSqr1, distanceSqr1 + difference, accumulator);
        }
        RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(SIMILARITY_RANDOM_PAIRS);
        for (int i = 0; i < SIMILARITY_RANDOM_PAIRS; ++i) {
            compareSimilarity(candidate, random.nextInt(DISTANCE_SQR_BOUND), random.nextInt(DISTANCE_SQR_BOUND), accumulator);
        }
    }

    private static void compareSimilarity(Similarity candidate, int distanceSqr1, int distanceSqr2, Accumulator accumulator) {
        double expected = invokeSimilarity(distanceSqr1, distanceSqr2);
        double actual = candidate.of(distanceSqr1, distanceSqr2);
        accumulator.compare("similarity(" + distanceSqr1 + ", " + distanceSqr2 + ")", expected, actual);
    }

    /** Every pair of statuses on a level grid, every combination of the three fluids, a column of heights; three pressures sharing one barrier noise. */
    private static void checkPressure(Aquifer reference, C2meAquifer candidate, Accumulator accumulator) {
        BlockState[] fluids = {Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(), Blocks.AIR.defaultBlockState()};
        for (int level1 : PRESSURE_LEVELS) {
            for (int level2 : PRESSURE_LEVELS) {
                for (int type1 = 0; type1 < fluids.length; ++type1) {
                    for (int type2 = 0; type2 < fluids.length; ++type2) {
                        Aquifer.FluidStatus status1 = new Aquifer.FluidStatus(level1, fluids[type1]);
                        Aquifer.FluidStatus status2 = new Aquifer.FluidStatus(level2, fluids[type2]);
                        Aquifer.FluidStatus status3 = new Aquifer.FluidStatus(level2 - 2, fluids[(type1 + 1) % fluids.length]);
                        for (int center : new int[] {level1, level2, Math.floorDiv(level1 + level2, 2)}) {
                            for (int blockY = center - PRESSURE_Y_REACH; blockY <= center + PRESSURE_Y_REACH; ++blockY) {
                                comparePressures(reference, candidate, blockY * 31 + 7, blockY, blockY * 17 - 3, status1, status2, status3, accumulator);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void comparePressures(Aquifer reference, C2meAquifer candidate, int x, int y, int z, Aquifer.FluidStatus status1,
                                         Aquifer.FluidStatus status2, Aquifer.FluidStatus status3, Accumulator accumulator) {
        MutableDouble shared = new MutableDouble(Double.NaN);
        candidate.forgetBarrierNoise();
        String where = " at (" + x + ", " + y + ", " + z + ") with " + status1 + ", " + status2 + ", " + status3;
        accumulator.compare("pressure 1-2" + where, invokePressure(reference, x, y, z, shared, status1, status2), candidate.pressure(x, y, z, status1, status2));
        accumulator.compare("pressure 1-3" + where, invokePressure(reference, x, y, z, shared, status1, status3), candidate.pressure(x, y, z, status1, status3));
        accumulator.compare("pressure 2-3" + where, invokePressure(reference, x, y, z, shared, status2, status3), candidate.pressure(x, y, z, status2, status3));
    }

    private static void checkFluidType(Aquifer reference, C2meAquifer candidate, Accumulator accumulator) {
        Aquifer.FluidStatus[] globals = {
            new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()),
            new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState()),
            new Aquifer.FluidStatus(DimensionType.WAY_BELOW_MIN_Y, Blocks.AIR.defaultBlockState())
        };
        RandomGenerator random = RandomGeneratorFactory.of(PRNG).create(FLUID_TYPE_POSITIONS);
        for (int i = 0; i < FLUID_TYPE_POSITIONS; ++i) {
            int x = random.nextInt(-FLUID_TYPE_COORDINATE_BOUND, FLUID_TYPE_COORDINATE_BOUND);
            int y = random.nextInt(-FLUID_TYPE_Y_BOUND, FLUID_TYPE_Y_BOUND);
            int z = random.nextInt(-FLUID_TYPE_COORDINATE_BOUND, FLUID_TYPE_COORDINATE_BOUND);
            for (int level : FLUID_TYPE_LEVELS) {
                for (Aquifer.FluidStatus global : globals) {
                    BlockState expected = invokeFluidType(reference, x, y, z, global, level);
                    BlockState actual = candidate.computeFluidType(x, y, z, global, level);
                    boolean reachedLavaEdge = level == LAVA_EDGE_LEVEL && expected.is(Blocks.LAVA) && !global.fluidType().is(Blocks.LAVA);
                    accumulator.compareState("fluidType at (" + x + ", " + y + ", " + z + ") level " + level + " global " + global, expected, actual,
                        reachedLavaEdge);
                }
            }
        }
    }

    private static double invokeSimilarity(int distanceSqr1, int distanceSqr2) {
        try {
            return (double)MOJANG_SIMILARITY.invoke(distanceSqr1, distanceSqr2);
        } catch (Throwable t) {
            throw new IllegalStateException("Mojang's similarity threw", t);
        }
    }

    private static double invokePressure(Object reference, int x, int y, int z, MutableDouble noise, Aquifer.FluidStatus status1,
                                         Aquifer.FluidStatus status2) {
        try {
            return (double)MOJANG_PRESSURE.invoke(reference, x, y, z, noise, status1, status2);
        } catch (Throwable t) {
            throw new IllegalStateException("Mojang's calculatePressure threw", t);
        }
    }

    private static BlockState invokeFluidType(Object reference, int x, int y, int z, Aquifer.FluidStatus global, int level) {
        try {
            return (BlockState)MOJANG_FLUID_TYPE.invoke(reference, x, y, z, global, level);
        } catch (Throwable t) {
            throw new IllegalStateException("Mojang's computeFluidType threw", t);
        }
    }

    private static MethodHandle unreflect(Method method) throws IllegalAccessException {
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method);
    }

    private static final class Accumulator {
        private long checks;
        private long differences;
        private long lavaAtEdge;
        private long wrappedCells;
        private String first = "";

        void compare(String what, double expected, double actual) {
            this.checks++;
            if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
                this.differ(what + ": expected " + expected + " got " + actual);
            }
        }

        void compareLocation(int index, long mojangLocation, C2meAquifer candidate) {
            this.checks++;
            int x = BlockPos.getX(mojangLocation);
            int y = BlockPos.getY(mojangLocation);
            int z = BlockPos.getZ(mojangLocation);
            this.wrappedCells += y > WRAPPED_Y_FROM_BELOW ? 1 : 0;
            if (candidate.locationX(index) != x || candidate.locationY(index) != y || candidate.locationZ(index) != z) {
                this.differ("cell " + index + ": expected (" + x + ", " + y + ", " + z + ") got (" + candidate.locationX(index) + ", "
                    + candidate.locationY(index) + ", " + candidate.locationZ(index) + ")");
            }
        }

        void compareState(String what, BlockState expected, BlockState actual, boolean reachedLavaEdge) {
            this.checks++;
            this.lavaAtEdge += reachedLavaEdge ? 1 : 0;
            if (expected != actual) {
                this.differ(what + ": expected " + expected + " got " + actual);
            }
        }

        private void differ(String detail) {
            this.differences++;
            if (this.first.isEmpty()) {
                this.first = detail;
            }
        }

        Result result() {
            return new Result(this.checks, this.differences, this.first, this.lavaAtEdge, this.wrappedCells);
        }
    }
}
