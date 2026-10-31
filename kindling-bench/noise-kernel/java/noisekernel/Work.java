package noisekernel;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;

/** Per-thread output buffer and scratch state shared by every variant. */
final class Work {
    final DensityBuffer buffer;
    final float[] values;
    final MemorySegment valuesSegment;
    final VectorKernel vector = new VectorKernel();

    Work(int capacity) {
        this.buffer = DensityBuffer.createUnpooled(capacity);
        this.values = this.buffer.values;
        this.valuesSegment = MemorySegment.ofArray(this.values);
    }

    void clear() {
        Arrays.fill(this.values, 0.0F);
    }

    double checksum() {
        double sum = 0.0;
        for (float value : this.values) {
            sum += value;
        }
        return sum;
    }
}
