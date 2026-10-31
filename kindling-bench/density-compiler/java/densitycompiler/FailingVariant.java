package densitycompiler;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Self-test only: Paper's output, except that the bench thread with the given name throws. A bench must report that, not hang. */
final class FailingVariant implements Variant {
    private final String failingThread;

    FailingVariant(String failingThread) {
        this.failingThread = failingThread;
    }

    @Override
    public String name() {
        return "FAILING";
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
                if (Thread.currentThread().getName().equals(FailingVariant.this.failingThread)) {
                    throw new IllegalStateException("self-test failure on " + FailingVariant.this.failingThread);
                }
                paper.sampleVolume(context, outputBuffer, volume);
            }

            @Override
            public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
                return paper.sampleValue(context, blockX, blockY, blockZ);
            }
        };
    }
}
