package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * CachingDensitySampler, kept in Paper's own cache cell of the context: the same id, the same volume, the same
 * eviction when a different volume comes. Unlike Paper's cell it may hold only the points some branch needed; a
 * {@link PendingCacheCell} on the cell says which, so a branch never recomputes what a sibling filled and every later
 * read from Paper's code completes the cell first. Computing a subset is exact because the input is volume-pure below
 * an interpolated function (prototype DESIGN, "Purity").
 */
final class CacheNode extends Node {
    final int cacheId;
    final DensitySampler paperInput;
    private final Node input;

    CacheNode(int cacheId, DensitySampler paperInput, Node input) {
        this.cacheId = cacheId;
        this.paperInput = paperInput;
        this.input = input;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        SamplerContext context = frame.context();
        if (!context.kindling$cachesEnabled()) {
            this.input.evaluate(frame, region, out, demand);
            return;
        }
        ScopedDensityBuffer cell = context.kindling$cachedBuffer(this.cacheId, region);
        PendingCacheCell pending;
        if (cell == null) {
            pending = new PendingCacheCell(this.paperInput, region.size());
            cell = context.kindling$install(this.cacheId, region, pending);
        } else {
            pending = context.kindling$pending(this.cacheId);
        }
        if (pending != null && !pending.isComplete() && !this.computeMissing(frame, region, demand, pending)) {
            this.input.evaluate(frame, region, out, demand);
            return;
        }
        System.arraycopy(cell.values, 0, out.values, 0, region.size());
    }

    @Override
    Node[] children() {
        return new Node[] {this.input};
    }

    /** Computes the demanded points the cell lacks; false if the cell was replaced meanwhile (then nothing is stored). */
    private boolean computeMissing(Frame frame, DensityVolume region, Demand demand, PendingCacheCell pending) {
        Demand missing = frame.push(demand.size);
        try {
            if (pending.missing(demand, missing) == 0) {
                return true;
            }
            try (ScopedDensityBuffer values = frame.acquire(region)) {
                this.input.evaluate(frame, region, values, missing);
                SamplerContext context = frame.context();
                ScopedDensityBuffer cell = context.kindling$cachedBuffer(this.cacheId, region);
                if (cell == null || context.kindling$pending(this.cacheId) != pending) {
                    return false;
                }
                pending.store(values.values, cell.values, missing);
                return true;
            }
        } finally {
            frame.pop(1);
        }
    }
}
