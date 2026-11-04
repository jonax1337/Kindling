package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * A Paper sampler the evaluator does not take apart (noises, gradients, splines, context-bound, anything unknown).
 * Volume-pure ones may be called on the bounding box or the y-runs of a partial demand, whichever the cost model
 * prefers; every other one is always called on the whole region, which is the volume Paper would pass. A leaf that
 * may reach a cache cell ("touching") records each call in the frame, so that the corner sampler can still run it
 * when pruning skipped it, and the context's cells end up as Paper leaves them.
 */
final class OpaqueNode extends Node {
    final DensitySampler paper;
    private final boolean volumePure;
    /** The stamp index of a touching leaf, -1 for a leaf that reaches no cache cell. */
    final int touchIndex;

    OpaqueNode(DensitySampler paper, boolean volumePure, int touchIndex) {
        this.paper = paper;
        this.volumePure = volumePure;
        this.touchIndex = touchIndex;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        if (this.touchIndex >= 0) {
            frame.stamp(this.touchIndex);
        }
        if (!this.volumePure || demand.isAll()) {
            this.paper.sampleVolume(frame.context(), out, region);
            return;
        }
        LeafCalls calls = frame.leafCalls();
        Tuning tuning = frame.tuning();
        calls.scan(region, demand, tuning.runGap());
        long fullCost = region.size() + tuning.callCost();
        long boxCost = calls.boxPoints() + tuning.callCost();
        long runsCost = calls.runPoints() + (long)tuning.callCost() * calls.runCount();
        if (fullCost <= boxCost && fullCost <= runsCost) {
            this.paper.sampleVolume(frame.context(), out, region);
        } else if (boxCost <= runsCost) {
            calls.box(frame, this.paper, region, out);
        } else {
            calls.runs(frame, this.paper, region, out);
        }
    }

    @Override
    Node[] children() {
        return new Node[0];
    }
}
