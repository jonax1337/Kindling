package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * RangeChoiceFunction.Sampler. Paper evaluates both branches on the whole volume and keeps whenOutOfRange where
 * !(input >= min) || !(input < max). Here the input is evaluated first and each branch only where Paper keeps its
 * value (a NaN input counts as out of range, as in Paper).
 */
final class RangeChoiceNode extends Node {
    private final Node input;
    private final float minInclusive;
    private final float maxExclusive;
    private final Node whenInRange;
    private final Node whenOutOfRange;

    RangeChoiceNode(Node input, float minInclusive, float maxExclusive, Node whenInRange, Node whenOutOfRange) {
        this.input = input;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = whenInRange;
        this.whenOutOfRange = whenOutOfRange;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        try (ScopedDensityBuffer inputBuffer = frame.acquire(region)) {
            this.input.evaluate(frame, region, inputBuffer, demand);
            Demand inRange = frame.push(demand.size);
            Demand outOfRange = frame.push(demand.size);
            try {
                this.split(demand, inputBuffer.values, inRange, outOfRange);
                this.evaluateBranches(frame, region, out, inRange, outOfRange);
            } finally {
                frame.pop(2);
            }
        }
    }

    @Override
    Node[] children() {
        return new Node[] {this.input, this.whenInRange, this.whenOutOfRange};
    }

    private void split(Demand demand, float[] input, Demand inRange, Demand outOfRange) {
        boolean[] needed = demand.mask;
        boolean[] in = inRange.mask;
        boolean[] outside = outOfRange.mask;
        int inCount = 0;
        int outCount = 0;
        for (int i = 0; i < demand.size; ++i) {
            boolean isIn = input[i] >= this.minInclusive && input[i] < this.maxExclusive;
            in[i] = needed[i] && isIn;
            outside[i] = needed[i] && !isIn;
            inCount += in[i] ? 1 : 0;
            outCount += outside[i] ? 1 : 0;
        }
        inRange.count = inCount;
        outOfRange.count = outCount;
    }

    private void evaluateBranches(Frame frame, DensityVolume region, DensityBuffer out, Demand inRange, Demand outOfRange) {
        if (!inRange.isEmpty()) {
            this.whenInRange.evaluate(frame, region, out, inRange);
        }
        if (outOfRange.isEmpty()) {
            return;
        }
        try (ScopedDensityBuffer outOfRangeBuffer = frame.acquire(region)) {
            this.whenOutOfRange.evaluate(frame, region, outOfRangeBuffer, outOfRange);
            float[] v = out.values;
            float[] o = outOfRangeBuffer.values;
            boolean[] take = outOfRange.mask;
            for (int i = 0; i < outOfRange.size; ++i) {
                if (take[i]) {
                    v[i] = o[i];
                }
            }
        }
    }
}
