package densitycompiler;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.TilingMode;
import densitycompiler.eval.Tuning;

/**
 * Differential test on synthetic density trees, checked against Paper's own samplers of the same trees. The real
 * routers never put a value exactly on a selector threshold, never produce NaN and never repeat a volume in a way
 * that matters, so verify on real worlds cannot see a selector that is off by one comparison. These trees can: all
 * inputs are small exact integers (periodic gradients on all three axes), so thresholds are hit exactly, and
 * log / 0 / 0 produce NaN, infinities and both zeros. Selectors under test: range choice, interval select, lerp,
 * each as the whole block level, below an interpolated function (corner level) and, for range choice, with
 * interpolated branches (the shape of the overworld); plus cache nodes shared by branches with different demands.
 * Every case runs on a chunk, unaligned boxes, a column, cell-aligned pass-through boxes and stepped volumes, at
 * three tunings of the leaf-call cost model.
 */
final class SyntheticSuite {
    private static final Pattern SEED_IN_LOCATION = Pattern.compile("seed (-?\\d+)");
    private static final int PERIOD = 32;
    private static final float[][] RANGES = {
        {0.0F, 8.0F}, {8.0F, 16.0F}, {16.0F, 32.0F}, {-0.0F, 0.0F}, {-0.0F, 8.0F}, {8.0F, 8.0F}, {31.0F, 32.0F}, {7.5F, 8.5F},
        {Float.NEGATIVE_INFINITY, 8.0F}, {8.0F, Float.POSITIVE_INFINITY}, {Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY},
    };
    private static final float[][] THRESHOLDS = {{8.0F}, {8.0F, 16.0F}, {0.0F, 8.0F, 16.0F}, {-0.0F, 0.0F, 8.0F}};
    private static final Tuning[] EXTRA_TUNINGS = {new Tuning(0, 0), new Tuning(100_000, 8)};

    private final List<String> names = new ArrayList<>();
    private final List<World> worlds = new ArrayList<>();
    private final World base;

    private SyntheticSuite(World base) {
        this.base = base;
    }

    /** Runs the suite and returns one tally per candidate and shape; case names are printed for failing tallies. */
    static List<Verifier.Tally> run(World base) {
        SyntheticSuite suite = new SyntheticSuite(base);
        suite.buildCases();
        List<Chunk> volumes = suite.volumes();
        System.out.printf("synthetic: %d trees, %d volumes each (%d calls)%n", suite.worlds.size(), volumes.size() / suite.worlds.size(), volumes.size());
        List<Verifier.Tally> tallies = Verifier.fresh(suite.worlds, Variants.reference(), candidates(), " synthetic").verify(volumes);
        for (Verifier.Tally tally : tallies) {
            if (!tally.passed()) {
                System.out.println("  first failing tree of " + tally.candidate() + ": " + suite.describe(tally.first().where()));
            }
        }
        return tallies;
    }

    /** Pruning and tiling at the default tuning and, because the leaf-call plan depends on it, at two extreme ones. */
    private static List<Variant> candidates() {
        List<Variant> candidates = new ArrayList<>(Variants.candidates().stream().filter(variant -> !variant.name().startsWith("COMPILED")).toList());
        candidates.addAll(Variants.compiled(Tuning.DEFAULT));
        for (Tuning tuning : EXTRA_TUNINGS) {
            String suffix = " tuning " + tuning.callCost() + "/" + tuning.runGap();
            for (CompiledVariant variant : Variants.compiled(tuning)) {
                if (variant.name().equals("COMPILED_PRUNE") || variant.name().equals("COMPILED")) {
                    candidates.add(variant.named(suffix));
                }
            }
        }
        return candidates;
    }

    private String describe(String location) {
        Matcher matcher = SEED_IN_LOCATION.matcher(location);
        return matcher.find() ? this.names.get(Integer.parseInt(matcher.group(1))) : location;
    }

    // ---- the trees ----

