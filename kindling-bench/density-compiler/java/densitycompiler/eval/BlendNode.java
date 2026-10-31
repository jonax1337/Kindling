package densitycompiler.eval;

import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * BlendDensityFunction.Sampler. Without a blender (or an empty one) Paper passes its input through unchanged, so
 * the input is evaluated with the same demand. With an active blender Paper's sampler runs on the whole region.
 */
final class BlendNode extends Node {
    private final Node input;
    private final DensitySampler paper;

    BlendNode(String label, Node input, DensitySampler paper) {
        super(label);
        this.input = input;
        this.paper = paper;
    }

    @Override
    void evaluate(Frame frame, DensityVolume region, DensityBuffer out, Demand demand) {
        Blender blender = frame.context().getField(Blender.CONTEXT_KEY);
        if (blender != null && !blender.isEmpty()) {
            this.paper.sampleVolume(frame.context(), out, region);
        } else {
            this.input.evaluate(frame, region, out, demand);
        }
    }
}
