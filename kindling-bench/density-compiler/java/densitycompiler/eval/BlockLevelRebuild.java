package densitycompiler.eval;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/**
 * Paper's block-level sampler tree, rebuilt top-down (memoized by identity) with the input of every
 * InterpolatedFunction.Sampler replaced by a CornerSampler. Exact by construction: above the corners it is Paper's code.
 */
final class BlockLevelRebuild {
    static final Class<?> INTERPOLATED = SamplerRecords.type("net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction$Sampler");

    private final TreeBuilder corners;
    private final Frame frame;
    private final Map<DensitySampler, DensitySampler> rebuilt = new IdentityHashMap<>();

    BlockLevelRebuild(TreeBuilder corners, Frame frame) {
        this.corners = corners;
        this.frame = frame;
    }

    DensitySampler rebuild(DensitySampler sampler) {
        DensitySampler done = this.rebuilt.get(sampler);
        if (done == null) {
            done = this.rebuildOnce(sampler);
            this.rebuilt.put(sampler, done);
        }
        return done;
    }

    /** The corner sampler that stands in for the input of one of Paper's interpolated samplers. */
    CornerSampler cornerOf(DensitySampler interpolated) {
        return (CornerSampler)SamplerRecords.component(this.rebuild(interpolated), "input");
    }

    private DensitySampler rebuildOnce(DensitySampler sampler) {
        if (sampler.getClass() == INTERPOLATED) {
            DensitySampler input = (DensitySampler)SamplerRecords.component(sampler, "input");
            CornerSampler corner = new CornerSampler(this.corners.build(input), this.frame, input);
            return SamplerRecords.rebuild(sampler, child -> child == input ? corner : child);
        }
        return SamplerRecords.isRecord(sampler) ? SamplerRecords.rebuild(sampler, this::rebuild) : sampler;
    }
}