    private void buildCases() {
        DensityFunction[] inputs = inputs();
        String[] inputNames = {"u", "u+v", "-(u-8)", "log(u-8)", "(u-8)/(v-8)"};
        DensityFunction[][] branchPairs = {
            {add(w(), c(1000)), mul(v(), c(-3))},
            {div(c(1), sub(w(), c(5))), log(sub(w(), c(5)))},
            {log(sub(w(), c(5))), add(w(), c(1000))},
            {c(1), c(2)},
        };
        String[] branchNames = {"finite", "inf/nan", "nan/finite", "const"};
        for (int i = 0; i < inputs.length; ++i) {
            for (float[] range : RANGES) {
                for (int b = 0; b < branchPairs.length; ++b) {
                    String label = "range_choice(" + inputNames[i] + ", [" + range[0] + ", " + range[1] + "), " + branchNames[b] + ")";
                    DensityFunction plain = DensityFunctions.rangeChoice(inputs[i], range[0], range[1], branchPairs[b][0], branchPairs[b][1]);
                    this.addAboveAndBelow(label, plain);
                    if (b == 0) {
                        this.add(label + " with interpolated branches", DensityFunctions.rangeChoice(inputs[i], range[0], range[1],
                            interpolated(branchPairs[b][0]), interpolated(branchPairs[b][1])));
                    }
                }
            }
        }
        this.buildLerps(branchPairs);
        this.buildIntervalSelects(inputs, inputNames, branchPairs);
        this.buildSharedCaches(inputs);
    }

    private void buildLerps(DensityFunction[][] branchPairs) {
        DensityFunction[] alphas = {
            gradient(Direction.Axis.Y, 4, 1.0F),
            sub(c(1), gradient(Direction.Axis.Y, 4, 1.0F)),
            DensityFunctions.min(c(1), div(u(), c(4))),
            log(sub(u(), c(2))),
            div(sub(u(), c(8)), sub(v(), c(8))),
        };
        String[] alphaNames = {"0..0.75", "1..0.25", "min(1,u/4)", "log(u-2)", "(u-8)/(v-8)"};
        DensityFunction[][] sides = {
            {branchPairs[0][0], branchPairs[0][1]},
            {branchPairs[1][0], branchPairs[1][1]},
            {branchPairs[1][1], branchPairs[1][0]},
            {branchPairs[0][0], branchPairs[1][0]},
            {c(3), branchPairs[0][1]},
            {branchPairs[0][0], c(4)},
        };
        for (int a = 0; a < alphas.length; ++a) {
            for (int s = 0; s < sides.length; ++s) {
                this.addAboveAndBelow("lerp(" + alphaNames[a] + ", sides " + s + ")", DensityFunctions.lerp(alphas[a], sides[s][0], sides[s][1]));
            }
        }
    }

    private void buildIntervalSelects(DensityFunction[] inputs, String[] inputNames, DensityFunction[][] branchPairs) {
        for (int i = 0; i < inputs.length; ++i) {
            for (float[] thresholds : THRESHOLDS) {
                for (int b = 0; b < 2; ++b) {
                    List<DensityFunction> branches = new ArrayList<>();
                    for (int k = 0; k <= thresholds.length; ++k) {
                        branches.add(add(branchPairs[b][k & 1], c(k)));
                    }
                    this.addAboveAndBelow("interval_select(" + inputNames[i] + ", " + Arrays.toString(thresholds) + ", branches " + b + ")",
                        DensityFunctions.intervalSelect(inputs[i], new FloatArrayList(thresholds), branches));
                }
            }
        }
    }

