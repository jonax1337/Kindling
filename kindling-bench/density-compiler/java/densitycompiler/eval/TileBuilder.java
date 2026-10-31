package densitycompiler.eval;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction;

/**
 * Builds the block stage from Paper's compiled samplers above the interpolated functions, memoized by identity.
 * Element-wise samplers, range choices and interpolated functions become column tiles; anything else is a
 * FullVolumeTile around its step-1 rebuild, so every tree is supported.
 */
final class TileBuilder {
    private static final String OP = "net.minecraft.world.level.levelgen.densityfunction.op.";
    private static final Class<?> RANGE_CHOICE = SamplerRecords.type(OP + "RangeChoiceFunction$Sampler");
    private static final Class<?> RANGE_CHOICE_CONST = SamplerRecords.type(OP + "RangeChoiceFunction$ConstSampler");

    private final BlockLevelRebuild rebuild;
    private final Map<DensitySampler, Tile> built = new IdentityHashMap<>();
    private final List<InterpolatedTile> interpolated = new ArrayList<>();
    private final List<PruneCounter> counters = new ArrayList<>();
    private int nextNumber;

    TileBuilder(BlockLevelRebuild rebuild) {
        this.rebuild = rebuild;
    }

    /** The block stage of root; calls the step-1 tree for volumes it does not tile. */
    TileStage build(DensitySampler root, TileFrame frame) {
        Tile tile = this.tile(root);
        return new TileStage(tile, List.copyOf(this.interpolated), frame, this.rebuild.rebuild(root));
    }

    List<PruneCounter> counters() {
        return this.counters;
    }

    private Tile tile(DensitySampler sampler) {
        Tile tile = this.built.get(sampler);
        if (tile == null) {
            tile = this.create(sampler);
            this.built.put(sampler, tile);
        }
        return tile;
    }

    private Tile create(DensitySampler sampler) {
        return switch (sampler) {
            case ConstantFunction.Sampler(float value) -> new ConstTile(value);
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
            case UnaryFunction.LeakyReLUSampler(DensitySampler input, float factor) ->
                new UnaryTile(UnaryNode.Kind.LEAKY_RELU, this.tile(input), factor, 0.0F);
            case ClampFunction.Sampler(DensitySampler input, float min, float max) -> new UnaryTile(UnaryNode.Kind.CLAMP, this.tile(input), min, max);
            default -> this.createFromPrivateRecord(sampler);
        };
    }

    private Tile createFromPrivateRecord(DensitySampler sampler) {
        Class<?> type = sampler.getClass();
        if (type == BlockLevelRebuild.INTERPOLATED) {
            return this.interpolated(sampler);
        } else if (type == RANGE_CHOICE) {
            return this.rangeChoice(sampler);
        } else if (type == RANGE_CHOICE_CONST) {
            return new ConstRangeChoiceTile(this.tile(sampler(sampler, "input")), floatOf(sampler, "minInclusive"), floatOf(sampler, "maxExclusive"),
                floatOf(sampler, "whenInRange"), floatOf(sampler, "whenOutOfRange"));
        }
        return new FullVolumeTile(this.rebuild.rebuild(sampler));
    }

    private Tile binary(BinaryNode.Kind kind, DensitySampler left, DensitySampler right) {
        return new BinaryTile(kind, this.tile(left), this.tile(right));
    }

    private Tile constBinary(ConstBinaryNode.Kind kind, DensitySampler input, float c) {
        return new ConstBinaryTile(kind, this.tile(input), c);
    }

    private Tile unary(UnaryNode.Kind kind, DensitySampler input) {
        return new UnaryTile(kind, this.tile(input), 0.0F, 0.0F);
    }

    private Tile interpolated(DensitySampler sampler) {
        int[] cellSizes = {(Integer)SamplerRecords.component(sampler, "cellSizeXz"), (Integer)SamplerRecords.component(sampler, "cellSizeY")};
        float[] cellSizesInv = {floatOf(sampler, "cellSizeXzInv"), floatOf(sampler, "cellSizeYInv")};
        PruneCounter counter = this.counter("#b" + this.nextNumber++ + " block interpolated: corner volume evaluated");
        InterpolatedTile tile = new InterpolatedTile(this.rebuild.cornerOf(sampler), cellSizes, cellSizesInv, counter);
        this.interpolated.add(tile);
        return tile;
    }

    private Tile rangeChoice(DensitySampler sampler) {
        float min = floatOf(sampler, "minInclusive");
        float max = floatOf(sampler, "maxExclusive");
        String label = "#b" + this.nextNumber++ + " block range_choice [" + min + ", " + max + ") columns";
        Tile[] branches = {this.tile(sampler(sampler, "whenInRange")), this.tile(sampler(sampler, "whenOutOfRange"))};
        PruneCounter[] counters = {this.counter(label + " in"), this.counter(label + " out")};
        return new RangeChoiceTile(this.tile(sampler(sampler, "input")), min, max, branches, counters);
    }

    private PruneCounter counter(String label) {
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
