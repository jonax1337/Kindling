package noisekernel;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * Java Vector API port of the batched volume kernel.
 * Per (x, z) column the eight corner values are staged per y with scalar code, then the trilinear
 * blend runs over y with vectors and a scalar tail. Multiply and add stay separate (no fma).
 */
final class VectorKernel {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final int PERM_MASK = 255;
    private static final int PERM_SHIFT = 8;

    private final Axis axisX = new Axis();
    private final Axis axisY = new Axis();
    private final Axis axisZ = new Axis();

    private float[] c000 = new float[0];
    private float[] c100 = new float[0];
    private float[] c010 = new float[0];
    private float[] c110 = new float[0];
    private float[] c001 = new float[0];
    private float[] c101 = new float[0];
    private float[] c011 = new float[0];
    private float[] c111 = new float[0];

    static int laneCount() {
        return SPECIES.length();
    }

    static String speciesName() {
        return SPECIES.toString();
    }

    void addToVolume(KernelCall call, float[] values) {
        Leaf leaf = call.leaf();
        DensityVolume volume = call.volume();
        int sizeX = volume.sizeX();
        int sizeY = volume.sizeY();
        int sizeZ = volume.sizeZ();
        this.fillAxes(call);
        this.ensureStage(sizeY);

        char[] p = leaf.pAdj;
        for (int indexX = 0; indexX < sizeX; ++indexX) {
            int x0 = p[this.axisX.floor[indexX] & PERM_MASK];
            int x1 = x0 >>> PERM_SHIFT;
            for (int indexZ = 0; indexZ < sizeZ; ++indexZ) {
                this.stageColumn(p, x0, x1, indexX, indexZ, sizeY);
                int index = sizeY * (indexX + indexZ * sizeX);
                this.blendColumn(values, index, this.axisX.alpha[indexX], this.axisZ.alpha[indexZ], call.amplitude(), sizeY);
            }
        }
    }

    private void fillAxes(KernelCall call) {
        Leaf leaf = call.leaf();
        DensityVolume volume = call.volume();
        this.axisX.fillPlain(volume.sizeX(), volume.minBlockX(), volume.stepBlockX(), call.xzScale(), leaf.offsetX);
        this.axisZ.fillPlain(volume.sizeZ(), volume.minBlockZ(), volume.stepBlockZ(), call.xzScale(), leaf.offsetZ);
        if (leaf.kind == Kind.SMEARED) {
            this.axisY.fillSmeared(volume.sizeY(), volume.minBlockY(), volume.stepBlockY(), call.yScale(), leaf.offsetY, leaf.fudgeYScale);
        } else {
            this.axisY.fillPlain(volume.sizeY(), volume.minBlockY(), volume.stepBlockY(), call.yScale(), leaf.offsetY);
        }
    }

    private void ensureStage(int sizeY) {
        if (this.c000.length >= sizeY) {
            return;
        }
        this.c000 = new float[sizeY];
        this.c100 = new float[sizeY];
        this.c010 = new float[sizeY];
        this.c110 = new float[sizeY];
        this.c001 = new float[sizeY];
        this.c101 = new float[sizeY];
        this.c011 = new float[sizeY];
        this.c111 = new float[sizeY];
    }

