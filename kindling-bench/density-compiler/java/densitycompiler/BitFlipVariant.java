package densitycompiler;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Self-test only: Paper's output with the lowest mantissa bit of one element flipped. The verifier must catch it. */
final class BitFlipVariant implements Variant {
    /** In the overworld volume (16 x 384 x 16) this is x = 0, y = 57 above the bottom, z = 2. */
    static final int FLIPPED_INDEX = 12_345;

    @Override
    public String name() {
        return "BIT_FLIP";
    }

    @Override
    public boolean cachesEnabled() {
        return true;
    }

    @Override
    public boolean benchmarkedByDefault() {
        return false;
    }

    @Override
    public DensitySampler sampler(World world) {
        DensitySampler paper = world.paperSampler();
        return new DensitySampler() {
            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
                paper.sampleVolume(context, outputBuffer, volume);
                float original = outputBuffer.get(FLIPPED_INDEX);
                outputBuffer.set(FLIPPED_INDEX, Float.intBitsToFloat(Float.floatToRawIntBits(original) ^ 1));
            }

            @Override
            public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
                return paper.sampleValue(context, blockX, blockY, blockZ);
            }
        };
    }
}
