package densitycompiler.eval;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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
 * RangeChoice, IntervalSelect and BlendDensity by class and read through SamplerRecords. Everything else becomes
 * an OpaqueNode that calls Paper's sampler.
 */
final class TreeBuilder {
    private static final String OP = "net.minecraft.world.level.levelgen.densityfunction.op.";
    private static final Class<?> RANGE_CHOICE = SamplerRecords.type(OP + "RangeChoiceFunction$Sampler");
    private static final Class<?> RANGE_CHOICE_CONST = SamplerRecords.type(OP + "RangeChoiceFunction$ConstSampler");
    private static final Class<?> INTERVAL_SELECT = SamplerRecords.type(OP + "IntervalSelectFunction$Sampler");
    private static final Class<?> INTERVAL_SELECT_SINGLE = SamplerRecords.type(OP + "IntervalSelectFunction$SingleThresholdSampler");
    private static final Class<?> BLEND_DENSITY = SamplerRecords.type(OP + "BlendDensityFunction$Sampler");

    private final Map<DensitySampler, Node> built = new IdentityHashMap<>();
    private final List<PruneCounter> counters = new ArrayList<>();
    private int nextNumber;

    List<PruneCounter> counters() {
        return this.counters;
    }

    Node build(DensitySampler sampler) {
        Node node = this.built.get(sampler);
        if (node == null) {
            node = this.create(sampler, "#" + this.nextNumber++);
            this.built.put(sampler, node);
        }
        return node;
    }

