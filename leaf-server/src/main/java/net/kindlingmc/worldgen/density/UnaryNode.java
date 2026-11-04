package net.kindlingmc.worldgen.density;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * UnaryFunction's samplers and ClampFunction.Sampler: the input into out, then Paper's per-element expression,
 * copied verbatim. One loop per kind, so the element loop has no dispatch.
 */
final class UnaryNode extends Node {
    enum Kind { ABS, CUBE, LEAKY_RELU, LOG, NEGATE, RECIPROCAL, SIGN, SQRT, SQUARE, SQUEEZE, CLAMP }

    private final Kind kind;
    private final Node input;
    private final float first;
    private final float second;

    /** first is LeakyReLU's negativeFactor or the clamp minimum, second the clamp maximum; unused otherwise. */
    UnaryNode(Kind kind, Node input, float first, float second) {
        this.kind = kind;
        this.input = input;
        this.first = first;
        this.second = second;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        this.input.evaluate(frame, region, out, demand);
        apply(this.kind, out.values, region.size(), this.first, this.second);
    }


    @Override
    Node[] children() {
        return new Node[] {this.input};
    }

    /** Paper's expression of kind on v[0, size) in place. */
    static void apply(Kind kind, float[] v, int size, float first, float second) {
        switch (kind) {
            case ABS -> abs(v, size);
            case CUBE -> cube(v, size);
            case LEAKY_RELU -> leakyRelu(v, size, first);
            case LOG -> log(v, size);
            case NEGATE -> negate(v, size);
            case RECIPROCAL -> reciprocal(v, size);
            case SIGN -> sign(v, size);
            case SQRT -> sqrt(v, size);
            case SQUARE -> square(v, size);
            case SQUEEZE -> squeeze(v, size);
            case CLAMP -> clamp(v, size, first, second);
        }
    }

    private static void abs(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = Math.abs(v[i]);
        }
    }

    private static void cube(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = Mth.cube(v[i]);
        }
    }

    private static void leakyRelu(float[] v, int size, float negativeFactor) {
        for (int i = 0; i < size; ++i) {
            float input = v[i];
            v[i] = input > 0.0F ? input : input * negativeFactor;
        }
    }

    private static void log(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = (float)Math.log(v[i]);
        }
    }

    private static void negate(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = -v[i];
        }
    }

    private static void reciprocal(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = 1.0F / v[i];
        }
    }

    private static void sign(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = Math.signum(v[i]);
        }
    }

    private static void sqrt(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = Mth.sqrt(v[i]);
        }
    }

    private static void square(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = Mth.square(v[i]);
        }
    }

    private static void squeeze(float[] v, int size) {
        for (int i = 0; i < size; ++i) {
            float clampedInput = Mth.clamp(v[i], -1.0F, 1.0F);
            v[i] = clampedInput / 2.0F - Mth.cube(clampedInput) / 24.0F;
        }
    }

    private static void clamp(float[] v, int size, float min, float max) {
        for (int i = 0; i < size; ++i) {
            v[i] = Mth.clamp(v[i], min, max);
        }
    }
}
