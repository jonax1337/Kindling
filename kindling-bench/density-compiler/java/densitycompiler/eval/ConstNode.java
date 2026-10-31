package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** ConstantFunction.Sampler: outputBuffer.fill(value). */
final class ConstNode extends Node {
    private final float value;

    ConstNode(String label, float value) {
        super(label);
        this.value = value;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        java.util.Arrays.fill(out.values, 0, region.size(), this.value);
    }
}
