package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Stands in for the input of one InterpolatedFunction.Sampler: the corner volume Paper's interpolation asks for is
 * evaluated by the node graph with every point demanded, then the context is brought to the state Paper's own input
 * would leave it in (see {@link CornerPlan}). Point queries go to Paper's input sampler. Immutable; every call gets
 * its state from a {@link Frame}.
 */
final class CornerSampler implements DensitySampler {
    private final Node root;
    private final DensitySampler paper;
    private final CornerPlan plan;
    private final Tuning tuning;
    private final int stampCount;

    CornerSampler(Node root, DensitySampler paper, CornerPlan plan, Tuning tuning, int stampCount) {
        this.root = root;
        this.paper = paper;
        this.plan = plan;
        this.tuning = tuning;
        this.stampCount = stampCount;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        if (this.holdsCellForRegion(context, volume)) {
            this.paper.sampleVolume(context, outputBuffer, volume);
            return;
        }
        Frame frame = Frame.enter(context, this.tuning, this.stampCount);
        try {
            Demand all = frame.push(volume.size());
            all.setAll(volume.size());
            this.root.evaluate(frame, volume, outputBuffer, all);
            frame.pop(1);
            this.leaveContextAsPaper(frame, context, volume);
        } finally {
            frame.exit();
        }
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return this.paper.sampleValue(context, blockX, blockY, blockZ);
    }

    /**
     * Whether a cache of the graph already holds the region Paper's evaluation would ask it for. Paper then answers
     * from the cell and leaves everything below that cache alone; the evaluator would replay and reinstall what lies
     * below, so Paper's own input runs instead (a context that is read twice with the same corner volume).
     */
    private boolean holdsCellForRegion(SamplerContext context, DensityVolume volume) {
        if (!context.kindling$cachesEnabled()) {
            return false;
        }
        CornerPlan plan = this.plan;
        for (int i = 0; i < plan.caches.length; ++i) {
            if (context.kindling$cachedBuffer(plan.caches[i].cacheId, plan.cachePaths[i].apply(volume)) != null) {
                return true;
            }
        }
        return false;
    }

    /** Runs the touching leaves pruning skipped and gives every cache of the graph a cell for its region. */
    private void leaveContextAsPaper(Frame frame, SamplerContext context, DensityVolume volume) {
        CornerPlan plan = this.plan;
        for (int i = 0; i < plan.leaves.length; ++i) {
            OpaqueNode leaf = plan.leaves[i];
            if (!frame.stamped(leaf.touchIndex)) {
                DensityVolume region = plan.leafPaths[i].apply(volume);
                try (ScopedDensityBuffer ignored = context.acquireBuffer(region)) {
                    leaf.paper.sampleVolume(context, ignored, region);
                }
            }
        }
        if (!context.kindling$cachesEnabled()) {
            return;
        }
        for (int i = 0; i < plan.caches.length; ++i) {
            CacheNode cache = plan.caches[i];
            DensityVolume region = plan.cachePaths[i].apply(volume);
            if (context.kindling$cachedBuffer(cache.cacheId, region) == null) {
                context.kindling$install(cache.cacheId, region, new PendingCacheCell(cache.paperInput, region.size()));
            }
        }
    }
}
