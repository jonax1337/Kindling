package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** RangeChoiceFunction.ConstSampler: both branches constant, so nothing to prune; Paper's choose() per element. */
final class ConstRangeChoiceNode extends Node {
    private final Node input;
    private final float minInclusive;
    private final float maxExclusive;
    private final float whenInRange;
    private final float whenOutOfRange;

    ConstRangeChoiceNode(Node input, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange) {
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


    @Override
    Node[] children() {
        return new Node[] {this.input};
    }

    /** Paper's choose() on v[0, size) in place. */
    static void choose(float[] v, int size, float minInclusive, float maxExclusive, float whenInRange, float whenOutOfRange) {
        for (int i = 0; i < size; ++i) {
            float input = v[i];
            v[i] = input >= minInclusive && input < maxExclusive ? whenInRange : whenOutOfRange;
        }
    }
}
