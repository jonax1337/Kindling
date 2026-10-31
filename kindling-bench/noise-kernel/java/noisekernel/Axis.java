package noisekernel;

import net.minecraft.world.level.levelgen.synth.GradientNoise;

/**
 * Per-axis tables (floor, smoothstep alpha, relative position) of one volume, computed once per call.
 * The arithmetic mirrors the per-axis precompute of PerlinNoise.addToVolume operation by operation.
 */
final class Axis {
    /** SharedConstants.DEBUG_ENABLE_FARLANDS is false in every release build. */
    private static final double HALF_ROUND_OFF = Math.nextDown(1.6777216E7);
    private static final double ROUND_OFF_PERIOD = 3.3554432E7;

    int[] floor = new int[0];
    float[] alpha = new float[0];
    float[] relative = new float[0];

    static double wrap(double x) {
        return x >= -HALF_ROUND_OFF && x < HALF_ROUND_OFF ? x : x - Math.floor(x / ROUND_OFF_PERIOD + 0.5) * ROUND_OFF_PERIOD;
    }

    static float smoothstep(float x) {
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    void fillPlain(int size, int min, int step, double scale, double offset) {
        this.ensure(size);
        double coordinate = min;
        for (int i = 0; i < size; ++i, coordinate += step) {
            double value = wrap(coordinate * scale) + offset;
            double floorD = Math.floor(value);
            float relativePosition = (float)(value - floorD);
            this.floor[i] = (int)floorD;
            this.relative[i] = relativePosition;
            this.alpha[i] = smoothstep(relativePosition);
        }
    }

    void fillSmeared(int size, int min, int step, double scale, double offset, double fudgeYScale) {
        this.ensure(size);
        double coordinate = min;
        for (int i = 0; i < size; ++i, coordinate += step) {
            double original = coordinate * scale;
            double value = wrap(original) + offset;
            double floorD = Math.floor(value);
            double relativePosition = value - floorD;
            this.floor[i] = (int)floorD;
            this.relative[i] = (float)(relativePosition - computeFudgeY(original, relativePosition, fudgeYScale));
            this.alpha[i] = smoothstep((float)relativePosition);
        }
    }

    /** SmearedPerlinNoise.computeFudgeY; the float literal is widened to double on purpose. */
    private static double computeFudgeY(double originalY, double relativeY, double fudgeYScale) {
        double fudgeLimit = originalY >= 0.0 && originalY < relativeY ? originalY : relativeY;
        return (int)Math.floor(fudgeLimit / fudgeYScale + 1.0E-7F) * fudgeYScale;
    }

    private void ensure(int size) {
        if (this.floor.length < size) {
            this.floor = new int[size];
            this.alpha = new float[size];
            this.relative = new float[size];
        }
    }

    /** Gradient components as floats, indexed by the low 4 bits of a hash (GradientNoise.GRADIENT). */
    static final float[] GRADIENT_X = new float[16];
    static final float[] GRADIENT_Y = new float[16];
    static final float[] GRADIENT_Z = new float[16];

    static {
        for (int i = 0; i < 16; ++i) {
            GradientNoise.Gradient gradient = GradientNoise.GRADIENT[i];
            GRADIENT_X[i] = (float)gradient.x();
            GRADIENT_Y[i] = (float)gradient.y();
            GRADIENT_Z[i] = (float)gradient.z();
        }
    }
}