    /** Fills the eight corner values for every y of one column; hashes are redone only when floorY changes. */
    private void stageColumn(char[] p, int x0, int x1, int indexX, int indexZ, int sizeY) {
        int floorZ = this.axisZ.floor[indexZ];
        float relativeX = this.axisX.relative[indexX];
        float relativeZ = this.axisZ.relative[indexZ];
        float d000xz = 0.0F, d100xz = 0.0F, d010xz = 0.0F, d110xz = 0.0F;
        float d001xz = 0.0F, d101xz = 0.0F, d011xz = 0.0F, d111xz = 0.0F;
        float g000y = 0.0F, g100y = 0.0F, g010y = 0.0F, g110y = 0.0F;
        float g001y = 0.0F, g101y = 0.0F, g011y = 0.0F, g111y = 0.0F;
        int lastFloorY = Integer.MIN_VALUE;

        for (int y = 0; y < sizeY; ++y) {
            int floorY = this.axisY.floor[y];
            if (lastFloorY != floorY) {
                lastFloorY = floorY;
                int xy00 = p[(x0 + floorY) & PERM_MASK];
                int xy01 = xy00 >>> PERM_SHIFT;
                int z000 = p[(xy00 + floorZ) & PERM_MASK];
                int z010 = p[(xy01 + floorZ) & PERM_MASK];
                int g000 = z000 & 15;
                int g001 = (z000 >>> PERM_SHIFT) & 15;
                d000xz = dotXz(g000, relativeX, relativeZ);
                g000y = Axis.GRADIENT_Y[g000];
                d001xz = dotXz(g001, relativeX, relativeZ - 1.0F);
                g001y = Axis.GRADIENT_Y[g001];

                int g010 = z010 & 15;
                int g011 = (z010 >>> PERM_SHIFT) & 15;
                d010xz = dotXz(g010, relativeX, relativeZ);
                g010y = Axis.GRADIENT_Y[g010];
                d011xz = dotXz(g011, relativeX, relativeZ - 1.0F);
                g011y = Axis.GRADIENT_Y[g011];

                int xy10 = p[(x1 + floorY) & PERM_MASK];
                int xy11 = xy10 >>> PERM_SHIFT;
                int z100 = p[(xy10 + floorZ) & PERM_MASK];
                int z110 = p[(xy11 + floorZ) & PERM_MASK];
                int g100 = z100 & 15;
                int g101 = (z100 >>> PERM_SHIFT) & 15;
                d100xz = dotXz(g100, relativeX - 1.0F, relativeZ);
                g100y = Axis.GRADIENT_Y[g100];
                d101xz = dotXz(g101, relativeX - 1.0F, relativeZ - 1.0F);
                g101y = Axis.GRADIENT_Y[g101];

                int g110 = z110 & 15;
                int g111 = (z110 >>> PERM_SHIFT) & 15;
                d110xz = dotXz(g110, relativeX - 1.0F, relativeZ);
                g110y = Axis.GRADIENT_Y[g110];
                d111xz = dotXz(g111, relativeX - 1.0F, relativeZ - 1.0F);
                g111y = Axis.GRADIENT_Y[g111];
            }

            float relativeY = this.axisY.relative[y];
            float relativeYLow = relativeY - 1.0F;
            this.c000[y] = d000xz + g000y * relativeY;
            this.c100[y] = d100xz + g100y * relativeY;
            this.c010[y] = d010xz + g010y * relativeYLow;
            this.c110[y] = d110xz + g110y * relativeYLow;
            this.c001[y] = d001xz + g001y * relativeY;
            this.c101[y] = d101xz + g101y * relativeY;
            this.c011[y] = d011xz + g011y * relativeYLow;
            this.c111[y] = d111xz + g111y * relativeYLow;
        }
    }

    /** values[index + y] += amplitude * lerp3(alphaX, alphaY[y], alphaZ, corners[y]) for all y of the column. */
    private void blendColumn(float[] values, int index, float alphaX, float alphaZ, float amplitude, int sizeY) {
        FloatVector vAlphaX = FloatVector.broadcast(SPECIES, alphaX);
        FloatVector vAlphaZ = FloatVector.broadcast(SPECIES, alphaZ);
        FloatVector vAmplitude = FloatVector.broadcast(SPECIES, amplitude);
        int vectorEnd = SPECIES.loopBound(sizeY);
        int y = 0;
        for (; y < vectorEnd; y += SPECIES.length()) {
            FloatVector vAlphaY = FloatVector.fromArray(SPECIES, this.axisY.alpha, y);
            FloatVector low = lerp(vAlphaY,
                lerp(vAlphaX, load(this.c000, y), load(this.c100, y)),
                lerp(vAlphaX, load(this.c010, y), load(this.c110, y)));
            FloatVector high = lerp(vAlphaY,
                lerp(vAlphaX, load(this.c001, y), load(this.c101, y)),
                lerp(vAlphaX, load(this.c011, y), load(this.c111, y)));
            FloatVector blended = lerp(vAlphaZ, low, high);
            FloatVector sum = FloatVector.fromArray(SPECIES, values, index + y).add(vAmplitude.mul(blended));
            sum.intoArray(values, index + y);
        }
        for (; y < sizeY; ++y) {
            float low = lerp(this.axisY.alpha[y],
                lerp(alphaX, this.c000[y], this.c100[y]),
                lerp(alphaX, this.c010[y], this.c110[y]));
            float high = lerp(this.axisY.alpha[y],
                lerp(alphaX, this.c001[y], this.c101[y]),
                lerp(alphaX, this.c011[y], this.c111[y]));
            values[index + y] += amplitude * lerp(alphaZ, low, high);
        }
    }

    private static FloatVector load(float[] source, int offset) {
        return FloatVector.fromArray(SPECIES, source, offset);
    }

    /** Mth.lerp: p0 + alpha * (p1 - p0), multiply and add kept separate. */
    private static FloatVector lerp(FloatVector alpha, FloatVector p0, FloatVector p1) {
        return p0.add(alpha.mul(p1.sub(p0)));
    }

    private static float lerp(float alpha, float p0, float p1) {
        return p0 + alpha * (p1 - p0);
    }

    private static float dotXz(int hash, float x, float z) {
        return Axis.GRADIENT_X[hash] * x + Axis.GRADIENT_Z[hash] * z;
    }
}
