package net.kindlingmc.worldgen.density;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Which values of a cache cell are present. Paper fills a cell with the whole volume; the pruning evaluator fills
 * only the points some branch reads and leaves this marker on the cell. Every read of the cell from Paper's code
 * that needs a missing value first calls {@link #complete}, which computes the cell's input exactly as Paper would
 * have (Paper's own sampler, on the whole volume) through an uncached view of the context, so no other cell changes.
 * So every later read gives Paper's bits, and the cell holds the same volume as Paper's at all times.
 *
 * <p>Owned by the cell of one context, which one thread uses at a time; it is dropped when the cell is evicted.
 */
public final class PendingCacheCell {
    private static final LongAdder COMPLETIONS = new LongAdder();

    private final DensitySampler input;
    private final boolean[] computed;
    private int computedCount;

    PendingCacheCell(DensitySampler input, int size) {
        this.input = input;
        this.computed = new boolean[size];
    }

    /** How many cells were completed by a later read, over all contexts (diagnostics and tests). */
    static long completions() {
        return COMPLETIONS.sum();
    }

    public boolean isComputed(int index) {
        return this.computed[index];
    }

    boolean isComplete() {
        return this.computedCount == this.computed.length;
    }

    /** Marks in missing the demanded points not computed yet; returns their number. */
    int missing(Demand demand, Demand missing) {
        boolean[] needed = demand.mask;
        boolean[] mask = missing.mask;
        int count = 0;
        for (int i = 0; i < demand.size; ++i) {
            mask[i] = needed[i] && !this.computed[i];
            count += mask[i] ? 1 : 0;
        }
        missing.count = count;
        return count;
    }

    /** Copies the points of missing from values into the cell and marks them computed. */
    void store(float[] values, float[] cell, Demand missing) {
        boolean[] mask = missing.mask;
        for (int i = 0; i < missing.size; ++i) {
            if (mask[i]) {
                cell[i] = values[i];
                this.computed[i] = true;
            }
        }
        this.computedCount += missing.count;
    }

    /** Fills every missing value of the cell with Paper's value for it. */
    public void complete(SamplerContext context, DensityVolume volume, DensityBuffer cell) {
        if (this.isComplete()) {
            return;
        }
        COMPLETIONS.increment();
        try (ScopedDensityBuffer values = context.acquireBuffer(volume)) {
            this.input.sampleVolume(context.kindling$uncached(), values, volume);
            for (int i = 0; i < this.computed.length; ++i) {
                if (!this.computed[i]) {
                    cell.values[i] = values.values[i];
                }
            }
        }
        Arrays.fill(this.computed, true);
        this.computedCount = this.computed.length;
    }
}
