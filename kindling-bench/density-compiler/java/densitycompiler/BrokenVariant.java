package densitycompiler;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Self-test only: a variant that forgets to write part of its output. The verifier must catch every kind, which it
 * can only do if no buffer it hands out still holds an earlier call's values.
 */
final class BrokenVariant implements Variant {
    enum Kind {
        /** Writes nothing. */
        NO_OP,
        /** Computes Paper's result but copies only the lower half of each column. */
        HALF_COLUMN,
        /** Copies a scratch buffer it never wrote into the output. */
        STALE_SCRATCH,
        /** Correct the first time it sees a volume; writes nothing when the same volume comes twice in a row. */
        SKIPS_REPEATED_VOLUME
    }

    private final Kind kind;

    BrokenVariant(Kind kind) {
        this.kind = kind;
    }

    @Override
    public String name() {
        return this.kind.name();
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
        Kind kind = this.kind;
        return new DensitySampler() {
            private DensityVolume previous;

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
                switch (kind) {
                    case NO_OP -> {
                    }
                    case HALF_COLUMN -> copyLowerHalf(paper, context, outputBuffer, volume);
                    case STALE_SCRATCH -> copyUnwrittenScratch(context, outputBuffer, volume);
                    case SKIPS_REPEATED_VOLUME -> {
                        if (!volume.equals(this.previous)) {
                            paper.sampleVolume(context, outputBuffer, volume);
                        }
                        this.previous = volume;
                    }
                }
            }

            @Override
            public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
                return paper.sampleValue(context, blockX, blockY, blockZ);
            }
        };
    }

    private static void copyLowerHalf(DensitySampler paper, SamplerContext context, DensityBuffer output, DensityVolume volume) {
        try (ScopedDensityBuffer scratch = context.acquireBuffer(volume)) {
            paper.sampleVolume(context, scratch, volume);
            int half = volume.sizeY() / 2;
            for (int column = 0; column < volume.sizeX() * volume.sizeZ(); ++column) {
                System.arraycopy(scratch.values, column * volume.sizeY(), output.values, column * volume.sizeY(), half);
            }
        }
    }

    private static void copyUnwrittenScratch(SamplerContext context, DensityBuffer output, DensityVolume volume) {
        try (ScopedDensityBuffer scratch = context.acquireBuffer(volume)) {
            output.copyFrom(scratch);
        }
    }
}
