package net.kindlingmc.worldgen.density;

import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * BlendDensityFunction.Sampler. Without a blender (or an empty one) Paper passes its input through unchanged, so the
 * input is evaluated with the same demand. With an active blender Paper's own sampler runs on the whole region
 * (CompiledFinalDensity already hands such a call to Paper as a whole; this is the second guard).
 */
final class BlendNode extends Node {
    private final Node input;
    private final DensitySampler paper;

    BlendNode(Node input, DensitySampler paper) {
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

    @Override
    Node[] children() {
        return new Node[] {this.input};
    }
}
