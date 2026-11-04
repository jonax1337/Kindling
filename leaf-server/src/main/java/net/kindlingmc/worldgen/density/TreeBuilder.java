package net.kindlingmc.worldgen.density;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.SliceFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;

/**
 * Builds the corner-level node graph from Paper's compiled samplers, memoized by identity (the tree is a DAG, so a
 * shared cache stays one node). Public sampler records are matched by record patterns, the private ones of
 * RangeChoice, IntervalSelect and BlendDensity by class and read through SamplerRecords. Everything else becomes an
 * OpaqueNode that calls Paper's sampler, so an unknown sampler type of a datapack is evaluated by Paper's own code.
 */
final class TreeBuilder {
    private static final String OP = "net.minecraft.world.level.levelgen.densityfunction.op.";
    private static final Class<?> RANGE_CHOICE = SamplerRecords.type(OP + "RangeChoiceFunction$Sampler");
    private static final Class<?> RANGE_CHOICE_CONST = SamplerRecords.type(OP + "RangeChoiceFunction$ConstSampler");
    private static final Class<?> INTERVAL_SELECT = SamplerRecords.type(OP + "IntervalSelectFunction$Sampler");
    private static final Class<?> INTERVAL_SELECT_SINGLE = SamplerRecords.type(OP + "IntervalSelectFunction$SingleThresholdSampler");
    private static final Class<?> BLEND_DENSITY = SamplerRecords.type(OP + "BlendDensityFunction$Sampler");

    private final Map<DensitySampler, Node> built = new IdentityHashMap<>();
    private int touchingLeaves;

    /** How many touching leaves were built; the size of a frame's stamp array. */
    int touchingLeaves() {
        return this.touchingLeaves;
    }

    Node build(DensitySampler sampler) {
        Node node = this.built.get(sampler);
        if (node == null) {
            node = this.create(sampler);
            this.built.put(sampler, node);
        }
        return node;
    }

