package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * RangeChoiceFunction.Sampler. Paper evaluates both branches on the whole volume and keeps whenOutOfRange where
 * !(input >= min) || !(input < max). With pruning, the input is evaluated first and each branch only where Paper
 * keeps its value (NaN input counts as out of range, as in Paper).
 */
final class RangeChoiceNode extends Node {
    private final Node input;
    private final float minInclusive;
    private final float maxExclusive;
    private final Node whenInRange;
    private final Node whenOutOfRange;
    private final PruneCounter inRangeCounter;
    private final PruneCounter outOfRangeCounter;

    /** Branches is (whenInRange, whenOutOfRange), counters the pruning counter of each branch in the same order. */
    RangeChoiceNode(String label, Node input, float minInclusive, float maxExclusive, Node[] branches, PruneCounter[] counters) {
        super(label);
        this.input = input;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = branches[0];
        this.whenOutOfRange = branches[1];
        this.inRangeCounter = counters[0];
        this.outOfRangeCounter = counters[1];
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        int size = region.size();
        try (ScopedDensityBuffer inputBuffer = frame.acquire(region)) {
            this.input.evaluate(frame, region, inputBuffer, demand);
            Demand inRange = frame.push(size);
            Demand outOfRange = frame.push(size);
            try {
                this.split(frame, demand, inputBuffer.values, inRange, outOfRange);
                this.evaluateBranches(frame, region, out, inputBuffer.values, inRange, outOfRange);
            } finally {
                frame.pop(2);
            }
        }
    }

    private void split(Frame frame, Demand demand, float[] input, Demand inRange, Demand outOfRange) {
        if (!frame.pruning()) {
            inRange.copyFrom(demand);
            outOfRange.copyFrom(demand);
            return;
        }
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

    private void evaluateBranches(Frame frame, DensityVolume region, DensityBuffer out, float[] input, Demand inRange, Demand outOfRange) {
        int size = region.size();
        this.inRangeCounter.recordBranch(size, inRange.count);
        this.outOfRangeCounter.recordBranch(size, outOfRange.count);
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
            for (int i = 0; i < size; ++i) {
                float value = input[i];
                if (take[i] && (!(value >= this.minInclusive) || !(value < this.maxExclusive))) {
                    v[i] = o[i];
                }
            }
        }
    }
}
