package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * BinaryFunction's two-input samplers: left into out, right into a scratch buffer, then Paper's per-element
 * expression verbatim (sub is out + -right, min/max compare and keep left unless right wins).
 */
final class BinaryNode extends Node {
    enum Kind { ADD, SUB, MUL, DIV, MIN, MAX }

    private final Kind kind;
    private final Node left;
    private final Node right;

    BinaryNode(String label, Kind kind, Node left, Node right) {
        super(label);
        this.kind = kind;
        this.left = left;
        this.right = right;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        this.left.evaluate(frame, region, out, demand);
        try (ScopedDensityBuffer rightBuffer = frame.acquire(region)) {
            this.right.evaluate(frame, region, rightBuffer, demand);
            combine(this.kind, out.values, rightBuffer.values, region.size());
        }
    }

    /** Paper's expression of kind on v[0, size) with right side r; also used by the block stage. */
    static void combine(Kind kind, float[] v, float[] r, int size) {
        switch (kind) {
            case ADD -> add(v, r, size);
            case SUB -> sub(v, r, size);
            case MUL -> mul(v, r, size);
            case DIV -> div(v, r, size);
            case MIN -> min(v, r, size);
            case MAX -> max(v, r, size);
        }
    }

    private static void add(float[] v, float[] r, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = v[i] + r[i];
        }
    }

    private static void sub(float[] v, float[] r, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = v[i] + -r[i];
        }
    }

    private static void mul(float[] v, float[] r, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = v[i] * r[i];
        }
    }

    private static void div(float[] v, float[] r, int size) {
        for (int i = 0; i < size; ++i) {
            v[i] = v[i] / r[i];
        }
    }

    private static void min(float[] v, float[] r, int size) {
        for (int i = 0; i < size; ++i) {
            float rightValue = r[i];
            if (rightValue < v[i]) {
                v[i] = rightValue;
            }
        }
    }

    private static void max(float[] v, float[] r, int size) {
        for (int i = 0; i < size; ++i) {
            float rightValue = r[i];
            if (rightValue > v[i]) {
                v[i] = rightValue;
            }
        }
    }
}
