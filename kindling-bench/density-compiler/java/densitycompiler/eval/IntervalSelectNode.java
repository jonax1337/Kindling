package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * IntervalSelectFunction's Sampler and SingleThresholdSampler (one threshold t: input < t picks the first, anything
 * else, NaN included, the last; the same as the general rule). Paper evaluates every branch on the whole volume;
 * with pruning a branch is evaluated only where it is picked.
 */
final class IntervalSelectNode extends Node {
    private final Node input;
    private final float[] thresholds;
    private final Node[] branches;
    private final PruneCounter[] counters;

    IntervalSelectNode(String label, Node input, float[] thresholds, Node[] branches, PruneCounter[] counters) {
        super(label);
        this.input = input;
        this.thresholds = thresholds;
        this.branches = branches;
        this.counters = counters;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        try (ScopedDensityBuffer inputBuffer = frame.acquire(region)) {
            this.input.evaluate(frame, region, inputBuffer, demand);
            for (int branch = 0; branch < this.branches.length; ++branch) {
                Demand picked = this.pickedDemand(frame, demand, inputBuffer.values, branch);
                try {
                    this.evaluateBranch(frame, region, out, inputBuffer.values, branch, picked);
                } finally {
                    frame.pop(1);
                }
            }
        }
    }

    /** Pushed on the frame; the caller pops it. Without pruning it is the whole parent demand. */
    private Demand pickedDemand(Frame frame, Demand demand, float[] input, int branch) {
        Demand picked = frame.push(demand.size);
        if (!frame.pruning()) {
            picked.copyFrom(demand);
            return picked;
        }
        boolean[] needed = demand.mask;
        boolean[] mask = picked.mask;
        int count = 0;
        for (int i = 0; i < demand.size; ++i) {
            mask[i] = needed[i] && this.selectSamplerIndex(input[i]) == branch;
            count += mask[i] ? 1 : 0;
        }
        picked.count = count;
        return picked;
    }

    private void evaluateBranch(Frame frame, DensityVolume region, DensityBuffer out, float[] input, int branch, Demand picked) {
        int size = region.size();
        this.counters[branch].recordBranch(size, picked.count);
        if (picked.isEmpty()) {
            return;
        }
        try (ScopedDensityBuffer branchBuffer = frame.acquire(region)) {
            this.branches[branch].evaluate(frame, region, branchBuffer, picked);
            float[] v = out.values;
            float[] b = branchBuffer.values;
            boolean[] take = picked.mask;
            for (int i = 0; i < size; ++i) {
                if (take[i] && this.selectSamplerIndex(input[i]) == branch) {
                    v[i] = b[i];
                }
            }
        }
    }

    private int selectSamplerIndex(float input) {
        for (int i = 0; i < this.thresholds.length; i++) {
            if (input < this.thresholds[i]) {
                return i;
            }
        }
        return this.branches.length - 1;
    }
}
