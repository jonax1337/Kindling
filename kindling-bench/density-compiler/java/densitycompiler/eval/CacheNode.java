package densitycompiler.eval;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * CachingDensitySampler: one cell per cache id, context and exact volume, as Paper's SamplerContext keeps it (a
 * different volume evicts). Unlike Paper's cell it remembers which points it has computed, so a branch that needs
 * a subset computes only the missing points and never misses what a sibling branch filled. This relies on the
 * input being volume-pure, which holds below an interpolated function (DESIGN, "Purity").
 */
final class CacheNode extends Node {
    private final Node input;
    private SamplerContext context;
    private DensityVolume volume;
    private ScopedDensityBuffer values;
    private boolean[] computed = new boolean[0];
    private int computedCount;

    CacheNode(String label, Node input) {
        super(label);
        this.input = input;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        this.bind(frame, region);
        int size = region.size();
        if (this.computedCount < size) {
            this.computeMissing(frame, region, demand);
        }
        System.arraycopy(this.values.values, 0, out.values, 0, size);
    }

    private void bind(Frame frame, DensityVolume region) {
        if (this.context == frame.context() && region.equals(this.volume)) {
            return;
        }
        if (this.context == frame.context() && this.values != null) {
            this.values.close();
        }
        int size = region.size();
        this.context = frame.context();
        this.volume = region;
        this.values = frame.acquire(region);
        if (this.computed.length < size) {
            this.computed = new boolean[size];
        }
        Arrays.fill(this.computed, 0, size, false);
        this.computedCount = 0;
    }

    private void computeMissing(Frame frame, DensityVolume region, Demand demand) {
        int size = region.size();
        Demand missing = frame.push(size);
        try {
            boolean[] needed = demand.mask;
            boolean[] mask = missing.mask;
            int count = 0;
            for (int i = 0; i < size; ++i) {
                mask[i] = needed[i] && !this.computed[i];
                count += mask[i] ? 1 : 0;
            }
            missing.count = count;
            if (count > 0) {
                this.fill(frame, region, missing);
            }
        } finally {
            frame.pop(1);
        }
    }

    /** With nothing computed yet the input may write straight into the cell: it only clobbers uncomputed points. */
    private void fill(Frame frame, DensityVolume region, Demand missing) {
        if (this.computedCount == 0) {
            this.input.evaluate(frame, region, this.values, missing);
        } else {
            try (ScopedDensityBuffer scratch = frame.acquire(region)) {
                this.input.evaluate(frame, region, scratch, missing);
                copyMasked(scratch.values, this.values.values, missing.mask, region.size());
            }
        }
        boolean[] mask = missing.mask;
        for (int i = 0; i < region.size(); ++i) {
            this.computed[i] |= mask[i];
        }
        this.computedCount += missing.count;
    }

    private static void copyMasked(float[] from, float[] to, boolean[] mask, int size) {
        for (int i = 0; i < size; ++i) {
            if (mask[i]) {
                to[i] = from[i];
            }
        }
    }
}