    /** One cache id read by branches with different (overlapping, partial) demands: the node must compute the union. */
    private void buildSharedCaches(DensityFunction[] inputs) {
        DensityFunction cached = DensityFunctions.cache(add(w(), c(1000)));
        DensityFunction other = DensityFunctions.cache(mul(v(), c(-3)));
        for (int i = 0; i < inputs.length; ++i) {
            this.addAboveAndBelow("shared cache range_choice " + i, DensityFunctions.rangeChoice(inputs[i], 8.0F, 16.0F, cached, add(cached, c(1))));
            this.addAboveAndBelow("shared cache lerp " + i, DensityFunctions.lerp(inputs[i], cached, other));
        }
        this.addAboveAndBelow("shared cache overlapping ranges", DensityFunctions.add(
            DensityFunctions.rangeChoice(u(), 8.0F, 16.0F, cached, c(0)), DensityFunctions.rangeChoice(u(), 12.0F, 20.0F, c(0), cached)));
        this.addAboveAndBelow("shared cache in two selectors", DensityFunctions.add(
            DensityFunctions.intervalSelect(u(), new FloatArrayList(new float[] {8.0F, 16.0F}), List.of(cached, c(0), other)),
            DensityFunctions.lerp(DensityFunctions.min(c(1), div(u(), c(4))), c(0), cached)));
    }

    private void addAboveAndBelow(String label, DensityFunction tree) {
        this.add(label + " (block level)", tree);
        this.add(label + " (corner level)", interpolated(tree));
    }

    private void add(String label, DensityFunction tree) {
        this.worlds.add(new World(this.worlds.size(), this.base.randomState(), this.base.noiseSettings(), tree, List.of(tree)));
        this.names.add(label);
    }

    // ---- the volumes ----

    private List<Chunk> volumes() {
        List<Chunk> volumes = new ArrayList<>();
        for (World world : this.worlds) {
            int minY = world.noiseSettings().minY();
            volumes.add(Chunk.chunk(world, 0, 0, Beardifier.EMPTY));
            volumes.add(Chunk.box(world, new DensityVolume(16, 112, 16, -13, minY + 14, 7)));
            volumes.add(Chunk.column(world, 5, 9));
            volumes.add(Chunk.box(world, new DensityVolume(8, 24, 8, 4, minY + 8, 12)));
            volumes.add(Chunk.box(world, new DensityVolume(1, 1, 1, 4, minY + 8, 8)));
            volumes.add(Chunk.box(world, new DensityVolume(5, 25, 5, 0, minY, 0, 4, 8, 4)));
            volumes.add(Chunk.box(world, new DensityVolume(4, 9, 3, 1, minY + 4, -3, 4, 8, 4)));
        }
        return volumes;
    }

    // ---- building blocks: exact small integers on every axis ----

    private static DensityFunction c(float value) {
        return DensityFunctions.constant(value);
    }

    private static DensityFunction add(DensityFunction left, DensityFunction right) {
        return DensityFunctions.add(left, right);
    }

    private static DensityFunction sub(DensityFunction left, DensityFunction right) {
        return DensityFunctions.sub(left, right);
    }

    private static DensityFunction mul(DensityFunction left, DensityFunction right) {
        return DensityFunctions.mul(left, right);
    }

    private static DensityFunction div(DensityFunction left, DensityFunction right) {
        return DensityFunctions.div(left, right);
    }

    private static DensityFunction log(DensityFunction input) {
        return DensityFunctions.log(input);
    }

    private static DensityFunction interpolated(DensityFunction input) {
        return DensityFunctions.interpolated(input, 4, 8);
    }

    private static DensityFunction gradient(Direction.Axis axis, int period, float top) {
        return DensityFunctions.gradient(axis, TilingMode.REPEAT, 0, period, 0.0F, top);
    }

    /** Coordinate modulo 32 on the y axis, as an exact float. */
    private static DensityFunction u() {
        return gradient(Direction.Axis.Y, PERIOD, PERIOD);
    }

    private static DensityFunction v() {
        return gradient(Direction.Axis.X, PERIOD, PERIOD);
    }

    private static DensityFunction w() {
        return gradient(Direction.Axis.Z, PERIOD, PERIOD);
    }

    /** Plain values, mixed axes, minus zero, NaN and minus infinity, and NaN / infinity / both zeros together. */
    private static DensityFunction[] inputs() {
        return new DensityFunction[] {
            u(),
            add(u(), v()),
            mul(sub(u(), c(8)), c(-1)),
            log(sub(u(), c(8))),
            div(sub(u(), c(8)), sub(v(), c(8))),
        };
    }
}
