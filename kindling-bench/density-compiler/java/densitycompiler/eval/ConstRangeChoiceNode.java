package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** RangeChoiceFunction.ConstSampler: both branches constant, so nothing to prune; Paper's choose() per element. */
final class ConstRangeChoiceNode extends Node {
    private final Node input;
    private final float minInclusive;
    private final float maxExclusive;
    private final float whenInRange;
    private final float whenOutOfRange;

    ConstRangeChoiceNode(String label, Node input, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange) {
        super(label);
        this.input = input;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = whenInRange;
        this.whenOutOfRange = whenOutOfRange;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        this.input.evaluate(frame, region, out, demand);
        choose(out.values, region.size(), this.minInclusive, this.maxExclusive, this.whenInRange, this.whenOutOfRange);
    }

    /** Paper's choose() on v[0, size) in place; also used by the block stage. */
    static void choose(float[] v, int size, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange) {
        for (int i = 0; i < size; ++i) {
            float input = v[i];
            v[i] = input >= minInclusive && input < maxExclusive ? whenInRange : whenOutOfRange;
        }
    }
}
