package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** BinaryFunction's samplers with one constant side, Paper's per-element expression verbatim. */
final class ConstBinaryNode extends Node {
    enum Kind {
        /** ConstAddSampler: input + c (also SUB with a constant right side, c already negated by Paper). */
        ADD,
        /** ConstMulSampler: input * c (also DIV by a constant, c = 1 / divisor computed by Paper). */
        MUL,
        /** ConstSubSampler: c - input. */
        SUB_FROM,
        /** ConstDivSampler: c / input. */
        DIV_INTO,
        /** ConstMinSampler: c where c < input. */
        MIN,
        /** ConstMaxSampler: c where c > input. */
        MAX
    }

    private final Kind kind;
    private final Node input;
    private final float constant;

    ConstBinaryNode(String label, Kind kind, Node input, float constant) {
        super(label);
        this.kind = kind;
        this.input = input;
        this.constant = constant;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        this.input.evaluate(frame, region, out, demand);
        apply(this.kind, out.values, region.size(), this.constant);
    }

    /** Paper's expression of kind with constant c on v[0, size) in place; also used by the block stage. */
    static void apply(Kind kind, float[] v, int size, float c) {
        switch (kind) {
            case ADD -> add(v, size, c);
            case MUL -> mul(v, size, c);
            case SUB_FROM -> subFrom(v, size, c);
            case DIV_INTO -> divInto(v, size, c);
            case MIN -> min(v, size, c);
            case MAX -> max(v, size, c);
        }
    }

    private static void add(float[] v, int size, float c) {
        for (int i = 0; i < size; ++i) {
            v[i] = v[i] + c;
        }
    }

    private static void mul(float[] v, int size, float c) {
        for (int i = 0; i < size; ++i) {
            v[i] = v[i] * c;
        }
    }

    private static void subFrom(float[] v, int size, float c) {
        for (int i = 0; i < size; ++i) {
            v[i] = c - v[i];
        }
    }

    private static void divInto(float[] v, int size, float c) {
        for (int i = 0; i < size; ++i) {
            v[i] = c / v[i];
        }
    }

    private static void min(float[] v, int size, float c) {
        for (int i = 0; i < size; ++i) {
            if (c < v[i]) {
                v[i] = c;
            }
        }
    }

    private static void max(float[] v, int size, float c) {
        for (int i = 0; i < size; ++i) {
            if (c > v[i]) {
                v[i] = c;
            }
        }
    }
}
