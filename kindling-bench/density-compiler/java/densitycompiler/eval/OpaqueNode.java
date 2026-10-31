package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * A Paper sampler the evaluator does not take apart (noises, gradients, splines, context-bound, anything unknown).
 * Volume-pure ones may be called on the bounding box or the y-runs of a partial demand, whichever the cost model
 * prefers; every other one is always called on the whole region, which is the volume Paper would pass.
 */
final class OpaqueNode extends Node {
    private final DensitySampler paper;
    private final boolean volumePure;
    private final PruneCounter counter;

    OpaqueNode(String label, DensitySampler paper, boolean volumePure, PruneCounter counter) {
        super(label);
        this.paper = paper;
        this.volumePure = volumePure;
        this.counter = counter;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        int size = region.size();
        if (!this.volumePure || demand.isAll()) {
            this.full(frame, region, out, demand);
            return;
        }
        LeafCalls calls = frame.leafCalls();
        calls.scan(region, demand, frame.tuning().runGap());
        int callCost = frame.tuning().callCost();
        long fullCost = size + callCost;
        long boxCost = calls.boxPoints() + callCost;
        long runsCost = calls.runPoints() + (long)callCost * calls.runCount();
        if (fullCost <= boxCost && fullCost <= runsCost) {
            this.full(frame, region, out, demand);
        } else if (boxCost <= runsCost) {
            calls.box(frame, this.paper, region, out);
            this.counter.recordLeaf(size, demand.count, PruneCounter.Plan.BOX, calls.boxPoints(), 1);
        } else {
            calls.runs(frame, this.paper, region, out);
            this.counter.recordLeaf(size, demand.count, PruneCounter.Plan.RUNS, calls.runPoints(), calls.runCount());
        }
    }

    private void full(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        this.paper.sampleVolume(frame.context(), out, region);
        this.counter.recordLeaf(region.size(), demand.count, PruneCounter.Plan.FULL, region.size(), 1);
    }
}
