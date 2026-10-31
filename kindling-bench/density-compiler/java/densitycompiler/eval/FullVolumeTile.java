package densitycompiler.eval;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;

/**
 * Any other block-level sampler (the beardifier's ContextBoundSampler, or a kind the block stage does not model):
 * Paper's sampler (from the step-1 rebuild, so interpolated inputs still go through the corner evaluator) is called
 * once per call on exactly the volume Paper passes, and columns are copied out of that buffer. A context-bound
 * sampler that resolves to a constant or to Beardifier.EMPTY (whose sampleVolume is fill(0.0F)) becomes a constant
 * column instead.
 */
final class FullVolumeTile extends Tile {
    private final DensitySampler sampler;
    private long preparedCall = -1;
    private boolean constant;
    private float constantValue;
    private float[] values;

    FullVolumeTile(DensitySampler sampler) {
        this.sampler = sampler;
    }

    @Override
    void fill(TileFrame frame, float[] out, int x, int z) {
        if (this.preparedCall != frame.call()) {
            this.prepare(frame);
        }
        DensityVolume volume = frame.volume();
        int sizeY = volume.sizeY();
        if (this.constant) {
            Arrays.fill(out, 0, sizeY, this.constantValue);
        } else {
            System.arraycopy(this.values, volume.indexUnchecked(x, 0, z), out, 0, sizeY);
        }
    }

    private void prepare(TileFrame frame) {
        DensitySampler resolved = this.resolve(frame);
        this.constant = true;
        if (resolved instanceof ConstantFunction.Sampler(float value)) {
            this.constantValue = value;
        } else if (resolved == Beardifier.EMPTY) {
            this.constantValue = 0.0F;
        } else {
            this.constant = false;
            ScopedDensityBuffer buffer = frame.hold(frame.volume());
            resolved.sampleVolume(frame.context(), buffer, frame.volume());
            this.values = buffer.values;
        }
        this.preparedCall = frame.call();
    }

    /** What ContextBoundSampler.sampleVolume would delegate to in this context; any other sampler is itself. */
    private DensitySampler resolve(TileFrame frame) {
        if (this.sampler instanceof ContextBoundSampler bound) {
            return frame.context().getFieldOrDefault(bound.key(), bound.fallbackSampler());
        }
        return this.sampler;
    }
}
