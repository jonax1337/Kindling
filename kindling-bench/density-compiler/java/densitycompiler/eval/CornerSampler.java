package densitycompiler.eval;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Stands in for the input of an InterpolatedFunction.Sampler: the corner volume Paper's interpolation asks for is
 * evaluated by the node graph with every point demanded. Point queries go to Paper's own input sampler.
 */
final class CornerSampler implements DensitySampler {
    private final Node root;
    private final Frame frame;
    private final DensitySampler paper;

    CornerSampler(Node root, Frame frame, DensitySampler paper) {
        this.root = root;
        this.frame = frame;
        this.paper = paper;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        this.frame.bind(context);
        Demand all = this.frame.push(volume.size());
        try {
            all.setAll(volume.size());
            this.root.evaluate(this.frame, volume, outputBuffer, all);
        } finally {
            this.frame.pop(1);
        }
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return this.paper.sampleValue(context, blockX, blockY, blockZ);
    }
}