    private Node create(DensitySampler sampler, String id) {
        return switch (sampler) {
            case ConstantFunction.Sampler(float value) -> new ConstNode(id + " const " + value, value);
            case BinaryFunction.AddSampler(DensitySampler left, DensitySampler right) -> this.binary(id, BinaryNode.Kind.ADD, left, right);
            case BinaryFunction.SubSampler(DensitySampler left, DensitySampler right) -> this.binary(id, BinaryNode.Kind.SUB, left, right);
            case BinaryFunction.MulSampler(DensitySampler left, DensitySampler right) -> this.binary(id, BinaryNode.Kind.MUL, left, right);
            case BinaryFunction.DivSampler(DensitySampler left, DensitySampler right) -> this.binary(id, BinaryNode.Kind.DIV, left, right);
            case BinaryFunction.MinSampler(DensitySampler left, DensitySampler right, float ignored) -> this.binary(id, BinaryNode.Kind.MIN, left, right);
            case BinaryFunction.MaxSampler(DensitySampler left, DensitySampler right, float ignored) -> this.binary(id, BinaryNode.Kind.MAX, left, right);
            case BinaryFunction.ConstAddSampler(DensitySampler input, float c) -> this.constBinary(id, ConstBinaryNode.Kind.ADD, input, c);
            case BinaryFunction.ConstMulSampler(DensitySampler input, float c) -> this.constBinary(id, ConstBinaryNode.Kind.MUL, input, c);
            case BinaryFunction.ConstSubSampler(float c, DensitySampler input) -> this.constBinary(id, ConstBinaryNode.Kind.SUB_FROM, input, c);
            case BinaryFunction.ConstDivSampler(float c, DensitySampler input) -> this.constBinary(id, ConstBinaryNode.Kind.DIV_INTO, input, c);
            case BinaryFunction.ConstMinSampler(DensitySampler input, float c) -> this.constBinary(id, ConstBinaryNode.Kind.MIN, input, c);
            case BinaryFunction.ConstMaxSampler(DensitySampler input, float c) -> this.constBinary(id, ConstBinaryNode.Kind.MAX, input, c);
            case UnaryFunction.AbsSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.ABS, input);
            case UnaryFunction.CubeSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.CUBE, input);
            case UnaryFunction.LogSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.LOG, input);
            case UnaryFunction.NegateSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.NEGATE, input);
            case UnaryFunction.ReciprocalSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.RECIPROCAL, input);
            case UnaryFunction.SignSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.SIGN, input);
            case UnaryFunction.SqrtSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.SQRT, input);
            case UnaryFunction.SquareSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.SQUARE, input);
            case UnaryFunction.SqueezeSampler(DensitySampler input) -> this.unary(id, UnaryNode.Kind.SQUEEZE, input);
            case UnaryFunction.LeakyReLUSampler(DensitySampler input, float factor) ->
                new UnaryNode(id + " leaky_relu " + factor, UnaryNode.Kind.LEAKY_RELU, this.build(input), factor, 0.0F);
            case ClampFunction.Sampler(DensitySampler input, float min, float max) ->
                new UnaryNode(id + " clamp [" + min + ", " + max + "]", UnaryNode.Kind.CLAMP, this.build(input), min, max);
            case LerpFunction.Sampler(DensitySampler alpha, DensitySampler first, DensitySampler second) ->
                this.lerp(id, alpha, this.build(first), this.build(second));
            case LerpFunction.ConstFirstSampler(DensitySampler alpha, float first, DensitySampler second) ->
                this.lerp(id, alpha, new ConstNode(id + " first " + first, first), this.build(second));
            case LerpFunction.ConstSecondSampler(DensitySampler alpha, DensitySampler first, float second) ->
                this.lerp(id, alpha, this.build(first), new ConstNode(id + " second " + second, second));
            case SliceFunction.XSampler(DensitySampler input, int x) -> this.slice(id + " slice x=" + x, input, new boolean[] {true, false, false}, new int[] {x, 0, 0});
            case SliceFunction.YSampler(DensitySampler input, int y) -> this.slice(id + " slice y=" + y, input, new boolean[] {false, true, false}, new int[] {0, y, 0});
            case SliceFunction.ZSampler(DensitySampler input, int z) -> this.slice(id + " slice z=" + z, input, new boolean[] {false, false, true}, new int[] {0, 0, z});
            case SliceFunction.XzSampler(DensitySampler input, int x, int z) ->
                this.slice(id + " slice x=" + x + " z=" + z, input, new boolean[] {true, false, true}, new int[] {x, 0, z});
            case CachingDensitySampler(int cacheId, DensitySampler input) -> new CacheNode(id + " cache " + cacheId, this.build(input));
            default -> this.createFromPrivateRecord(sampler, id);
        };
    }

    private Node createFromPrivateRecord(DensitySampler sampler, String id) {
        Class<?> type = sampler.getClass();
        if (type == RANGE_CHOICE) {
            return this.rangeChoice(sampler, id);
        } else if (type == RANGE_CHOICE_CONST) {
            return new ConstRangeChoiceNode(id + " range_choice_const", this.build(sampler(sampler, "input")), floatOf(sampler, "minInclusive"),
                floatOf(sampler, "maxExclusive"), floatOf(sampler, "whenInRange"), floatOf(sampler, "whenOutOfRange"));
        } else if (type == INTERVAL_SELECT) {
            return this.intervalSelect(id, sampler(sampler, "input"), (float[])SamplerRecords.component(sampler, "thresholds"),
                (DensitySampler[])SamplerRecords.component(sampler, "samplers"));
        } else if (type == INTERVAL_SELECT_SINGLE) {
            return this.intervalSelect(id, sampler(sampler, "input"), new float[] {floatOf(sampler, "threshold")},
                new DensitySampler[] {sampler(sampler, "ifBelow"), sampler(sampler, "ifAbove")});
        } else if (type == BLEND_DENSITY) {
            return new BlendNode(id + " blend_density", this.build(sampler(sampler, "input")), sampler);
        }
        return this.opaque(sampler, id);
    }

    private Node binary(String id, BinaryNode.Kind kind, DensitySampler left, DensitySampler right) {
        return new BinaryNode(id + " " + kind, kind, this.build(left), this.build(right));
    }

    private Node constBinary(String id, ConstBinaryNode.Kind kind, DensitySampler input, float c) {
        return new ConstBinaryNode(id + " const " + kind + " " + c, kind, this.build(input), c);
    }

    private Node unary(String id, UnaryNode.Kind kind, DensitySampler input) {
        return new UnaryNode(id + " " + kind, kind, this.build(input), 0.0F, 0.0F);
    }

    private Node lerp(String id, DensitySampler alpha, Node first, Node second) {
        String label = id + " lerp";
        PruneCounter[] counters = {this.branchCounter(label + " first"), this.branchCounter(label + " second")};
        return new LerpNode(label, this.build(alpha), new Node[] {first, second}, counters);
    }

    private Node slice(String label, DensitySampler input, boolean[] collapse, int[] coordinate) {
        return new SliceNode(label, this.build(input), collapse, coordinate);
    }

    private Node rangeChoice(DensitySampler sampler, String id) {
        float min = floatOf(sampler, "minInclusive");
        float max = floatOf(sampler, "maxExclusive");
        String label = id + " range_choice [" + min + ", " + max + ")";
        Node input = this.build(sampler(sampler, "input"));
        Node[] branches = {this.build(sampler(sampler, "whenInRange")), this.build(sampler(sampler, "whenOutOfRange"))};
        PruneCounter[] counters = {this.branchCounter(label + " in"), this.branchCounter(label + " out")};
        return new RangeChoiceNode(label, input, min, max, branches, counters);
    }

    private Node intervalSelect(String id, DensitySampler input, float[] thresholds, DensitySampler[] samplers) {
        String label = id + " interval_select " + thresholds.length;
        Node[] branches = new Node[samplers.length];
        PruneCounter[] counters = new PruneCounter[samplers.length];
        for (int i = 0; i < samplers.length; ++i) {
            branches[i] = this.build(samplers[i]);
            counters[i] = this.branchCounter(label + " branch " + i);
        }
        return new IntervalSelectNode(label, this.build(input), thresholds.clone(), branches, counters);
    }

    private Node opaque(DensitySampler sampler, String id) {
        boolean pure = Purity.isVolumePure(sampler);
        String name = sampler.getClass().getName();
        String label = id + " leaf " + name.substring(name.lastIndexOf('.') + 1) + (pure ? " (pure)" : " (full only)");
        PruneCounter counter = PruneCounter.leaf(label);
        this.counters.add(counter);
        return new OpaqueNode(label, sampler, pure, counter);
    }

    private PruneCounter branchCounter(String label) {
        PruneCounter counter = PruneCounter.branch(label);
        this.counters.add(counter);
        return counter;
    }

    private static DensitySampler sampler(DensitySampler record, String component) {
        return (DensitySampler)SamplerRecords.component(record, component);
    }

    private static float floatOf(DensitySampler record, String component) {
        return (Float)SamplerRecords.component(record, component);
    }
}
