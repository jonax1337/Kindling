package net.kindlingmc.worldgen.density;

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

    LerpNode(Node alpha, Node first, Node second) {
        this.alpha = alpha;
        this.first = first;
        this.second = second;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        try (ScopedDensityBuffer alphaBuffer = frame.acquire(region);
             ScopedDensityBuffer firstBuffer = frame.acquire(region);
             ScopedDensityBuffer secondBuffer = frame.acquire(region)) {
            this.alpha.evaluate(frame, region, alphaBuffer, demand);
            float[] alphas = alphaBuffer.values;
            evaluateSide(frame, region, firstBuffer, this.first, demand, alphas, 1.0F);
            evaluateSide(frame, region, secondBuffer, this.second, demand, alphas, 0.0F);
            merge(out.values, alphas, firstBuffer.values, secondBuffer.values, demand.size);
        }
    }

    @Override
    Node[] children() {
        return new Node[] {this.alpha, this.first, this.second};
    }

    /** Evaluates side where the parent demand holds and alpha != skipAlpha. */
    private static void evaluateSide(Frame frame, DensityVolume region, DensityBuffer buffer, Node side, Demand demand, float[] alphas, float skipAlpha) {
        Demand sideDemand = frame.push(demand.size);
        try {
            boolean[] needed = demand.mask;
            boolean[] mask = sideDemand.mask;
            int count = 0;
            for (int i = 0; i < demand.size; ++i) {
                mask[i] = needed[i] && alphas[i] != skipAlpha;
                count += mask[i] ? 1 : 0;
            }
            sideDemand.count = count;
            if (count > 0) {
                side.evaluate(frame, region, buffer, sideDemand);
            }
        } finally {
            frame.pop(1);
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
