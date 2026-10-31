package densitycompiler.eval;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * LerpFunction's three samplers (a constant side is a ConstNode: filling a buffer with the constant and reading it
 * back gives the same bits). Paper's cascade per element: alpha == 0 -> first, alpha == 1 -> second, else
 * Mth.lerp(alpha, first, second). So first is read where alpha != 1 and second where alpha != 0 (NaN reads both).
 */
final class LerpNode extends Node {
    private final Node alpha;
    private final Node first;
    private final Node second;
    private final PruneCounter firstCounter;
    private final PruneCounter secondCounter;

    /** Sides is (first, second), counters the pruning counter of each side in the same order. */
    LerpNode(String label, Node alpha, Node[] sides, PruneCounter[] counters) {
        super(label);
        this.alpha = alpha;
        this.first = sides[0];
        this.second = sides[1];
        this.firstCounter = counters[0];
        this.secondCounter = counters[1];
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        int size = region.size();
        try (ScopedDensityBuffer alphaBuffer = frame.acquire(region);
             ScopedDensityBuffer firstBuffer = frame.acquire(region);
             ScopedDensityBuffer secondBuffer = frame.acquire(region)) {
            this.alpha.evaluate(frame, region, alphaBuffer, demand);
            float[] alphas = alphaBuffer.values;
            Demand firstDemand = sideDemand(frame, demand, alphas, 1.0F);
            try {
                evaluateSide(frame, region, firstBuffer, this.first, this.firstCounter, firstDemand);
            } finally {
                frame.pop(1);
            }
            Demand secondDemand = sideDemand(frame, demand, alphas, 0.0F);
            try {
                evaluateSide(frame, region, secondBuffer, this.second, this.secondCounter, secondDemand);
            } finally {
                frame.pop(1);
            }
            merge(out.values, alphas, firstBuffer.values, secondBuffer.values, size);
        }
    }

    /** The side's demand: the parent demand where alpha != skipAlpha. Pushed on the frame; the caller pops it. */
    private static Demand sideDemand(Frame frame, Demand demand, float[] alphas, float skipAlpha) {
        Demand side = frame.push(demand.size);
        if (!frame.pruning()) {
            side.copyFrom(demand);
            return side;
        }
        boolean[] needed = demand.mask;
        boolean[] mask = side.mask;
        int count = 0;
        for (int i = 0; i < demand.size; ++i) {
            mask[i] = needed[i] && alphas[i] != skipAlpha;
            count += mask[i] ? 1 : 0;
        }
        side.count = count;
        return side;
    }

    private static void evaluateSide(Frame frame, DensityVolume region, DensityBuffer buffer, Node side, PruneCounter counter, Demand demand) {
        counter.recordBranch(region.size(), demand.count);
        if (!demand.isEmpty()) {
            side.evaluate(frame, region, buffer, demand);
        }
    }

    private static void merge(float[] v, float[] alphas, float[] firsts, float[] seconds, int size) {
        for (int i = 0; i < size; ++i) {
            float alpha = alphas[i];
            if (alpha == 0.0F) {
                v[i] = firsts[i];
            } else if (alpha == 1.0F) {
                v[i] = seconds[i];
            } else {
                v[i] = Mth.lerp(alpha, firsts[i], seconds[i]);
            }
        }
    }
}
