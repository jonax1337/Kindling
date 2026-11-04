package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * IntervalSelectFunction's Sampler and SingleThresholdSampler (one threshold t: input < t picks the first, anything
 * else, NaN included, the last; the same as the general rule). Paper evaluates every branch on the whole volume;
 * here a branch is evaluated only where it is picked.
 */
final class IntervalSelectNode extends Node {
    private final Node input;
    private final float[] thresholds;
    private final Node[] branches;

    IntervalSelectNode(Node input, float[] thresholds, Node[] branches) {
        this.input = input;
        this.thresholds = thresholds;
        this.branches = branches;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        try (ScopedDensityBuffer inputBuffer = frame.acquire(region)) {
            this.input.evaluate(frame, region, inputBuffer, demand);
            float[] input = inputBuffer.values;
            for (int branch = 0; branch < this.branches.length; ++branch) {
                Demand picked = frame.push(demand.size);
                try {
                    this.pick(demand, input, branch, picked);
                    if (!picked.isEmpty()) {
                        this.evaluateBranch(frame, region, out, branch, picked);
                    }
                } finally {
                    frame.pop(1);
                }
            }
        }
    }

    @Override
    Node[] children() {
        Node[] children = new Node[this.branches.length + 1];
        children[0] = this.input;
        System.arraycopy(this.branches, 0, children, 1, this.branches.length);
        return children;
    }

    private void pick(Demand demand, float[] input, int branch, Demand picked) {
        boolean[] needed = demand.mask;
        boolean[] mask = picked.mask;
        int count = 0;
        for (int i = 0; i < demand.size; ++i) {
            mask[i] = needed[i] && this.selectSamplerIndex(input[i]) == branch;
            count += mask[i] ? 1 : 0;
        }
        picked.count = count;
    }

    private void evaluateBranch(Frame frame, DensityVolume region, DensityBuffer out, int branch, Demand picked) {
        try (ScopedDensityBuffer branchBuffer = frame.acquire(region)) {
            this.branches[branch].evaluate(frame, region, branchBuffer, picked);
            float[] v = out.values;
            float[] b = branchBuffer.values;
            boolean[] take = picked.mask;
            for (int i = 0; i < picked.size; ++i) {
                if (take[i]) {
                    v[i] = b[i];
                }
            }
        }
    }

    /** Paper's selectSamplerIndex: the first threshold above the input, else the last branch (NaN included). */
    private int selectSamplerIndex(float input) {
        for (int i = 0; i < this.thresholds.length; i++) {
            if (input < this.thresholds[i]) {
                return i;
            }
        }
        return this.branches.length - 1;
    }
}