    private Node create(DensitySampler sampler) {
        return switch (sampler) {
            case ConstantFunction.Sampler(float value) -> new ConstNode(value);
            case BinaryFunction.AddSampler(DensitySampler left, DensitySampler right) -> this.binary(BinaryNode.Kind.ADD, left, right);
            case BinaryFunction.SubSampler(DensitySampler left, DensitySampler right) -> this.binary(BinaryNode.Kind.SUB, left, right);
            case BinaryFunction.MulSampler(DensitySampler left, DensitySampler right) -> this.binary(BinaryNode.Kind.MUL, left, right);
            case BinaryFunction.DivSampler(DensitySampler left, DensitySampler right) -> this.binary(BinaryNode.Kind.DIV, left, right);
            case BinaryFunction.MinSampler(DensitySampler left, DensitySampler right, float ignored) -> this.binary(BinaryNode.Kind.MIN, left, right);
            case BinaryFunction.MaxSampler(DensitySampler left, DensitySampler right, float ignored) -> this.binary(BinaryNode.Kind.MAX, left, right);
            case BinaryFunction.ConstAddSampler(DensitySampler input, float c) -> this.constBinary(ConstBinaryNode.Kind.ADD, input, c);
            case BinaryFunction.ConstMulSampler(DensitySampler input, float c) -> this.constBinary(ConstBinaryNode.Kind.MUL, input, c);
            case BinaryFunction.ConstSubSampler(float c, DensitySampler input) -> this.constBinary(ConstBinaryNode.Kind.SUB_FROM, input, c);
            case BinaryFunction.ConstDivSampler(float c, DensitySampler input) -> this.constBinary(ConstBinaryNode.Kind.DIV_INTO, input, c);
            case BinaryFunction.ConstMinSampler(DensitySampler input, float c) -> this.constBinary(ConstBinaryNode.Kind.MIN, input, c);
            case BinaryFunction.ConstMaxSampler(DensitySampler input, float c) -> this.constBinary(ConstBinaryNode.Kind.MAX, input, c);
            case UnaryFunction.AbsSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.ABS, input);
            case UnaryFunction.CubeSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.CUBE, input);
            case UnaryFunction.LogSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.LOG, input);
            case UnaryFunction.NegateSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.NEGATE, input);
            case UnaryFunction.ReciprocalSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.RECIPROCAL, input);
            case UnaryFunction.SignSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.SIGN, input);
            case UnaryFunction.SqrtSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.SQRT, input);
            case UnaryFunction.SquareSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.SQUARE, input);
            case UnaryFunction.SqueezeSampler(DensitySampler input) -> this.unary(UnaryNode.Kind.SQUEEZE, input);
            case UnaryFunction.LeakyReLUSampler(DensitySampler input, float factor) -> new UnaryNode(UnaryNode.Kind.LEAKY_RELU, this.build(input), factor, 0.0F);
            case ClampFunction.Sampler(DensitySampler input, float min, float max) -> new UnaryNode(UnaryNode.Kind.CLAMP, this.build(input), min, max);
            case LerpFunction.Sampler(DensitySampler alpha, DensitySampler first, DensitySampler second) ->
                new LerpNode(this.build(alpha), this.build(first), this.build(second));
            case LerpFunction.ConstFirstSampler(DensitySampler alpha, float first, DensitySampler second) ->
                new LerpNode(this.build(alpha), new ConstNode(first), this.build(second));
            case LerpFunction.ConstSecondSampler(DensitySampler alpha, DensitySampler first, float second) ->
                new LerpNode(this.build(alpha), this.build(first), new ConstNode(second));
            case SliceFunction.XSampler(DensitySampler input, int x) -> this.slice(input, new boolean[] {true, false, false}, new int[] {x, 0, 0});
            case SliceFunction.YSampler(DensitySampler input, int y) -> this.slice(input, new boolean[] {false, true, false}, new int[] {0, y, 0});
            case SliceFunction.ZSampler(DensitySampler input, int z) -> this.slice(input, new boolean[] {false, false, true}, new int[] {0, 0, z});
            case SliceFunction.XzSampler(DensitySampler input, int x, int z) -> this.slice(input, new boolean[] {true, false, true}, new int[] {x, 0, z});
            case CachingDensitySampler(int cacheId, DensitySampler input) -> new CacheNode(cacheId, input, this.build(input));
            default -> this.createFromPrivateRecord(sampler);
        };
    }

    private Node createFromPrivateRecord(DensitySampler sampler) {
        Class<?> type = sampler.getClass();
        if (type == RANGE_CHOICE) {
            return new RangeChoiceNode(this.build(sampler(sampler, "input")), floatOf(sampler, "minInclusive"), floatOf(sampler, "maxExclusive"),
                this.build(sampler(sampler, "whenInRange")), this.build(sampler(sampler, "whenOutOfRange")));
        } else if (type == RANGE_CHOICE_CONST) {
            return new ConstRangeChoiceNode(this.build(sampler(sampler, "input")), floatOf(sampler, "minInclusive"),
                floatOf(sampler, "maxExclusive"), floatOf(sampler, "whenInRange"), floatOf(sampler, "whenOutOfRange"));
        } else if (type == INTERVAL_SELECT) {
            return this.intervalSelect(sampler(sampler, "input"), (float[])SamplerRecords.component(sampler, "thresholds"),
                (DensitySampler[])SamplerRecords.component(sampler, "samplers"));
        } else if (type == INTERVAL_SELECT_SINGLE) {
            return this.intervalSelect(sampler(sampler, "input"), new float[] {floatOf(sampler, "threshold")},
                new DensitySampler[] {sampler(sampler, "ifBelow"), sampler(sampler, "ifAbove")});
        } else if (type == BLEND_DENSITY) {
            return new BlendNode(this.build(sampler(sampler, "input")), sampler);
        }
        return this.opaque(sampler);
    }

    private Node binary(BinaryNode.Kind kind, DensitySampler left, DensitySampler right) {
        return new BinaryNode(kind, this.build(left), this.build(right));
    }

    private Node constBinary(ConstBinaryNode.Kind kind, DensitySampler input, float c) {
        return new ConstBinaryNode(kind, this.build(input), c);
    }

    private Node unary(UnaryNode.Kind kind, DensitySampler input) {
        return new UnaryNode(kind, this.build(input), 0.0F, 0.0F);
    }

    private Node slice(DensitySampler input, boolean[] collapse, int[] coordinate) {
        return new SliceNode(this.build(input), collapse, coordinate);
    }

    private Node intervalSelect(DensitySampler input, float[] thresholds, DensitySampler[] samplers) {
        Node[] branches = new Node[samplers.length];
        for (int i = 0; i < samplers.length; ++i) {
            branches[i] = this.build(samplers[i]);
        }
        return new IntervalSelectNode(this.build(input), thresholds.clone(), branches);
    }

    private Node opaque(DensitySampler sampler) {
        int touchIndex = Purity.reachesCache(sampler) ? this.touchingLeaves++ : -1;
        return new OpaqueNode(sampler, Purity.isVolumePure(sampler), touchIndex);
    }

    private static DensitySampler sampler(DensitySampler record, String component) {
        return (DensitySampler)SamplerRecords.component(record, component);
    }

    private static float floatOf(DensitySampler record, String component) {
        return (Float)SamplerRecords.component(record, component);
    }
}
